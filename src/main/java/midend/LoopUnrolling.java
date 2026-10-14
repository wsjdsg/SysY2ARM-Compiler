package midend;

import midend.analysis.CFGBuilder;
import midend.analysis.CloneRelation;
import midend.analysis.Loop;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static java.lang.Math.log;
import static java.lang.Math.pow;

public class LoopUnrolling {
    private ArrayList<Func> funcs = new ArrayList<>();
    private HashMap<Func, HashSet<Loop>> funcLoops = new HashMap<>();
    private HashMap<Func, ArrayList<Loop>> preOrderFuncLoops = new HashMap<>();

    private MyModule module;

    private Loop emptyLoop = new Loop();

    public LoopUnrolling(MyModule myModule) {
        this.module = myModule;
        for (Func func : myModule.funcs) {
            funcs.add(func);
        }
        for (Func func : funcs) {
            HashSet<Loop> loops = new HashSet<>(func.getAllLoops());
            funcLoops.put(func, loops);
            preOrderFuncLoops.put(func, new ArrayList<>());
        }
        for (Func func : funcs) {
            if (func.getFuncBody() != null) {
                for (Loop loop : func.getTopLoops()) {
                    loop.setParentLoop(emptyLoop);
                    emptyLoop.addSubLoop(loop);
                }

                for (Loop loop : func.getTopLoops()) {
                    preOrderWalkLoops(func, loop);
                }
            }
        }
    }

    public void preOrderWalkLoops(Func func, Loop curLoop) {
        if (curLoop == null) {
            return;
        }
        for (Loop loop : curLoop.getSubLoops()) {
            preOrderWalkLoops(func, loop);
        }
        preOrderFuncLoops.get(func).add(curLoop);
    }

    public void runLoopUnroll(MyModule module) {
        for (Func func : funcs) {
            runLoopUnrollForFunc(func);
        }
        CFGBuilder.analyseCFG(module);
    }

    public void runLoopUnrollForFunc(Func func) {
        for (Loop loop : preOrderFuncLoops.get(func)) {
            if (loop.isStandardLoop() && loop.idcHasSetted && loop.getSubLoops().isEmpty()) {
                runLoopUnrollForLoop(loop, func);
            }
        }
        for (Loop loop : func.getTopLoops()) {
            loop.setParentLoop(null);
        }
    }

