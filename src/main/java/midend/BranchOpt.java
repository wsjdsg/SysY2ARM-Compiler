package midend;

import midend.analysis.CFGBuilder;
import mir.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * 分支优化后会改变分支的结构，所以支配信息可能会发生更改！
 */
public class BranchOpt {
    private static int i = 0;
    private MyModule module;

    public BranchOpt(MyModule myModule) {
        module = myModule;
    }

    public void run() throws IOException {
        //MyIO.outLlvm(module, "mainD.ll");
        CFGBuilder.analyseCFG(module);
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                runBranchOpt(func);
                unReachableEmit(func);
            }
        }
    }

    public void runBranchOpt(Func func) throws IOException {
        boolean changed = true;
        while (changed) {
            changed = false;
            changed = changed || removeSinglePhi(func) || blockMerge(func) || branchProcess(func) || deadBlockEmit(func) || diamondOpt(func);
        }
    }

    public boolean removeSinglePhi(Func f) throws IOException {
        boolean flag = false;
        for (BasicBlock bb : f.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                if (instr instanceof Instr.PhiInstr) {
                    if (instr.getUsedValues().size() == 1) {
                        flag = true;
                        instr.remove();
                        Value v = instr.getUsedValues().get(0);
                        v.getUsers().remove(instr);
                        instr.getUsedValues().clear();
                        instr.replaceAllUsesWith(v);
                    }
                } else {
                    break;
                }
            }
        }
//        if (flag) {
//            MyIO.outLlvm(module, (i++) + "phi.txt");
//        }
        return flag;
    }

    /**
     * 合并两个块
     * 1.instrs拼接，第一个块的尾部br删除
     * 2.前驱后继关系维护
     * 3.def-use关系，什么指令会用到block？跳转和Phi！
     * 4.此之前需要消除单个的phi
     *
     * @param func
     * @return
     */


    public boolean blockMerge(Func func) throws IOException {
        boolean flag = false;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock bb : func.getBasicBlocks()) {
                if (bb.getPredecessors().size() == 1) {
                    BasicBlock pred = bb.getPredecessors().get(0);
                    if (pred.getSuccessors().size() == 1) {
                        changed = true;
                        flag = true;
                        bb.remove();
                        pred.getSuccessors().clear();
                        for (BasicBlock succ : bb.getSuccessors()) {
                            pred.getSuccessors().add(succ);
                            succ.getPredecessors().set(succ.getPredecessors().indexOf(bb), pred);
                            for (Instr instr : succ.getInstrs()) {
                                if (!(instr instanceof Instr.PhiInstr)) {
                                    break;
                                }
                                Instr.PhiInstr phiInstr = (Instr.PhiInstr) instr;
                                if (!phiInstr.getOptionBlocks().contains(bb)) {
                                    throw new RuntimeException(phiInstr + " don't contains Bb " + bb);
                                }
                                phiInstr.getOptionBlocks().set(phiInstr.getOptionBlocks().indexOf(bb), pred);
                            }
                        }
                        pred.getInstrs().getLast().remove();
                        ArrayList<Instr> tobeInserted = new ArrayList<>();
                        for (Instr instr : bb.getInstrs()) {
                            tobeInserted.add(instr);
                            instr.setBelongBlock(pred);
                        }
                        for (Instr instr : tobeInserted) {
                            pred.getInstrs().insertAtTail(instr);
                        }
                    }
                }
            }
        }
//        if (flag) {
//            MyIO.outLlvm(module, (i++) + "block.txt");
//        }
        return flag;
    }

    public boolean branchProcess(Func func) throws IOException {
        boolean flag = false;
        for (BasicBlock bb : func.getBasicBlocks()) {
            Instr tail = bb.getInstrs().getLast();
            if (tail instanceof Instr.BranchInstr) {
                Instr.BranchInstr br = (Instr.BranchInstr) tail;
                if (br.getCond() instanceof Constant.BoolConst) {
                    flag = true;
                    if (((Constant.BoolConst) br.getCond()).getBoolVal() != 0) {
                        br.remove();
                        Instr.JumpInstr jumpInstr = new Instr.JumpInstr((BasicBlock) br.getUsedValues().get(1), br.getBelongBlock());
                        bb.getSuccessors().remove(br.getUsedValues().get(2));
                        BasicBlock succ = (BasicBlock) br.getUsedValues().get(2);
                        for (Instr instr : succ.getInstrs()) {
                            if (!(instr instanceof Instr.PhiInstr)) {
                                break;
                            } else {
                                Instr.PhiInstr phiInstr = (Instr.PhiInstr) instr;
                                phiInstr.getUsedValues().remove(phiInstr.getOptionBlocks().indexOf(bb));
                                phiInstr.removeOption(bb);
                            }
                        }
                        succ.getPredecessors().remove(bb);
                    } else {
                        br.remove();
                        Instr.JumpInstr jumpInstr = new Instr.JumpInstr((BasicBlock) br.getUsedValues().get(2), br.getBelongBlock());
                        bb.getSuccessors().remove(br.getUsedValues().get(1));
                        BasicBlock succ = (BasicBlock) br.getUsedValues().get(1);
                        for (Instr instr : succ.getInstrs()) {
                            if (!(instr instanceof Instr.PhiInstr)) {
                                break;
                            } else {
                                Instr.PhiInstr phiInstr = (Instr.PhiInstr) instr;
                                phiInstr.getUsedValues().remove(phiInstr.getOptionBlocks().indexOf(bb));
                                phiInstr.removeOption(bb);
                            }
                        }
                        succ.getPredecessors().remove(bb);
                    }
                }
            }
        }
//        if (flag) {
//            MyIO.outLlvm(module, (i++) + "branch.txt");
//        }
        return flag;
    }

    public boolean deadBlockEmit(Func func) throws IOException {
        boolean flag = false;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock bb : func.getBasicBlocks()) {
                if (bb.getPredecessors().size() == 0 && !func.getBasicBlocks().getFirst().equals(bb)) {
                    flag = true;
                    changed = true;
                    bb.remove();
                    for (BasicBlock bbb : bb.getSuccessors()) {
                        for (Instr instr : bbb.getInstrs()) {
                            if (!(instr instanceof Instr.PhiInstr)) {
                                break;
                            } else {
                                Instr.PhiInstr phiInstr = (Instr.PhiInstr) instr;
                                if (!phiInstr.getOptionBlocks().contains(bb)) {
                                    throw new RuntimeException(bb + " " + phiInstr);
                                }
                                phiInstr.getUsedValues().remove(phiInstr.getOptionBlocks().indexOf(bb));
                                phiInstr.removeOption(bb);
                            }
                        }
                        bbb.getPredecessors().remove(bb);
                    }
                }
            }
        }
