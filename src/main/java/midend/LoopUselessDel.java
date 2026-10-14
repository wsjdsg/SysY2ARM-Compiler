package midend;

import midend.analysis.Loop;
import midend.analysis.SideEffectAnalysis;
import mir.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class LoopUselessDel {
    private ArrayList<Func> funcs = new ArrayList<>();
    private ArrayList<GlobalValue> globalValues = new ArrayList<>();

    private Queue<Loop> loopQueue = new LinkedList<>();

    public LoopUselessDel(MyModule module) {
        SideEffectAnalysis sideEffectAnalysis = new SideEffectAnalysis(module);
        sideEffectAnalysis.runAnalysis();
        for (Func func : module.funcs) {
            funcs.add(func);
        }
        for (GlobalValue globalValue : module.globalValues) {
            globalValues.add(globalValue);
        }
    }

    public void runLoopUselessDel() {
        for (Func func : funcs) {
            if (func.getFuncBody() != null) {
                runLoopUselessDelForFunc(func);
            }
        }
    }

    public void runLoopUselessDelForFunc(Func func) {
        for (Loop loop : func.getTopLoops()) {
            fulfillLoopQueue(loop);
        }
        while (!loopQueue.isEmpty()) {
            Loop loop = loopQueue.remove();
            runLoopUselessDelForLoop(loop);
        }
    }

    public void fulfillLoopQueue(Loop loop) {
        for (Loop loop1 : loop.getSubLoops()) {
            fulfillLoopQueue(loop1);
        }
        loopQueue.add(loop);
    }

    public void runLoopUselessDelForLoop(Loop loop) {
        if (!loop.isStandardLoop()) {
            return;
        }

        HashSet<Instr> loopInstrs = new HashSet<>();
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instr instr : block.getInstrs()) {
                if (instr.isStore() || instr.isRet() ||
                        instr.isCall() && ((Instr.CallInstr) instr).getFunc().hasSideEffect()) {
                    return;
                }
                //System.out.println(instr.getName() + " " + ifHasBeenUsedOutside(loop, instr)
                if (ifHasBeenUsedOutside(loop, instr)) {
                    return;
                }

                loopInstrs.add(instr);
            }
        }

        BasicBlock hasExited = loop.getEntryBlock().getSuccessors().get(0).getParentLoop() != null &&
                loop.getEntryBlock().getSuccessors().get(0).getParentLoop().equals(loop) ?
                loop.getEntryBlock().getSuccessors().get(1) : loop.getEntryBlock().getSuccessors().get(0);


        for (Instr instr : hasExited.getInstrs()) {
            if (instr instanceof Instr.PhiInstr) {
                return;
            }
//            for (Value op : ((Instr.PhiInstr) instr).getOptions()) {
//                if (op instanceof Instr && loopInstrs.contains(op)) {
//                    return;
//                }
//            }
            //todo
        }

        BasicBlock entryPre = loop.getEntryBlock().getPredecessors().get(0).getParentLoop() != null &&
                loop.getEntryBlock().getPredecessors().get(0).getParentLoop().equals(loop) ?
                loop.getEntryBlock().getPredecessors().get(1) : loop.getEntryBlock().getPredecessors().get(0);

        if (entryPre.getSuccessors().size() != 1) {
            return;
        }

        if (!(entryPre.getInstrs().getLast() instanceof Instr.JumpInstr)) {
            return;
        }

        Instr.JumpInstr entryPreJump = (Instr.JumpInstr) entryPre.getInstrs().getLast();

        entryPreJump.setTarget(hasExited);


        for (BasicBlock block : loop.getAllBlocks()) {
            block.remove();
            if (loop.getParentLoop() != null) {
                loop.getParentLoop().getAllBlocks().remove(block);
            }
            for (Instr instr : block.getUsers()) {
                instr.getUsedValues().remove(block);
            }
        }

        for (Instr instr : loopInstrs) {
            for (Value value : instr.getUsedValues()) {
                value.getUsers().remove(instr);
            }
            for (Instr instr1 : instr.getUsers()) {
                instr1.getUsedValues().remove(instr);
            }
            instr.remove();
        }

        if (loop.getParentLoop() != null) {
            loop.getParentLoop().getSubLoops().remove(loop);
        }
        loop.getEntryBlock().getBelongFunc().getTopLoops().remove(loop);
        removeAllLoops(loop, loop.getEntryBlock().getBelongFunc());
    }

    public void removeAllLoops(Loop loop, Func func) {
        func.getAllLoops().remove(loop);
        for (Loop loop1 : loop.getSubLoops()) {
            removeAllLoops(loop1, func);
        }
    }

    public boolean ifHasBeenUsedOutside(Loop loop, Instr instr) {
        if (instr instanceof Instr.StoreInstr) {
            for (Instr user : ((Instr.StoreInstr) instr).getAddress().getUsers()) {
                if (user.belongBlock.getParentLoop() == null ||
                        !user.belongBlock.getParentLoop().equals(loop)) {
                    return true;
                }
            }
        }

        for (Instr user : instr.getUsers()) {
            if (user.belongBlock.getParentLoop() == null ||
                    !user.belongBlock.getParentLoop().equals(loop)) {
                return true;
            }
        }
        return false;
    }

}
