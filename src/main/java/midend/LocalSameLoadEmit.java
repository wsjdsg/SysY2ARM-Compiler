package midend;

import mir.*;

import java.util.HashMap;

/**
 * store addr
 * load addr
 * load same_addr
 * ...
 * store addr
 */
public class LocalSameLoadEmit {
    private MyModule module;

    public LocalSameLoadEmit(MyModule module) {
        this.module = module;
    }

    public void run() {
        for (Func func : module.getFuncs()) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                runPerBasicBlock(bb);
            }
        }
    }

    /**
     * 记得维护def-use关系
     *
     * @param bb
     */
    public void runPerBasicBlock(BasicBlock bb) {
        HashMap<Instr.GepInstr, Instr.LoadInstr> canReplace = new HashMap<>();
        for (Instr instr : bb.getInstrs()) {
            if (instr instanceof Instr.GepInstr) {
                canReplace.put((Instr.GepInstr) instr, null);
            }
            if (instr instanceof Instr.StoreInstr) {
                Instr.StoreInstr storeInstr = (Instr.StoreInstr) instr;
                if (storeInstr.getAddress() instanceof Instr.GepInstr &&
                        ((Instr.GepInstr) storeInstr.getAddress()).getBelongBlock().equals(bb)) {
                    canReplace.put((Instr.GepInstr) storeInstr.getAddress(), null);
                }
            }
            if (instr instanceof Instr.LoadInstr) {
                Instr.LoadInstr loadInstr = (Instr.LoadInstr) instr;
                if (loadInstr.getAddress() instanceof Instr.GepInstr &&
                        ((Instr.GepInstr) loadInstr.getAddress()).getBelongBlock().equals(bb)) {
                    if (canReplace.get(loadInstr.getAddress()) != null) {
                        if (canDel(loadInstr)) {
                            instr.remove();
                            instr.replaceAllUsesWith(canReplace.get(loadInstr.getAddress()));
                            for (Value v : instr.getUsedValues()) {
                                v.getUsers().remove(instr);
                            }
                        }
                    } else {
                        canReplace.put((Instr.GepInstr) loadInstr.getAddress(), loadInstr);
                    }
                }
            }
        }
    }

    private boolean canDel(Instr.LoadInstr loadInstr) {
        for (Instr user : loadInstr.getUsers()) {
            if (!user.getBelongBlock().equals(loadInstr.getBelongBlock())) {
                return false;
            }
        }
        return true;
    }
}
