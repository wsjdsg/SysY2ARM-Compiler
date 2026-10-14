package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashSet;

public class UselessRetValEmit {
    private ArrayList<Func> funcs = new ArrayList<>();
    private ArrayList<GlobalValue> globalValues = new ArrayList<>();

    public UselessRetValEmit(MyModule module) {
        for (Func func : module.funcs) {
            funcs.add(func);
        }
        for (GlobalValue globalValue : module.globalValues) {
            globalValues.add(globalValue);
        }
    }

    public void runUselessRetValEmit() {
        for (Func func : funcs) {
            if (!func.getName().equals("main")) {
                runUselessRetValEmitForFunc(func);
            }
        }
    }

    public void runUselessRetValEmitForFunc(Func func) {
        if (func.getRetType().isVoidType()) {
            return;
        }

        HashSet<Instr> funcCallers = new HashSet<>();

        for (Func func1 : funcs) {
            for (BasicBlock block : func1.getBasicBlocks()) {
                for (Instr instr : block.getInstrs()) {
                    if (instr instanceof Instr.CallInstr && ((Instr.CallInstr) instr).getFunc().equals(func)) {
                        funcCallers.add(instr);
                    }
                }
            }
        }

        for (Instr caller : funcCallers) {
            if (caller.getUsers().size() != 0) {
                return;
            }
        }

        for (BasicBlock block : func.getBasicBlocks()) {
            for (Instr instr : block.getInstrs()) {
                if (instr instanceof Instr.RetInstr) {
                    Value retVal = ((Instr.RetInstr) instr).getRetValue();

                    if (retVal instanceof Instr.CallInstr) {
                        boolean flag = funNotChangeOutside(((Instr.CallInstr) retVal).getFunc());
                        for (Instr instr1 : retVal.getUsers()) {
                            if (!(instr1 instanceof Instr.RetInstr && instr1.belongBlock.getBelongFunc().equals(func))) {
                                flag = false;
                                break;
                            }
                        }
                        if (flag) {
                            for (Value param : ((Instr.CallInstr) retVal).getParams()) {
                                param.getUsers().remove(retVal);
                            }
                            retVal.remove();
                        }
                    }

                    if (func.getRetType().isIntType()) {
                        Constant.IntConst intConst = new Constant.IntConst(0);
                        ((Instr.RetInstr) instr).setRetValue(intConst);
                    } else if (func.getRetType().isFloatType()) {
                        Constant.FloatConst floatConst = new Constant.FloatConst(0.0f);
                        ((Instr.RetInstr) instr).setRetValue(floatConst);
                    }
                }
            }
        }

    }

    public boolean funNotChangeOutside(Func func) {
        for (Value param : func.getParams()) {
            if (param.getType().isArrayType() || param.getType().isPointerType()) {
                return false;
            }
        }

        for (GlobalValue globalValue : globalValues) {
            if (!globalValue.ifConst) {
                for (Instr instr : globalValue.getUsers()) {
                    if (instr.belongBlock.getBelongFunc().equals(func)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

}
