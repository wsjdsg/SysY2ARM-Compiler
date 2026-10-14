package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 在load这个地址的值之前，连续同一个地址的store，实际上只有最后一次有效
 */
public class UselessStoreEmit {
    private MyModule module;

    public UselessStoreEmit(MyModule module) {
        this.module = module;
    }

    public void run() {
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                emitUselessStoreInFunc(func);
            }
        }
    }

    /**
     * def-use关系记得维护！
     *
     * @param func
     */
    public void emitUselessStoreInFunc(Func func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            HashMap<Value, Instr> sts = new HashMap<>();
            ArrayList<Instr> rms = new ArrayList<>();
            for (Instr instr : bb.getInstrs()) {
                if (instr instanceof Instr.StoreInstr) {
                    Instr.StoreInstr storeInstr = (Instr.StoreInstr) instr;
                    if (sts.containsKey(storeInstr.getAddress())) {
                        rms.add(sts.get(storeInstr.getAddress()));
                    }
                    sts.put(storeInstr.getAddress(), storeInstr);
                }
                if (instr instanceof Instr.LoadInstr) {
                    Instr.LoadInstr loadInstr = (Instr.LoadInstr) instr;
                    if (sts.containsKey(loadInstr.getAddress())) {
                        sts.remove(loadInstr.getAddress());
                    }
                }
            }
            for (Instr instr : rms) {
                instr.remove();
                for (Value v : instr.getUsedValues()) {
                    v.getUsers().remove(instr);
                }
                instr.getUsedValues().clear();
            }
        }
    }
}
