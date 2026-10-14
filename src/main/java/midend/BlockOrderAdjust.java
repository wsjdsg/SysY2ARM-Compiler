package midend;

import mir.BasicBlock;
import mir.Func;
import mir.Instr;
import mir.MyModule;
import tools.MyList;

import java.util.HashSet;

public class BlockOrderAdjust {
    private static HashSet<BasicBlock> ifHasVisited = new HashSet<>();

    public static void runBlockOrderAdjust(MyModule module) {
        for (Func func : module.funcs) {
            if (func.getFuncBody() != null) {
                runBlockOrderAdjustForFunc(func);
            }
        }
    }

    private static void runBlockOrderAdjustForFunc(Func func) {
        ifHasVisited.clear();
        BasicBlock entry = func.getBasicBlocks().getFirst();
        MyList<BasicBlock> orderedBB = new MyList<>();
        dfsToGenNewOrder(entry, orderedBB);
        func.setBasicBlocks(orderedBB);
    }

    private static void dfsToGenNewOrder(BasicBlock start, MyList<BasicBlock> orderedBB) {
        if (ifHasVisited.contains(start)) {
            return;
        }
        ifHasVisited.add(start);
        orderedBB.insertAtTail(start);
        Instr bjInstr = start.getInstrs().getLast();
        if (bjInstr instanceof Instr.JumpInstr) {
            for (BasicBlock block : start.getSuccessors()) {
                dfsToGenNewOrder(block, orderedBB);
            }
        } else if (bjInstr instanceof Instr.BranchInstr) {
            BasicBlock thenAct = ((Instr.BranchInstr) bjInstr).getThenAct();
            BasicBlock elseAct = ((Instr.BranchInstr) bjInstr).getElseAct();
            dfsToGenNewOrder(elseAct, orderedBB);
            dfsToGenNewOrder(thenAct, orderedBB);
        }
    }

}
