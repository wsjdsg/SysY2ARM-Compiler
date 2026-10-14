package midend;

import midend.analysis.Loop;
import mir.*;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopFold {
    private ArrayList<Func> funcs = new ArrayList<>();
    private HashSet<Loop> allLoops = new HashSet<>();
    private HashSet<Loop> loopsToRemove = new HashSet<>();

    public LoopFold(MyModule module) {
        for (Func func : module.funcs) {
            funcs.add(func);
        }
    }

    public void runLoopFold() {
        for (Func func : funcs) {
            allLoops.clear();
            loopsToRemove.clear();
            loopFoldForFunc(func);
        }
    }

    public void loopFoldForFunc(Func func) {
        for (Loop loop : func.getAllLoops()) {
            allLoops.add(loop);
            genCalMesForLoop(loop);
        }
        for (Loop loop : func.getAllLoops()) {
            if (!loopsToRemove.contains(loop)) {
                LoopFoldForLoop(loop);
            }
        }

        for (Loop loop : loopsToRemove) {
            if (loop.getParentLoop() != null) {
                loop.getParentLoop().getSubLoops().remove(loop);
            }

            BasicBlock entering = loop.getEntryBlock().getPredecessors().get(0).equals(loop.getLatchBlocks().get(0)) ?
                    loop.getEntryBlock().getPredecessors().get(1) : loop.getEntryBlock().getPredecessors().get(0);
            BasicBlock exitNext = loop.getExits().get(0).getSuccessors().get(0);
            Instr brEntering = entering.getInstrs().getLast();
            brEntering.getUsedValues().set(brEntering.getUsedValues().indexOf(loop.getEntryBlock()), exitNext);
            loop.getEntryBlock().getUsers().remove(brEntering);
            exitNext.getPredecessors().set(exitNext.getPredecessors().indexOf(loop.getEntryBlock()), entering);

            for(BasicBlock block : loop.getAllBlocks()) {
                block.remove();
                for(Instr instr : block.getUsers()) {
                    instr.getUsedValues().remove(block);
                }
                for(Instr instr : block.getInstrs()) {
                    instr.remove();
                    for(Value value : instr.getUsedValues()) {
                        value.getUsers().remove(instr);
                    }
                    for(Instr user : instr.getUsers()) {
                        user.getUsedValues().remove(instr);
                    }
                }
            }
        }
    }

    public void genCalMesForLoop(Loop loop) {
        loop.valEntering = null;
        loop.aluConstIdx = 0;
        loop.aluForCal = null;
        loop.phiForCal = null;
        loop.calMesHasSetted = false;

        if (!loop.isStandardLoop() || !loop.idcHasSetted) {
            return;
        }

        if (!loop.getSubLoops().isEmpty()) {
            return;
        }
        for (BasicBlock block : loop.getAllBlocks()) {
            if (!loop.getLatchBlocks().contains(block) && !loop.getExits().contains(block)) {
                return;
            }
        }

        BasicBlock latch = loop.getLatchBlocks().get(0);
        HashSet<Instr> idcInstrs = new HashSet<>();
        idcInstrs.add(latch.getInstrs().getLast());
        idcInstrs.add(loop.getEntryBlock().getInstrs().getLast());
        idcInstrs.add((Instr) loop.valFromAlu);
        idcInstrs.add((Instr) loop.valFromPhi);
        idcInstrs.add((Instr) loop.cmpInstr);

        for (Instr instr : idcInstrs) {
            //System.out.println(instr + " " + ifHasUserOutside(loop, instr));
            if (ifHasUserOutside(loop, instr)) {
                return;
            }
        }


        Instr.AluInstr aluInstr = null;
        Instr.PhiInstr phiInstr = null;
        int aluInstrCnt = 0;
        int phiInstrCnt = 0;
        for (BasicBlock block : loop.getAllBlocks()) {
            for (Instr instr : block.getInstrs()) {
                if (!idcInstrs.contains(instr)) {
                    if (instr instanceof Instr.AluInstr) {
                        aluInstr = (Instr.AluInstr) instr;
                        aluInstrCnt++;
                    } else if (instr instanceof Instr.PhiInstr) {
                        phiInstr = (Instr.PhiInstr) instr;
                        phiInstrCnt++;
                    } else {
                        return;
                    }
                }
            }
        }

        if (phiInstrCnt != 1 || aluInstrCnt != 1) {
            return;
        }

        if (!ifHasUserOutside(loop, phiInstr) || ifHasUserOutside(loop, aluInstr)) {
            return;
        }

        int latchIdx = loop.getEntryBlock().getPredecessors().indexOf(latch);
        int enteringIdx = 1 - latchIdx;

        if (!phiInstr.getUsedValues().get(latchIdx).equals(aluInstr)) {
            return;
        }
        if (!aluInstr.getUsedValues().contains(phiInstr)) {
            return;
        }
        if (!((aluInstr.getUsedValues().get(1 - aluInstr.getUsedValues().indexOf(phiInstr))) instanceof Constant.IntConst)) {
            return;
        }

        Value valEntering = phiInstr.getUsedValues().get(enteringIdx);

        loop.valEntering = valEntering;
        loop.aluForCal = aluInstr;
        loop.phiForCal = phiInstr;
        loop.aluConstIdx = 1 - aluInstr.getUsedValues().indexOf(phiInstr);
        loop.calMesHasSetted = true;
    }

    public void LoopFoldForLoop(Loop loop) {
        if (!loop.calMesHasSetted) {
            return;
        }

        if (loop.getExits().size() != 1) {
            return;
        }

        BasicBlock exit = loop.getExits().get(0);

        if (exit.getSuccessors().size() != 1) {
            return;
        }
        BasicBlock exitSuc = exit.getSuccessors().get(0).getParentLoop() != null &&
                exit.getSuccessors().get(0).getParentLoop().equals(loop) ?
                exit.getSuccessors().get(1) : exit.getSuccessors().get(0);

        Loop nextLoop = exitSuc.getParentLoop();

        if (nextLoop == null || !nextLoop.calMesHasSetted) {
            return;
        }

        if (!nextLoop.isStandardLoop()) {
            return;
        }

        if (!nextLoop.getEntryBlock().equals(exitSuc)) {
            return;
        }

        if (!nextLoop.valFromDef.equals(loop.valFromDef) ||
                !nextLoop.valToCalWith.equals(loop.valToCalWith) ||
                !nextLoop.valToCmpWith.equals(loop.valToCmpWith)) {
            return;
        }

        if (loop.cmpInstr instanceof Instr.IcmpInstr) {
            if (!((Instr.IcmpInstr) loop.cmpInstr).getIcmpOp().equals(((Instr.IcmpInstr) nextLoop.cmpInstr).getIcmpOp())) {
                return;
            }
        } else if (loop.cmpInstr instanceof Instr.FcmpInstr) {
            if (!((Instr.FcmpInstr) loop.cmpInstr).getFcmpOp().equals(((Instr.FcmpInstr) nextLoop.cmpInstr).getFcmpOp())) {
                return;
            }
        } else {
            return;
        }

        if (!loop.valEntering.equals(nextLoop.valEntering)) {
            return;
        }

        int aluVal = ((Constant.IntConst) ((Instr.AluInstr) loop.aluForCal).getUsedValues().get(loop.aluConstIdx)).getIntVal();
        int nextAluVal = ((Constant.IntConst) ((Instr.AluInstr) nextLoop.aluForCal).getUsedValues().get(nextLoop.aluConstIdx)).getIntVal();

        if (aluVal != nextAluVal || loop.aluConstIdx != nextLoop.aluConstIdx ||
                !((Instr.AluInstr) loop.aluForCal).getAluOp().equals(((Instr.AluInstr) nextLoop.aluForCal).getAluOp())) {
            return;
        }
        nextLoop.phiForCal.replaceAllUsesWith(loop.phiForCal);
        loopsToRemove.add(nextLoop);
    }

    public boolean ifHasUserOutside(Loop loop, Instr instr) {
        for (Instr user : instr.getUsers()) {
            if (user.belongBlock.getParentLoop() == null ||
                    !user.belongBlock.getParentLoop().equals(loop)) {
                return true;
            }
        }
        return false;
    }

}
