package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class RemovePhi {
    private MyModule module;

    public RemovePhi(MyModule module) {
        this.module = module;
    }

    private void removePhiForEachBlock(BasicBlock basicBlock) {
        if (!(basicBlock.getInstrs().getFirst() instanceof Instr.PhiInstr)) {
            return;
        }
        HashMap<BasicBlock, Pcopy> PcopyMap = new HashMap<>();
        for (BasicBlock bb : basicBlock.getPredecessors()) {
            PcopyMap.put(bb, new Pcopy());
        }
        for (Instr instr : basicBlock.getInstrs()) {
            if (!(instr instanceof Instr.PhiInstr)) {
                break;
            }
            Instr.PhiInstr phiInstr = (Instr.PhiInstr) instr;
            for (int i = 0; i < phiInstr.getOptionBlocks().size(); i++) {
                PcopyMap.get(phiInstr.getOptionBlocks().get(i)).addPair(phiInstr, phiInstr.getUsedValues().get(i));
            }
            phiInstr.remove();
        }
        for (int i = 0; i < basicBlock.getPredecessors().size(); i++) {
            BasicBlock bb = basicBlock.getPredecessors().get(i);
            processPcopy(bb, PcopyMap.get(bb), basicBlock, i);
        }
    }

    private void processPcopy(BasicBlock bb, Pcopy pcopy, BasicBlock succ, int idx) {
        HashMap<Value, Value> map = pcopy.map; //<dst,src>
        ArrayList<Instr.MoveInstr> moves = new ArrayList<>();
        while (!map.isEmpty()) {
            HashSet<Value> rmKeys = new HashSet();
            for (Value v : map.keySet()) {
                if (!map.values().contains(v)) {
                    rmKeys.add(v);
                } //被赋值以后不会用于赋值
            }
            if (rmKeys.isEmpty()) {
                //存在环！那就直接把环断开就好了
                Value cirPint = detectCircle(map);
                Value oriSrc = map.get(cirPint);
                if (oriSrc.equals(cirPint)) {
                    map.remove(cirPint);
                } else {
                    VirtualValue tmpReg = new VirtualValue(cirPint.getType());
                    moves.add(new Instr.MoveInstr(cirPint.getType(), oriSrc, tmpReg, bb));
                    map.put(cirPint, tmpReg);
                }
            } else {
                for (Value v : rmKeys) {
                    moves.add(new Instr.MoveInstr(v.getType(), map.get(v), v, bb));
                    map.remove(v);
                }
            }
        }
        if (bb.getSuccessors().size() == 1) {
            for (Instr.MoveInstr moveInstr : moves) {
                bb.getInstrs().getLast().insertBefore(moveInstr);
            }
        } else {
            BasicBlock mid = new BasicBlock(bb.getBelongFunc());
            for (Instr.MoveInstr moveInstr : moves) {
                mid.getInstrs().insertAtTail(moveInstr);
            }
            Instr.JumpInstr jumpInstr = new Instr.JumpInstr(succ, mid);
            assert bb.getInstrs().getLast() instanceof Instr.BranchInstr;
            Instr.BranchInstr branchInstr = (Instr.BranchInstr) bb.getInstrs().getLast();
            if (branchInstr.getUsedValues().get(1).equals(succ)) {
                branchInstr.getUsedValues().set(1, mid);
            } else {
                branchInstr.getUsedValues().set(2, mid);
            }
            succ.getPredecessors().set(idx, mid);
            succ.getPredecessors().remove(succ.getPredecessors().size() - 1);
            bb.getSuccessors().remove(succ);
            bb.getSuccessors().add(mid);
            mid.getPredecessors().add(bb);
        }
    }

    public Value detectCircle(HashMap<Value, Value> G) {
        HashSet<Value> visited = new HashSet<>();
        Value ori;
        ori = null;
        for (Value v : G.keySet()) {
            ori = v;
            break;
        }
        if (ori == null) {
            throw new RuntimeException("ori is null");
        }
        while (!visited.contains(ori)) {
            visited.add(ori);
            ori = G.get(ori);
        }
        return ori;
    }

    public void run() {
        for (Func func : module.getFuncs()) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                removePhiForEachBlock(bb);
            }
        }
    }

    public class Pcopy {
        HashMap<Value, Value> map = new HashMap<>();

        public void addPair(Value lhs, Value rhs) {
            map.put(lhs, rhs);
        }
    }
}
