package midend;

import midend.analysis.DomInfo;
import midend.analysis.LoopAnalysis;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GCM {
    private MyModule module;
    private HashSet<Instr> visited = new HashSet<>();
    private HashMap<Instr, BasicBlock> earliestBB = new HashMap<>();

    public GCM(MyModule module) {
        this.module = module;
    }

    public void run() {
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                GCM(func);
            }
        }
    }

    public void GCM(Func func) {
        DomInfo domInfo = new DomInfo();
        domInfo.calculate(module);
        LoopAnalysis.runLoopAnalysis(module);
        visited.clear();
        scheduleEarlyInFunc(func);
        visited.clear();
        scheduleLateInFunc(func);
    }

    public void scheduleEarlyInFunc(Func func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                if (!isMovable(instr)) {
                    earliestBB.put(instr, instr.getBelongBlock());
                    visited.add(instr);
                }
            }
        }
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                if (!isMovable(instr)) {
                    for (Value v : instr.getUsedValues()) {
                        if (v instanceof Instr) {
                            scheduleEarly((Instr) v, func);
                        }
                    }
                }
            }
        }
    }

    public void scheduleLateInFunc(Func func) {
        ArrayList<Instr> instrs = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                instrs.add(instr);
            }
        }
        for (Instr i : instrs) {
            if (!isMovable(i)) {
                visited.add(i);
            }
            scheduleLate(i);
        }
    }

    public boolean isMovable(Instr instr) {
        return (instr instanceof Instr.AluInstr) ||
                (instr instanceof Instr.IcmpInstr) ||
                (instr instanceof Instr.FcmpInstr) ||
                (instr instanceof Instr.ZextInstr) ||
                (instr instanceof Instr.FptosiInstr) ||
                (instr instanceof Instr.SitofpInstr) ||
                (instr instanceof Instr.GepInstr) ||
                (instr instanceof Instr.CallInstr && ((Instr.CallInstr) instr).isPure());
    }

    public void scheduleEarly(Instr instr, Func func) {
        if (visited.contains(instr)) {
            return;
        }
        visited.add(instr);
        earliestBB.put(instr, func.getBbs().getFirst());
        for (Value v : instr.getUsedValues()) {
            if (v instanceof Instr) {
                scheduleEarly((Instr) v, func);
                if (earliestBB.get(instr).getDomLevel() < earliestBB.get((Instr) v).getDomLevel()) {
                    earliestBB.put(instr, earliestBB.get((Instr) v));
                }
            }
        }
    }


    public void scheduleLate(Instr instr) {
        if (visited.contains(instr)) {
            return;
        }
        visited.add(instr);
        BasicBlock lca = null;
        for (Instr user : instr.getUsers()) {
            if (isMovable(user)) {
                scheduleLate(user);
            }
            BasicBlock use = user.getBelongBlock();
            if (user instanceof Instr.PhiInstr) {
                for (int i = 0; i < user.getUsedValues().size(); i++) {
                    if (user.getUsedValues().get(i).equals(instr)) {
                        use = ((Instr.PhiInstr) user).getOptionBlocks().get(i);
                        lca = LCA(lca, use);
                    }
                }
            } else {
                lca = LCA(lca, use);
            }
        }
        if (lca == null) {
            return;
        }
        BasicBlock best = lca;
        while (!lca.equals(earliestBB.get(instr))) {
            lca = lca.getDominator();
            if (lca.getLoopDepth() < best.getLoopDepth()) {
                best = lca;
            }
        }
        //将instr插入best这个基本块
        Instr tmp = null;
        for (Instr i : best.getInstrs()) {
            if (i instanceof Instr.PhiInstr) {
                continue;
            }
            if (instr.getUsers().contains(i)) {
                tmp = i;
                break;
            }
        }
        if (tmp == null) {
            tmp = best.getInstrs().getLast();
        }
        instr.remove();
        instr.setBelongBlock(best);
        tmp.insertBefore(instr);
    }

    public BasicBlock LCA(BasicBlock bb1, BasicBlock bb2) {
        if (bb1 == null) {
            return bb2;
        }
        while (bb1.getDomLevel() < bb2.getDomLevel()) {
            bb2 = bb2.getDominator();
        }
        while (bb2.getDomLevel() < bb1.getDomLevel()) {
            bb1 = bb1.getDominator();
        }
        while (!(bb1.equals(bb2))) {
            bb1 = bb1.getDominator();
            bb2 = bb2.getDominator();
        }
        return bb1;
    }
}
