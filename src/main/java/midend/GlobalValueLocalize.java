package midend;

import frontend.sym.InitSym;
import mir.*;
import mir.type.SymType;
import tools.MyList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class GlobalValueLocalize {
    private HashSet<GlobalValue.DefGlobalVal> defGlobalVals = new HashSet<>();
    private HashSet<Value> globalValToRemove = new HashSet<>();

    public GlobalValueLocalize(MyList<GlobalValue> globalValues) {
        for (GlobalValue globalValue : globalValues) {
            if (globalValue instanceof GlobalValue.DefGlobalVal) {
                defGlobalVals.add((GlobalValue.DefGlobalVal) globalValue);
            }
        }
    }

    public void runLocalization(MyList<GlobalValue> globalValues) {
        for (GlobalValue.DefGlobalVal value : defGlobalVals) {
            if (value.initValue.getType().isIntType() ||
                    value.initValue.getType().isFloatType()) {
                valueLocalize(value);
            }
        }

        for (Value value : globalValToRemove) {
            globalValues.remove((GlobalValue) value);
            defGlobalVals.remove((GlobalValue.DefGlobalVal) value);
            value.remove();
        }
    }

    private void valueLocalize(Value value) {
        HashSet<Instr> userInstrs = new HashSet<>();
        HashSet<Func> userFuncs = new HashSet<>();

        boolean ifHasStored = false;
        for (Instr instr : value.getUsers()) {
            if (instr instanceof Instr.StoreInstr) {
                ifHasStored = true;
            }
            userInstrs.add(instr);
            userFuncs.add(instr.getBelongBlock().getBelongFunc());
        }

        if (!ifHasStored) {
            InitSym.InitValue initValue = (InitSym.InitValue) ((GlobalValue.DefGlobalVal) value).initValue;
            Constant constant = null;
            if (initValue.getType().isIntType()) {
                constant = new Constant.IntConst((Integer) ((Constant) initValue.getValue()).getConstValue());
            } else if (initValue.getType().isFloatType()) {
                constant = new Constant.FloatConst((Float) ((Constant) initValue.getValue()).getConstValue());
            }
            for (Instr instr : userInstrs) {
                instr.replaceAllUsesWith(constant);
                instr.remove();
            }
            globalValToRemove.add(value);
            return;
        }

        if (userFuncs.size() == 1) {
            List<Func> list = new ArrayList<>(userFuncs);
            Func func = list.get(0);
            if (func.getName().equals("main")) {
                BasicBlock entry = func.getBasicBlocks().getFirst();
                InitSym.InitValue initValue = (InitSym.InitValue) ((GlobalValue.DefGlobalVal) value).initValue;
                if (initValue.getType().isIntType()) {
                    Instr.AllocaInstr allocaInstr = new Instr.AllocaInstr(SymType.BasicType.Basic_INT, entry);
                    Instr.StoreInstr storeInstr = new Instr.StoreInstr(initValue.getValue(), allocaInstr, entry);
                    value.replaceAllUsesWith(allocaInstr);
                    value.remove();
                    allocaInstr.remove();
                    storeInstr.remove();
                    entry.insertAtHead(allocaInstr);
                    allocaInstr.insertAfter(storeInstr);
                } else if (initValue.getType().isFloatType()) {
                    Instr.AllocaInstr allocaInstr = new Instr.AllocaInstr(SymType.BasicType.Basic_FLOAT, entry);
                    Instr.StoreInstr storeInstr = new Instr.StoreInstr(initValue.getValue(), allocaInstr, entry);
                    value.replaceAllUsesWith(allocaInstr);
                    value.remove();
                    allocaInstr.remove();
                    storeInstr.remove();
                    entry.insertAtHead(allocaInstr);
                    allocaInstr.insertAfter(storeInstr);
                }
                globalValToRemove.add(value);
            }
        }

    }


}
