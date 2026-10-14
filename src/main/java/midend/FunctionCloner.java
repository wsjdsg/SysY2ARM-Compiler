package midend;

import mir.*;
import tools.MyList;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 1.此类实现了一个静态方法cloneFunc，旨在深克隆一个Func，并使得def-use链依据
 * clone关系，将copy对象替换掉原来的对象，类似图的同构
 * 2.如果用到了全局变量，就不clone
 * TODO 考虑到phi指令数据结构修改后，可能涉及到的clone修改
 */
public class FunctionCloner {
    public static Func cloneFunc(Func func) throws CloneNotSupportedException {
        Func f = (Func) func.clone();
        ArrayList<Func.Param> paramArrayList = new ArrayList<>();
        //clone para arraylist
        HashMap<Func.Param, Func.Param> paraMap = new HashMap<>();
        for (int i = 0; i < func.getParams().size(); i++) {
            Func.Param param = func.getParams().get(i).getCopyInCertainFunc(f);
            paramArrayList.add(param);
            paraMap.put(func.getParams().get(i), param);
        }
        f.setParams(paramArrayList);
        //clone Basic blocks but instrs inside is shallow copy
        MyList<BasicBlock> bbs = new MyList<>();
        HashMap<BasicBlock, BasicBlock> bbCopyMap = new HashMap<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            BasicBlock copyBB = bb.getCopyInCertainFunc(f);
            bbCopyMap.put(bb, copyBB);
            bbs.insertAtTail(copyBB);
        }
        f.setBasicBlocks(bbs);
        //维护basic block的前驱后继关系
        for (BasicBlock bb : bbs) {
            for (int i = 0; i < bb.getPredecessors().size(); i++) {
                bb.getPredecessors().set(i, bbCopyMap.get(bb.getPredecessors().get(i)));
            }
            for (int i = 0; i < bb.getSuccessors().size(); i++) {
                bb.getSuccessors().set(i, bbCopyMap.get(bb.getSuccessors().get(i)));
            }
        }

        //deep copy th instrs and reserve a origin-copy hashmap
        //def-use 链此步并未修改，仍需修改未copy对象之间的def-use
        HashMap<Instr, Instr> copyMap = new HashMap<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            MyList<Instr> list = new MyList<>();
            for (Instr instr : bb.getInstrs()) {
                Instr newInst = instr.getCopyInCertainBB(bbCopyMap.get(bb));
                copyMap.put(instr, newInst);
                list.insertAtTail(newInst);
            }
            bbCopyMap.get(bb).setInstrs(list);
        }
        //指令替换为了copy，但是def-use关系没有变，需要维护
        for (Func.Param param : f.getParams()) {
            for (int i = 0; i < param.getUsers().size(); i++) {
                param.getUsers().set(i, copyMap.get(param.getUsers().get(i)));
            }
        }
        for (BasicBlock bb : f.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                ArrayList<Instr> users = instr.getUsers();
                ArrayList<Value> usedValues = instr.getUsedValues();
                for (int i = 0; i < users.size(); i++) {
                    if (!copyMap.containsKey(users.get(i))) {
                        throw new RuntimeException(users.get(i).toString() + " not mapped!");
                    }
                    users.set(i, copyMap.get(users.get(i)));
                }
                for (int i = 0; i < usedValues.size(); i++) {
                    if (!(usedValues.get(i) instanceof GlobalValue)) {
                        if (usedValues.get(i) instanceof Func.Param) {
                            usedValues.set(i, paraMap.get(usedValues.get(i)));
                        } else if (usedValues.get(i) instanceof Instr) {
                            if (!copyMap.containsKey(usedValues.get(i))) {
                                throw new RuntimeException(usedValues.get(i) + " not mapped ");
                            }
                            usedValues.set(i, copyMap.get(usedValues.get(i)));
                        } else if (usedValues.get(i) instanceof BasicBlock) {
                            if (!bbCopyMap.containsKey(usedValues.get(i))) {
                                throw new RuntimeException(usedValues.get(i) + " not mapped");
                            }
                            usedValues.set(i, bbCopyMap.get(usedValues.get(i)));
                        }
                    }
                }
            }
        }
        for (BasicBlock bb : f.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                for (Value v : instr.getUsedValues()) {
                    if (v instanceof GlobalValue) {
                        v.addUser(instr);
                    }
                }
                if (instr instanceof Instr.PhiInstr) {
                    var arr = ((Instr.PhiInstr) instr).getOptionBlocks();
                    for (int i = 0; i < arr.size(); i++) {
                        arr.set(i, bbCopyMap.get(arr.get(i)));
                    }
                }
            }
        }
        return f;
    }
}