    public void runLoopUnrollForLoop(Loop loop, Func func) {
        if (!(loop.valFromDef instanceof Constant) ||
                !(loop.valToCmpWith instanceof Constant) ||
                !(loop.valToCalWith instanceof Constant)) {
            return;
        }

        if (!loop.valFromAlu.getType().isIntType()) {
            return;
        }

        int start = (int) ((Constant) loop.valFromDef).getConstValue();
        int step = (int) ((Constant) loop.valToCalWith).getConstValue();
        int end = (int) ((Constant) loop.valToCmpWith).getConstValue();
        if (step == 0 || start == end) {
            return;
        }

        int loopTimes = 0;
        switch (((Instr.AluInstr) loop.valFromAlu).getAluOp()) {
            case ADD:
                switch (((Instr.IcmpInstr) loop.cmpInstr).getIcmpOp()) {
                    case EQ:
                        loopTimes = 0;
                        break;
                    case NE:
                        if ((end - start) % step != 0) {
                            return;
                        }
                        loopTimes = (end - start) / step;
                        break;
                    case SLE:
                    case SGE:
                        loopTimes = (end - start) / step + 1;
                        break;
                    case SLT:
                    case SGT:
                        if ((end - start) % step == 0) {
                            loopTimes = (end - start) / step;
                        } else {
                            loopTimes = (end - start) / step + 1;
                        }
                        break;
                }
                break;
            case SUB:
                switch (((Instr.IcmpInstr) loop.cmpInstr).getIcmpOp()) {
                    case EQ:
                        loopTimes = 0;
                        break;
                    case NE:
                        if ((start - end) % step != 0) {
                            return;
                        }
                        loopTimes = (start - end) / step;
                        break;
                    case SLE:
                    case SGE:
                        loopTimes = (start - end) / step + 1;
                        break;
                    case SLT:
                    case SGT:
                        if ((start - end) % step == 0) {
                            loopTimes = (start - end) / step;
                        } else {
                            loopTimes = (start - end) / step + 1;
                        }
                        break;
                }
                break;
            case MUL:
                double minc = log(end / step) / log(step);
                boolean mod0 = (start * pow(step, minc) == end);
                switch (((Instr.IcmpInstr) loop.cmpInstr).getIcmpOp()) {
                    case EQ:
                        loopTimes = 0;
                        break;
                    case NE:
                        if (!mod0) {
                            return;
                        }
                        loopTimes = (int) minc;
                        break;
                    case SLE:
                    case SLT:
                        loopTimes = (int) minc + 1;
                        break;
                    case SGE:
                    case SGT:
                        loopTimes = mod0 ? (int) minc : (int) minc + 1;
                        break;
                }
                break;
            default:
                return;
        }

        loop.loopTimes = loopTimes;
        if (loopTimes <= 1) {
            return;
        }

        HashSet<BasicBlock> allBlocks = new HashSet<>(loop.getAllBlocks());
        HashSet<BasicBlock> allChildExits = new HashSet<>();
        getAllChildExits(loop, allChildExits);

        for (BasicBlock childExit : allChildExits) {
            if (!allBlocks.contains(childExit)) {
                return;
            }
        }

        int instrSum = 0;
        for (BasicBlock block : allBlocks) {
            for (Instr instr : block.getInstrs()) {
                instrSum++;
            }
        }

        if (instrSum * loopTimes > 2500) {
            return;
        }

        BasicBlock entry = loop.getEntryBlock();
        BasicBlock latch = loop.getLatchBlocks().get(0);
        BasicBlock hasExited = loop.getExits().get(0).getSuccessors().get(0).getParentLoop().equals(loop) ?
                loop.getExits().get(0).getSuccessors().get(1) : loop.getExits().get(0).getSuccessors().get(0);
        BasicBlock entering = entry.getPredecessors().get(0).equals(latch) ?
                entry.getPredecessors().get(1) : entry.getPredecessors().get(0);


        BasicBlock firstNextBody = entry.getSuccessors().get(0).getParentLoop().equals(loop) ?
                entry.getSuccessors().get(0) : entry.getSuccessors().get(1);

        if (firstNextBody.getPredecessors().size() != 1) {
            return;
        }

        HashMap<Value, Value> defInEntering = new HashMap<>();
        HashMap<Value, Value> defAfterLatch = new HashMap<>();

        HashMap<Value, Value> newPhi = new HashMap<>();     //newPhiAfterLatch---oldphi
        HashMap<Value, Value> oldPhitoNewPhi = new HashMap<>();

        for (Instr instr : entry.getInstrs()) {
            if (!(instr instanceof Instr.PhiInstr)) {
                break;
            }
            int enteringIdx = ((Instr.PhiInstr) instr).getOptionBlocks().indexOf(entering);
            defInEntering.put(instr, ((Instr.PhiInstr) instr).getOptions().get(enteringIdx));

//            instr.getUsedValues().get(enteringIdx).getUsers().remove(instr);
//            instr.getUsedValues().remove(enteringIdx);
//
//            ArrayList<Value> values = new ArrayList<>();
//            values.add(instr.getUsedValues().get(latchIdx));
//            Instr.PhiInstr phiAfterLatch = new Instr.PhiInstr(instr.getType(), values, latchSuc);
//            defAfterLatch.put(instr, phiAfterLatch);
//            oldPhitoNewPhi.put(instr, phiAfterLatch);
//            newPhi.put(phiAfterLatch, instr);
        }

        BasicBlock latchSuc = new BasicBlock(entry.getBelongFunc());
        loop.addBlock(latchSuc);
        latchSuc.setParentLoop(loop);
        //int latchIdx = entry.getPredecessors().indexOf(latch);


        for (Instr instr : entry.getInstrs()) {
            if (!(instr instanceof Instr.PhiInstr)) {
                break;
            }
            int latchIdx = ((Instr.PhiInstr) instr).getOptionBlocks().indexOf(latch);
            ArrayList<Value> values = new ArrayList<>();
            values.add(((Instr.PhiInstr) instr).getOptions().get(latchIdx));
            Instr.PhiInstr phiAfterLatch = new Instr.PhiInstr(instr.getType(), values, latchSuc);
            phiAfterLatch.insertOption(latch);
            instr.getUsedValues().get(latchIdx).getUsers().remove(instr);
            ((Instr.PhiInstr) instr).removeOption(latch);
            instr.getUsedValues().remove(latchIdx);

            defAfterLatch.put(instr, phiAfterLatch);
            oldPhitoNewPhi.put(instr, phiAfterLatch);
            newPhi.put(phiAfterLatch, instr);
        }


        ArrayList<BasicBlock> newEntryPres = new ArrayList<>();
        newEntryPres.add(entering);
        entry.setPredecessors(newEntryPres);
        ArrayList<BasicBlock> newEntrySuc = new ArrayList<>();
        newEntrySuc.add(firstNextBody);
        entry.setSuccessors(newEntrySuc);

        loop.getAllBlocks().remove(entry);
        entry.setParentLoop(loop.getParentLoop());
//        if (loop.getParentLoop() != null) {
//            loop.getParentLoop().getAllBlocks().add(entry);
//        }
        entry.getInstrs().getLast().remove();
        Instr.JumpInstr jumpInstr = new Instr.JumpInstr(firstNextBody, entry);

        Instr.JumpInstr jumpInstr1 = new Instr.JumpInstr(hasExited, latchSuc);
        latch.getSuccessors().set(latch.getSuccessors().indexOf(entry), latchSuc);

        latch.getInstrs().getLast().getUsedValues().set(
                latch.getInstrs().getLast().getUsedValues().indexOf(entry), latchSuc);

        entry.getUsers().remove(latch.getInstrs().getLast());
        latchSuc.getUsers().add(latch.getInstrs().getLast());
        latchSuc.getPredecessors().add(latch);
        latchSuc.getSuccessors().add(hasExited);
        hasExited.getPredecessors().set(hasExited.getPredecessors().indexOf(entry), latchSuc);


        Loop parentLoop = loop.getParentLoop();
        parentLoop.getSubLoops().remove(loop);

        for (BasicBlock block : loop.getAllBlocks()) {
            if (block.getParentLoop().equals(loop)) {
                block.setParentLoop(parentLoop);
                parentLoop.addBlock(block);
            }
        }

        for (Loop loop1 : loop.getSubLoops()) {
            if (loop1.getParentLoop().equals(loop)) {
                loop1.setParentLoop(parentLoop);
            }
        }

        HashSet<BasicBlock> allBlockInLoopBody = new HashSet<>(loop.getAllBlocks());
        allBlockInLoopBody.remove(entry);


        BasicBlock srcFirstBody = firstNextBody;
        BasicBlock srcLatchSuc = latchSuc;
        BasicBlock cloneFirstBody = null;
        BasicBlock cloneLatchSuc = null;
        for (int i = 0; i < loopTimes - 1; i++) {
            CloneRelation.clear();
            for (BasicBlock block : allBlockInLoopBody) {
                block.cloneBlockForLoopUnroll(entry.getBelongFunc(), parentLoop);
            }
            for (BasicBlock block : allBlockInLoopBody) {
                BasicBlock cloneBlock = (BasicBlock) CloneRelation.getCloneVal(block);
                cloneBlock.fixPreSuc(block);
                cloneBlock.fixInstrs();
            }
//            //--------------------------------------------------
//            for(BasicBlock block : func.getBasicBlocks()) {
//                if(!allBlockInLoopBody.contains(block)){
//                    for(Instr instr : block.getInstrs()) {
//                        instr.fixInstr();
//                    }
//                }
//            }
//            //---------------------------------------------------

            cloneFirstBody = (BasicBlock) CloneRelation.getCloneVal(srcFirstBody);
            cloneLatchSuc = (BasicBlock) CloneRelation.getCloneVal(srcLatchSuc);

            srcLatchSuc.getSuccessors().set(srcLatchSuc.getSuccessors().indexOf(hasExited), cloneFirstBody);
            hasExited.getPredecessors().set(hasExited.getPredecessors().indexOf(srcLatchSuc), cloneLatchSuc);
            ArrayList<BasicBlock> pres = new ArrayList<>();
            pres.add(srcLatchSuc);
            cloneFirstBody.setPredecessors(pres);

            srcLatchSuc.getInstrs().getLast().getUsedValues().set(
                    srcLatchSuc.getInstrs().getLast().getUsedValues().indexOf(hasExited), cloneFirstBody);

            hasExited.getUsers().remove(srcLatchSuc.getInstrs().getLast());
            cloneFirstBody.getUsers().add(srcLatchSuc.getInstrs().getLast());

            //System.out.println("****" + hasExited + "***" + cloneFirstBody);

            //            ////////////////////////////////////////////////////////////
//            for (BasicBlock block : allBlockInLoopBody) {
//                BasicBlock cloneBB = (BasicBlock) CloneRelation.getCloneVal(block);
//                System.out.println(cloneBB.getName());
//                for (Instr instr : cloneBB.getInstrs()) {
//                    System.out.println(instr);
//                }
//            }
            //            /////////////////////////////////////////////////////////


            HashSet<Value> keys = new HashSet<>();
            for (Value value : defAfterLatch.keySet()) {
                keys.add(value);
            }

            for (Value value : keys) {
                defAfterLatch.put(CloneRelation.getCloneVal(value), defAfterLatch.get(value));
                if (CloneRelation.srcValtoCloneVal.containsKey(value)) {
                    defAfterLatch.remove(value);
                }
            }

            for (BasicBlock block : allBlockInLoopBody) {
                BasicBlock cloneBlock = (BasicBlock) CloneRelation.getCloneVal(block);
                for (Instr instr : cloneBlock.getInstrs()) {
                    for (Value usedValue : instr.getUsedValues()) {
                        if (defAfterLatch.containsKey(usedValue)) {
                            Value phiAfterLatch = defAfterLatch.get(usedValue);
                            instr.getUsedValues().set(instr.getUsedValues().indexOf(usedValue), phiAfterLatch);
                            usedValue.getUsers().remove(instr);
                            phiAfterLatch.getUsers().add(instr);
                        }
                    }
                }
            }

            keys.clear();
            for (Value value : defAfterLatch.keySet()) {
                keys.add(value);
            }
            for (Value key : keys) {
                Value phi = defAfterLatch.get(key);
                Value clonePhi = CloneRelation.getCloneVal(phi);
                defAfterLatch.put(phi, clonePhi);
                defAfterLatch.remove(key);
            }

            keys.clear();
            for (Value value : oldPhitoNewPhi.keySet()) {
                keys.add(value);
            }
            for (Value key : keys) {
                Value clonePhi = CloneRelation.getCloneVal(oldPhitoNewPhi.get(key));
                oldPhitoNewPhi.put(key, clonePhi);
            }

            srcFirstBody = cloneFirstBody;
            srcLatchSuc = cloneLatchSuc;
            HashSet<BasicBlock> cloneAllBlockInLoopBody = new HashSet<>();
            for (BasicBlock block : allBlockInLoopBody) {
                cloneAllBlockInLoopBody.add((BasicBlock) CloneRelation.getCloneVal(block));
            }
            allBlockInLoopBody = cloneAllBlockInLoopBody;
        }

        for (Instr instr : hasExited.getInstrs()) {
            if (!(instr instanceof Instr.PhiInstr)) {
                break;
            }

            for (Value value : instr.getUsedValues()) {
                if (value instanceof Instr && ((Instr) value).getBelongBlock().equals(entry)) {
                    int idx = instr.getUsedValues().indexOf(value);

                    //if(oldPhitoNewPhi.get(value) != null) {
                        instr.modifyUse(oldPhitoNewPhi.get(value), idx);
                    //}
                    //((Instr.PhiInstr) instr).getOptionBlocks().set(idx, cloneLatchSuc);
                }
            }
//                    instr.getUsedValues().set(idx, oldPhitoNewPhi.get(value));
//                    value.getUsers().remove(instr);
//                    oldPhitoNewPhi.get(value).getUsers().add(instr);

        }

    }

    public void getAllChildExits(Loop loop, HashSet<BasicBlock> allChildExits) {
        for (Loop loop1 : loop.getSubLoops()) {
            allChildExits.addAll(loop1.getExits());
            getAllChildExits(loop1, allChildExits);
        }
    }


}