//        if (flag) {
//            MyIO.outLlvm(module, (i++) + "dead.txt");
//        }
        return flag;
    }

    /**
     * 这个暂且有点毛病
     *
     * @param func
     * @return
     */

    public boolean diamondOpt(Func func) throws IOException {
        boolean changed = false;
        boolean flag = false;
        for (BasicBlock bb : func.getBasicBlocks()) {
            if (bb.getSuccessors().size() == 2 &&
                    bb.getSuccessors().get(0).getSuccessors().size() == 1 &&
                    bb.getSuccessors().get(1).getSuccessors().size() == 1 &&
                    bb.getSuccessors().get(0).getPredecessors().size() == 1 &&
                    bb.getSuccessors().get(1).getPredecessors().size() == 1) {
                if (bb.getSuccessors().get(0).getInstrs().getFirst() instanceof Instr.JumpInstr &&
                        bb.getSuccessors().get(1).getInstrs().getFirst() instanceof Instr.JumpInstr &&
                        bb.getSuccessors().get(0).getSuccessors().get(0).equals(bb.getSuccessors().get(1).getSuccessors().get(0))) {
                    BasicBlock succ = bb.getSuccessors().get(0).getSuccessors().get(0);
                    if (!(succ.getInstrs().getFirst() instanceof Instr.PhiInstr)) {
                        flag = true;
                        bb.getSuccessors().get(0).remove();
                        bb.getSuccessors().get(1).remove();
                        succ.getPredecessors().remove(bb.getSuccessors().get(0));
                        succ.getPredecessors().remove(bb.getSuccessors().get(1));
                        bb.getSuccessors().clear();
                        bb.getSuccessors().add(succ);
                        bb.getInstrs().getLast().remove();
                        Instr.JumpInstr jumpInstr = new Instr.JumpInstr(succ, bb);
                        changed = true;
                    }
                }
            }
        }
//        if (flag) {
//            MyIO.outLlvm(module, (i++) + "diamond.txt");
//        }
        return flag;
    }

    public void unReachableEmit(Func func) throws IOException {
        HashSet<BasicBlock> reachable = new HashSet<>();
        ArrayDeque<BasicBlock> queue = new ArrayDeque();
        queue.add(func.getBasicBlocks().getFirst());
        while (!queue.isEmpty()) {
            BasicBlock bb = queue.pollFirst();
            reachable.add(bb);
            for (int i = 0; i < bb.getSuccessors().size(); i++) {
                if (!reachable.contains(bb.getSuccessors().get(i)) && !queue.contains(bb.getSuccessors().get(i))) {
                    queue.add(bb.getSuccessors().get(i));
                }
            }
        }
        HashSet<BasicBlock> rms = new HashSet<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            if (!reachable.contains(bb)) {
                bb.remove();
                rms.add(bb);
            }
        }
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                if (!(instr instanceof Instr.PhiInstr)) {
                    break;
                }
                Instr.PhiInstr phiInstr = (Instr.PhiInstr) instr;
                for (int i = 0; i < phiInstr.getOptionBlocks().size(); i++) {
                    if (rms.contains(phiInstr.getOptionBlocks().get(i))) {
                        phiInstr.getOptionBlocks().remove(i);
                        phiInstr.getUsedValues().remove(i);
                        i--;
                    }
                }
            }
        }
        removeSinglePhi(func);
        //MyIO.outLlvm(module, (i++) + "unReach.txt");
    }
}
