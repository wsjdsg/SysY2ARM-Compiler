package midend.analysis;

import mir.*;

/**
 * 建立块之间的前驱后继关系
 */


public class CFGBuilder {
    public static void analyseCFG(MyModule module) {
        for (Func func : module.getFuncs()) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                bb.getPredecessors().clear();
                bb.getSuccessors().clear();
            }
        }
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                for (BasicBlock bb : func.getBasicBlocks()) {
                    Instr instr = bb.getInstrs().getLast();
                    if (instr instanceof Instr.BranchInstr) {
                        BasicBlock bb1 = (BasicBlock) instr.getUsedValues().get(1);
                        BasicBlock bb2 = (BasicBlock) instr.getUsedValues().get(2);
                        bb.getSuccessors().add(bb1);
                        bb.getSuccessors().add(bb2);
                        bb1.getPredecessors().add(bb);
                        bb2.getPredecessors().add(bb);
                    } else if (instr instanceof Instr.JumpInstr) {
                        BasicBlock bb1 = (BasicBlock) instr.getUsedValues().get(0);
                        bb.getSuccessors().add(bb1);
                        bb1.getPredecessors().add(bb);
                    }
                }
                //清除不能到达的块，也即没有前驱
                boolean changed = true;
                while (changed) {
                    changed = false;
                    for (BasicBlock bb : func.getBasicBlocks()) {
                        if (bb.getPredecessors().isEmpty() && !bb.equals(func.getBasicBlocks().getFirst())) {
                            changed = true;
                            for (BasicBlock bbb : bb.getSuccessors()) {
                                bbb.getPredecessors().remove(bb);
                            }
                            for (Instr instr : bb.getInstrs()) {
                                for (Value v : instr.getUsedValues()) {
                                    v.getUsers().remove(instr);
                                }
                            }
                            bb.getSuccessors().clear();
                            func.getBasicBlocks().remove(bb);
                        }
                    }
                }
            }
        }
    }
}
