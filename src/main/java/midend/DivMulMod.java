package midend;

import mir.*;
import mir.type.SymType;

/***
 * 此优化在GVN,GCM之后
 * 将取模展开为除法，乘法和减法
 */
public class DivMulMod {
    private MyModule module;

    public DivMulMod(MyModule module) {
        this.module = module;
    }

    private int isPow2(int a) {
        int pow = 1;
        for (int i = 0; i < 32; i++) {
            if (a == pow) {
                return i;
            }
            pow <<= 1;
        }
        return -1;
    }

    public void run() {
        for (Func func : module.getFuncs()) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                for (Instr instr : bb.getInstrs()) {
                    if (instr instanceof Instr.AluInstr) {
                        opt(instr);
                    }
                }
            }
        }
    }

    /**
     * TODO
     * 除法和取模计算机都是向下取整的，这里移位是向零取整的，不太好
     *
     * @param instr
     * @return
     */
    private void opt(Instr instr) {
        assert instr instanceof Instr.AluInstr;
        Instr.AluInstr alu = (Instr.AluInstr) instr;
        switch (alu.getAluOp()) {
            case MUL:
                mulOpt(alu);
                break;
            case DIV:
                divOpt(alu);
                break;
            case REM:
                modOpt(alu);
                break;
            case FREM:
                fmodOpt(alu);
                break;
        }
    }

    private void mulOpt(Instr.AluInstr aluInstr) {
        Value op1 = aluInstr.getA1();
        Value op2 = aluInstr.getA2();
        if (op1 instanceof Constant.IntConst) {
            int val = ((Constant.IntConst) op1).getIntVal();
            int exp = isPow2(val);
            if (exp > 0) {
                Instr sub = new Instr.ShlInstr(SymType.BasicType.Basic_INT, op2, new Constant.IntConst(exp), aluInstr.getBelongBlock());
                aluInstr.insertAfter(sub);
                aluInstr.remove();
                aluInstr.replaceAllUsesWith(sub);
                for (Value v : aluInstr.getUsedValues()) {
                    v.getUsers().remove(aluInstr);
                }
                aluInstr.getUsedValues().clear();
            } else if (exp == 0) {
                aluInstr.replaceAllUsesWith(op2);
                aluInstr.remove();
                for (Value v : aluInstr.getUsedValues()) {
                    v.getUsers().remove(aluInstr);
                }
                aluInstr.getUsedValues().clear();
            }
        } else if (op2 instanceof Constant.IntConst) {
            int val = ((Constant.IntConst) op2).getIntVal();
            int exp = isPow2(val);
            if (exp > 0) {
                Instr sub = new Instr.ShlInstr(SymType.BasicType.Basic_INT, op1, new Constant.IntConst(exp), aluInstr.getBelongBlock());
                aluInstr.insertAfter(sub);
                aluInstr.remove();
                aluInstr.replaceAllUsesWith(sub);
                for (Value v : aluInstr.getUsedValues()) {
                    v.getUsers().remove(aluInstr);
                }
                aluInstr.getUsedValues().clear();
            } else if (exp == 0) {
                aluInstr.replaceAllUsesWith(op1);
                aluInstr.remove();
                for (Value v : aluInstr.getUsedValues()) {
                    v.getUsers().remove(aluInstr);
                }
                aluInstr.getUsedValues().clear();
            }
        }
    }

    private void divOpt(Instr.AluInstr aluInstr) {
        Value op1 = aluInstr.getA1();
        Value op2 = aluInstr.getA2();
        if (op2 instanceof Constant.IntConst) {
            int val = ((Constant.IntConst) op2).getIntVal();
            int exp = isPow2(val);
            if (exp == 0) {
                aluInstr.remove();
                aluInstr.replaceAllUsesWith(op1);
                for (Value v : aluInstr.getUsedValues()) {
                    v.getUsers().remove(aluInstr);
                }
                aluInstr.getUsedValues().clear();
            }
        }
    }

    /**
     * 这里负数对2幂次的取模会有些问题，所以舍弃这个优化
     * new 指令的def-use关系记得维护
     *
     * @param aluInstr
     * @return
     */
    private void modOpt(Instr.AluInstr aluInstr) {
        Value op1 = aluInstr.getA1();
        Value op2 = aluInstr.getA2();
        if (op2 instanceof Constant.IntConst) {
            int val = ((Constant.IntConst) op2).getIntVal();
            int exp = isPow2(val);
            if (exp == 0) {
                aluInstr.remove();
                aluInstr.replaceAllUsesWith(new Constant.IntConst(0));
                for (Value v : aluInstr.getUsedValues()) {
                    v.getUsers().remove(aluInstr);
                }
                aluInstr.getUsedValues().clear();
                return;
            }
            if (isPow2(val + 1) == 31) {
                aluInstr.remove();
                aluInstr.replaceAllUsesWith(op1);
                for (Value v : aluInstr.getUsedValues()) {
                    v.getUsers().remove(aluInstr);
                }
                aluInstr.getUsedValues().clear();
                return;
            }
        }
        Instr div = new Instr.AluInstr(SymType.BasicType.Basic_INT, aluInstr.getBelongBlock(), Instr.AluInstr.AluOp.DIV, op1, op2);
        Instr mul;
        int exp;
        if (op2 instanceof Constant.IntConst && (exp = isPow2(((Constant.IntConst) op2).getIntVal())) > 0) {
            mul = new Instr.ShlInstr(SymType.BasicType.Basic_INT, div, new Constant.IntConst(exp), aluInstr.getBelongBlock());
        } else {
            mul = new Instr.AluInstr(SymType.BasicType.Basic_INT, aluInstr.getBelongBlock(), Instr.AluInstr.AluOp.MUL, div, op2);
        }
        Instr.AluInstr sub = new Instr.AluInstr(SymType.BasicType.Basic_INT, aluInstr.getBelongBlock(), Instr.AluInstr.AluOp.SUB, op1, mul);
        aluInstr.insertAfter(div);
        div.insertAfter(mul);
        mul.insertAfter(sub);
        aluInstr.remove();
        aluInstr.replaceAllUsesWith(sub);
        for (Value v : aluInstr.getUsedValues()) {
            v.getUsers().remove(aluInstr);
        }
        aluInstr.getUsedValues().clear();
    }

    public void fmodOpt(Instr.AluInstr aluInstr) {
        Value op1 = aluInstr.getA1();
        Value op2 = aluInstr.getA2();
        Instr div = new Instr.AluInstr(SymType.BasicType.Basic_FLOAT, aluInstr.getBelongBlock(), Instr.AluInstr.AluOp.FDIV, op1, op2);
        Instr mul = new Instr.AluInstr(SymType.BasicType.Basic_FLOAT, aluInstr.getBelongBlock(), Instr.AluInstr.AluOp.FMUL, div, op2);
        Instr.AluInstr sub = new Instr.AluInstr(SymType.BasicType.Basic_FLOAT, aluInstr.getBelongBlock(), Instr.AluInstr.AluOp.FSUB, op1, mul);
        aluInstr.insertAfter(div);
        div.insertAfter(mul);
        mul.insertAfter(sub);
        aluInstr.remove();
        aluInstr.replaceAllUsesWith(sub);
        for (Value v : aluInstr.getUsedValues()) {
            v.getUsers().remove(aluInstr);
        }
        aluInstr.getUsedValues().clear();
    }
}
