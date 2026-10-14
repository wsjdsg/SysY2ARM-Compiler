package midend;

import midend.analysis.SideEffectAnalysis;
import mir.*;

import java.util.ArrayList;

/**
 * 仅仅是根据Users的数量来决定
 */
public class SimpleDCE {
    private MyModule module;

    public SimpleDCE(MyModule module) {
        this.module = module;
    }

    public void run() {
        SideEffectAnalysis sideEffectAnalysis = new SideEffectAnalysis(module);
        sideEffectAnalysis.runAnalysis();
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                simpleDCE2Func(func);
            }
        }
    }

    public void simpleDCE2Func(Func func) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock bb : func.getBasicBlocks()) {
                ArrayList<Instr> rms = new ArrayList<>();
                for (Instr instr : bb.getInstrs()) {
                    if (rms.contains(instr)) {
                        continue;
                    }
                    if (instr instanceof Instr.StoreInstr) {
                        continue;
                    }
                    if (instr instanceof Instr.CallInstr && ((Instr.CallInstr) instr).getFunc().hasSideEffect()) {
                        continue;
                    }
                    if (instr instanceof Instr.RetInstr || instr instanceof Instr.BranchInstr ||
                            instr instanceof Instr.JumpInstr) {
                        continue;
                    }
                    if (instr instanceof Instr.AllocaInstr) {
                        boolean all = true;
                        for (Instr user : instr.getUsers()) {
                            if (!(user instanceof Instr.StoreInstr)) {
                                all = false;
                                break;
                            }
                        }
                        if (all) {
                            changed = true;
                            if (!rms.contains(instr)) {
                                rms.add(instr);
                            }
                            rms.addAll(instr.getUsers());
                            continue;
                        }
                    }
                    if (instr.getUsers().size() == 0) {
                        changed = true;
                        if (!rms.contains(instr)) {
                            rms.add(instr);
                        }
                    }
                }
                for (Instr v : rms) {
                    //没有人会用到这些指令
                    v.remove();
                    for (Value vv : v.getUsedValues()) {
                        vv.getUsers().remove(v);
                    }
                    v.getUsedValues().clear();
                    for (Instr user : v.getUsers()) {
                        user.getUsedValues().remove(v);
                    }
                    v.getUsers().clear();
                }
            }
        }
    }
}
