package midend;

import midend.analysis.DomInfo;
import mir.*;
import mir.type.SymType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public class Mem2Reg {
    private static final DomInfo domInfo = new DomInfo();
    private static final HashSet<BasicBlock> defBBs = new HashSet<>();
    private static final HashSet<BasicBlock> useBBs = new HashSet<>();
    private static final HashSet<Instr> defInstrs = new HashSet<>();
    private static final HashSet<Instr> useInstrs = new HashSet<>();
    private static final Stack<Value> reachedDef = new Stack<>();

    public static void run(MyModule myModel) {
        domInfo.calculate(myModel);
        for (Func func : myModel.funcs) {
            for (BasicBlock basicBlock : func.getBasicBlocks()) {
                Instr loopInstr = basicBlock.getInstrs().getFirst();
                do {
                    if (loopInstr instanceof Instr.AllocaInstr &&
                            !(((Instr.AllocaInstr) loopInstr).getContentType() instanceof SymType.ArrayType)) {
                        if (((Instr.AllocaInstr) loopInstr).getContentType().isIntType()) {
                            loopInstr.insertAfter(new Instr.StoreInstr(new Constant.IntConst(0), loopInstr, basicBlock));
                        } else {
                            loopInstr.insertAfter(new Instr.StoreInstr(new Constant.FloatConst(0), loopInstr, basicBlock));
                        }
                        process((Instr.AllocaInstr) loopInstr);
                        Instr temp = loopInstr;
                        loopInstr = (Instr) loopInstr.getNext();
                        basicBlock.getInstrs().remove(temp);
                        for (Value v : temp.getUsedValues()) {
                            v.getUsers().remove(temp);
                        }
                    } else {
                        loopInstr = (Instr) loopInstr.getNext();
                    }
                } while (loopInstr != null);
            }
        }
    }

    private static void process(Instr.AllocaInstr allocInstr) {
        defBBs.clear();
        useBBs.clear();
        defInstrs.clear();
        useInstrs.clear();
        for (Instr instr : allocInstr.getUsers()) {
            if (instr instanceof Instr.StoreInstr) {
                defBBs.add(instr.belongBlock);
                defInstrs.add(instr);
            } else if (instr instanceof Instr.LoadInstr) {
                useBBs.add(instr.belongBlock);
                useInstrs.add(instr);
            } else {
                System.out.println("请检查use关系");
            }
        }
        processDefAndUse(allocInstr);
        removeStoreAndLoadInstr();
    }

    private static void processDefAndUse(Instr.AllocaInstr allocInstr) {
        if (useInstrs.isEmpty()) {
            for (Instr instr : defInstrs) {
                instr.belongBlock.getInstrs().remove(instr);
                for (Value v : instr.getUsedValues()) {
                    v.getUsers().remove(instr);
                }
            }
            return;
        }
        if (defBBs.size() == 1) {
            removeSingleDefLoad();
        } else {
            HashSet<BasicBlock> iterateBlockHasDef = new HashSet<>(defBBs);
            HashSet<BasicBlock> iterateBlockTobeInsert = new HashSet<>();
            iterateDF(iterateBlockTobeInsert, iterateBlockHasDef);
            insertPhi(iterateBlockTobeInsert, allocInstr);
            reachedDef.clear();
            DFSRename(allocInstr.getBelongBlock().getBelongFunc().getBasicBlocks().getFirst());
        }
    }

    private static void removeSingleDefLoad() {
        Instr reachedDef = null;
        if (defInstrs.size() == 1) {
            reachedDef = defInstrs.iterator().next();
            for (Instr use : useInstrs) {
                Value newValue = ((Instr.StoreInstr) reachedDef).getValue();
                use.replaceAllUsesWith(newValue);
            }
        } else {
            ArrayList<BasicBlock> temp = new ArrayList<>(defBBs);
            BasicBlock defBB = temp.get(0);
            for (Instr instr : defBB.getInstrs()) {
                if (defInstrs.contains(instr)) {
                    reachedDef = instr;
                } else if (useInstrs.contains(instr)) {
                    if (reachedDef == null) {
                        System.out.println("load 了一个野指针，赶紧检查一下");
                    } else {
                        instr.replaceAllUsesWith(((Instr.StoreInstr) reachedDef).getValue());
                    }
                }
            }
            for (Instr instr : useInstrs) {
                if (instr.getBelongBlock().equals(defBB)) {
                    continue;
                }
                if (reachedDef == null) {
                    System.out.println("load 了一个野指针，赶紧检查一下");
                } else {
                    instr.replaceAllUsesWith(((Instr.StoreInstr) reachedDef).getValue());
                }
            }
        }
    }

    private static void iterateDF(HashSet<BasicBlock> iterateBlockTobeInsert,
                                  HashSet<BasicBlock> iterateBlockHasDef) {
        while (!iterateBlockHasDef.isEmpty()) {
            BasicBlock nowBlock = iterateBlockHasDef.iterator().next();
            iterateBlockHasDef.remove(nowBlock);
            HashMap<BasicBlock, HashSet<BasicBlock>> DF = domInfo.getDFs().get(nowBlock.getBelongFunc());
            for (BasicBlock loopBlock : DF.get(nowBlock)) {
                iterateBlockTobeInsert.add(loopBlock);
                if (!defBBs.contains(loopBlock)) {
                    iterateBlockHasDef.add(loopBlock);
                }
            }
        }
    }

    private static void insertPhi(HashSet<BasicBlock> iterateBlockTobeInsert, Instr.AllocaInstr allocInstr) {
        for (BasicBlock bb : iterateBlockTobeInsert) {
            Instr.PhiInstr phiInstr = new Instr.PhiInstr(allocInstr.getContentType(), bb);
            useInstrs.add(phiInstr);
            defInstrs.add(phiInstr);
            useBBs.add(bb);
            defBBs.add(bb);
        }
    }

    private static void DFSRename(BasicBlock nowBlock) {
        // 先修改本块内的load和store
        int count = 0;
        for (Instr instr : nowBlock.getInstrs()) {
            if (instr instanceof Instr.StoreInstr && defInstrs.contains(instr)) {
                reachedDef.push(((Instr.StoreInstr) instr).getValue());
                count++;
            }
            if (instr instanceof Instr.LoadInstr && useInstrs.contains(instr)) {
                Value newValue = reachedDef.peek();
                if (newValue == null) {
                    System.out.println("未store就load，出问题了");
                } else {
                    instr.replaceAllUsesWith(reachedDef.peek());
                }
            }
            if (instr instanceof Instr.PhiInstr && defInstrs.contains(instr)) {
                reachedDef.push(instr);
                count++;
            }
        }
        // 然后填充其后继的phi
        for (BasicBlock bb : nowBlock.getSuccessors()) {
            for (Instr instr : bb.getInstrs()) {
                if (instr instanceof Instr.PhiInstr && useInstrs.contains(instr)) {
                    if (!reachedDef.isEmpty()) {
                        instr.addUsedValue(reachedDef.peek(), nowBlock);
                        reachedDef.peek().addUser(instr);
                    } else {
                        instr.addUsedValue(new GlobalValue.UndefGlobalVal(), nowBlock);
                    }
                } else {
                    break;
                }
            }
        }
        // 最后在支配树上DFS
        Func belongFunc = nowBlock.getBelongFunc();
        for (BasicBlock nextBBInDT : domInfo.getDTAdjacencyLists().get(belongFunc).get(nowBlock)) {
            DFSRename(nextBBInDT);
        }
        // 把本块内的定义弹出
        while (count != 0) {
            reachedDef.pop();
            count--;
        }
    }

    private static void removeStoreAndLoadInstr() {
        for (Instr instr : defInstrs) {
            if (instr instanceof Instr.PhiInstr) continue;
            instr.remove();
            for (Value v : instr.getUsedValues()) {
                v.getUsers().remove(instr);
            }
        }
        for (Instr instr : useInstrs) {
            if (instr instanceof Instr.PhiInstr) continue;
            instr.remove();
            for (Value v : instr.getUsedValues()) {
                v.getUsers().remove(instr);
            }
        }
    }
}