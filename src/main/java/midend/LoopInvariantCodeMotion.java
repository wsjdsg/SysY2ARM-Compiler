//循环无关代码外提，主要考虑循环内不受循环影响的变量定义指令（def）

package midend;

import midend.analysis.Loop;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LoopInvariantCodeMotion {

    private ArrayList<Func> funcs = new ArrayList<>();
    private ArrayList<GlobalValue> globalValues = new ArrayList<>();
    private HashMap<Instr.AllocaInstr, HashSet<Instr>> allocValDoms = new HashMap<>();  //对alloc开辟的空间中存的值有改变作用的指令
    private HashMap<Instr.AllocaInstr, HashSet<Instr>> allocValUsers = new HashMap<>(); //使用到alloc开辟的空间中存的值的指令

    public LoopInvariantCodeMotion(MyModule module) {
        for (Func func : module.funcs) {
            funcs.add(func);
        }
        for (GlobalValue globalValue : module.globalValues) {
            globalValues.add(globalValue);
        }
    }


    public void runLoopCodeMotion() {
        allocValDoms.clear();
        allocValUsers.clear();
        for (Func func : funcs) {
            codeMotionForFunc(func);
        }
    }

    private void codeMotionForFunc(Func func) {
        for (BasicBlock block : func.getBasicBlocks()) {
            for (Instr instr : block.getInstrs()) {
                if (instr instanceof Instr.AllocaInstr) {
                    for (Instr user : instr.getUsers()) {
                        genAllocRelatives((Instr.AllocaInstr) instr, user);
                    }
                }
            }
        }
        for (Instr.AllocaInstr allocaInstr : allocValDoms.keySet()) {
            codeMotionForInstr(allocaInstr);
        }
    }

    private void genAllocRelatives(Instr.AllocaInstr allocaInstr, Instr instr) {
        if (instr instanceof Instr.StoreInstr) {
            if (!allocValDoms.containsKey(allocaInstr)) {
                allocValDoms.put(allocaInstr, new HashSet<>());
            }
            allocValDoms.get(allocaInstr).add(instr);
        } else if (instr instanceof Instr.GepInstr || instr instanceof Instr.BitcastInstr) {
            for (Instr user : instr.getUsers()) {
                genAllocRelatives(allocaInstr, user);
            }
        } else if (instr instanceof Instr.LoadInstr) {
            if (!allocValUsers.containsKey(allocaInstr)) {
                allocValUsers.put(allocaInstr, new HashSet<>());
            }
            allocValUsers.get(allocaInstr).add(instr);
        } else if (instr instanceof Instr.CallInstr) {
            if (!allocValDoms.containsKey(allocaInstr)) {
                allocValDoms.put(allocaInstr, new HashSet<>());
            }
            allocValDoms.get(allocaInstr).add(instr);

            String funcName = ((Instr.CallInstr) instr).getFunc().getName();
            if (!funcName.equals("memset") && !funcName.equals("getarray") && !funcName.equals("getfarray")) {
                if (!allocValUsers.containsKey(allocaInstr)) {
                    allocValUsers.put(allocaInstr, new HashSet<>());
                }
                allocValUsers.get(allocaInstr).add(instr);
            }
        }
    }

    private void codeMotionForInstr(Instr.AllocaInstr allocaInstr) {
        HashSet<Instr> valDoms = allocValDoms.get(allocaInstr);

        //保证所有可以改变alloc开辟空间中的存储值的指令在一个basicBlock内
        BasicBlock block = null;
        for (Instr instr : valDoms) {
            if (block == null) {
                block = instr.getBelongBlock();
            } else {
                if (!block.equals(instr.getBelongBlock())) {
                    return;
                }
            }
        }

        //保证所有改变值的操作在使用值的操作之前
        boolean userFirst = false;
        assert block != null;
        for (Instr instr : block.getInstrs()) {
            if (allocValUsers.get(allocaInstr) != null && allocValUsers.get(allocaInstr).contains(instr)) {
                userFirst = true;
            }
            if (userFirst && valDoms.contains(instr)) {
                return;
            }
        }

        //保证定义的值不受循环影响（都是常量），这里似乎可以进一步优化
        for (Instr instr : valDoms) {
            if (instr instanceof Instr.CallInstr) {
                if (((Instr.CallInstr) instr).getFunc().getName().equals("memset")) {
                    if (((Instr.CallInstr) instr).getParams().get(1) instanceof Constant.IntConst &&
                            ((Instr.CallInstr) instr).getParams().get(2) instanceof Constant.IntConst) {
                        int param1 = ((Constant.IntConst) ((Instr.CallInstr) instr).getParams().get(1)).intVal;
                        if (param1 != 0) {
                            return;
                        }
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else if (instr instanceof Instr.StoreInstr) {
                if (!(((Instr.StoreInstr) instr).getValue() instanceof Constant.IntConst) &&
                        !(((Instr.StoreInstr) instr).getValue() instanceof Constant.FloatConst)) {
                    return;
                }

                Value pointer = ((Instr.StoreInstr) instr).getAddress();
                if (pointer instanceof Instr.GepInstr) {
                    for (Value dimLen : ((Instr.GepInstr) pointer).getDimLens()) {
                        if (!(dimLen instanceof Constant.IntConst)) {
                            return;
                        }
                    }
                }

            } else {
                return;
            }
        }

        Loop loop = block.getParentLoop();
        if (loop == null || loop.getLoopDepth() == 0) {
            return;
        }

        if(loop.getEntryBlock().getPredecessors().size() != 2) {
            return;
        }

        ArrayList<Instr> instrsToRemove = new ArrayList<>();
        for (Instr instr : block.getInstrs()) {
            if (valDoms.contains(instr)) {
                instrsToRemove.add(instr);
            }
        }

        for (Instr instr : instrsToRemove) {
            instr.remove();
            BasicBlock entering = null;
            for (BasicBlock block1 : loop.getEntryBlock().getPredecessors()) {
                if (!loop.getExits().contains(block1)) {
                    entering = block1;
                    break;
                }
            }

            if (entering == null) {
                return;
            }

            entering.getInstrs().getLast().insertBefore(instr);
            instr.belongBlock = entering;
        }
    }

}
