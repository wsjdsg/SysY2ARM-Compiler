package midend;

import mir.Constant;
import mir.Instr;
import mir.Value;

/**
 * 企图将一条指令转化为常数从而可以删去
 */
public class InstrToConstant {
    public static final double eps = 1e-7;

    public static Value simplify(Instr instr) {
        if (instr instanceof Instr.AluInstr) {
            return alu2Const(instr);
        } else if (instr instanceof Instr.IcmpInstr || instr instanceof Instr.FcmpInstr) {
            return cmp2Const(instr);
        } else if (instr instanceof Instr.ZextInstr) {
            return zext2Const(instr);
        } else if (instr instanceof Instr.FptosiInstr) {
            return fptosi2Const(instr);
        } else if (instr instanceof Instr.SitofpInstr) {
            return sitofp2Const(instr);
        } else if (instr instanceof Instr.AshrInstr) {
            return ashr2Const(instr);
        } else if (instr instanceof Instr.LshrInstr) {
            return lshr2Const(instr);
        } else if (instr instanceof Instr.ShlInstr) {
            return shl2Const(instr);
        } else {
            return instr;
        }
    }

    public static Value ashr2Const(Instr v) {
        Value lop = v.getUsedValues().get(0);
        Value rop = v.getUsedValues().get(1);
        if (lop instanceof Constant.IntConst && rop instanceof Constant.IntConst) {
            return new Constant.IntConst(((Constant.IntConst) lop).getIntVal() >> ((Constant.IntConst) rop).getIntVal());
        }
        return v;
    }

    public static Value lshr2Const(Instr v) {
        Value lop = v.getUsedValues().get(0);
        Value rop = v.getUsedValues().get(1);
        if (lop instanceof Constant.IntConst && rop instanceof Constant.IntConst) {
            return new Constant.IntConst(((Constant.IntConst) lop).getIntVal() >>> ((Constant.IntConst) rop).getIntVal());
        }
        if (rop instanceof Constant.IntConst) {
            if (((Constant.IntConst) rop).getIntVal() >= 32) {
                return new Constant.IntConst(0);
            }
        }
        return v;
    }

    public static Value shl2Const(Instr v) {
        Value lop = v.getUsedValues().get(0);
        Value rop = v.getUsedValues().get(1);
        if (lop instanceof Constant.IntConst && rop instanceof Constant.IntConst) {
            return new Constant.IntConst(((Constant.IntConst) lop).getIntVal() << ((Constant.IntConst) rop).getIntVal());
        }
        if (rop instanceof Constant.IntConst) {
            if (((Constant.IntConst) rop).getIntVal() >= 32) {
                return new Constant.IntConst(0);
            }
        }
        return v;
    }

    //TODO
    public static Value alu2Const(Instr v) {
        assert v instanceof Instr.AluInstr;
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        Instr.AluInstr.AluOp op = ((Instr.AluInstr) v).getAluOp();
        if (lop instanceof Constant.IntConst && rop instanceof Constant.IntConst) {
            Constant.IntConst i1 = (Constant.IntConst) lop;
            Constant.IntConst i2 = (Constant.IntConst) rop;
            switch (op) {
                case ADD:
                    return new Constant.IntConst(i1.getIntVal() + i2.getIntVal());
                case SUB:
                    return new Constant.IntConst(i1.getIntVal() - i2.getIntVal());
                case MUL:
                    return new Constant.IntConst(i1.getIntVal() * i2.getIntVal());
                case DIV:
                    return new Constant.IntConst(i1.getIntVal() / i2.getIntVal());
                case REM:
                    return new Constant.IntConst(i1.getIntVal() % i2.getIntVal());
                default:
                    throw new RuntimeException("badbad");
            }
        } else if (lop instanceof Constant.FloatConst && rop instanceof Constant.FloatConst) {
            Constant.FloatConst f1 = (Constant.FloatConst) lop;
            Constant.FloatConst f2 = (Constant.FloatConst) rop;
            switch (op) {
                case FADD:
                    return new Constant.FloatConst(f1.getFloatVal() + f2.getFloatVal());
                case FSUB:
                    return new Constant.FloatConst(f1.getFloatVal() - f2.getFloatVal());
                case FMUL:
                    return new Constant.FloatConst(f1.getFloatVal() * f2.getFloatVal());
                case FDIV:
                    return new Constant.FloatConst(f1.getFloatVal() / f2.getFloatVal());
                case FREM:
                    return new Constant.FloatConst(f1.getFloatVal() % f2.getFloatVal());
                default:
                    throw new RuntimeException("bad bad ");
            }
        }
        return specialJudge((Instr.AluInstr) v);
    }

    public static Value specialJudge(Instr.AluInstr v) {
        Instr.AluInstr.AluOp op = v.getAluOp();
        switch (op) {
            case ADD:
                return specialJudgeAdd(v);
            case SUB:
                return specialJudgeSub(v);
            case MUL:
                return specialJudgeMul(v);
            case DIV:
                return specialJudgeDiv(v);
            case REM:
                return specialJudgeRem(v);
            case FADD:
                return specialJudgeFadd(v);
            case FSUB:
                return specialJudgeFsub(v);
            case FMUL:
                return specialJudgeFmul(v);
            case FDIV:
                return specialJudgeFdiv(v);
            case FREM:
                return specialJudgeFrem(v);
            default:
                throw new RuntimeException("special judge failed!");
        }
    }

    public static Value specialJudgeAdd(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.IntConst && ((Constant.IntConst) lop).getIntVal() == 0) {
            return rop;
        } else if (rop instanceof Constant.IntConst && ((Constant.IntConst) rop).getIntVal() == 0) {
            return lop;
        }
        return v;
    }

    public static Value specialJudgeSub(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (rop instanceof Constant.IntConst && ((Constant.IntConst) rop).getIntVal() == 0) {
            return lop;
        }
        if (lop.equals(rop)) {
            return new Constant.IntConst(0);
        }
        return v;
    }

    public static Value specialJudgeMul(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.IntConst) {
            if (((Constant.IntConst) lop).getIntVal() == 0) {
                return new Constant.IntConst(0);
            } else if (((Constant.IntConst) lop).getIntVal() == 1) {
                return rop;
            }
        }
        if (rop instanceof Constant.IntConst) {
            if (((Constant.IntConst) rop).getIntVal() == 0) {
                return new Constant.IntConst(0);
            } else if (((Constant.IntConst) rop).getIntVal() == 1) {
                return lop;
            }
        }
        return v;
    }

    public static Value specialJudgeDiv(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.IntConst && ((Constant.IntConst) lop).getIntVal() == 0) {
            return new Constant.IntConst(0);
        }
        if (lop.equals(rop)) {
            return new Constant.IntConst(1);
        }
        if (rop instanceof Constant.IntConst && ((Constant.IntConst) rop).getIntVal() == 1) {
            return lop;
        }
        return v;
    }

    public static Value specialJudgeRem(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.IntConst && ((Constant.IntConst) lop).getIntVal() == 0) {
            return new Constant.IntConst(0);
        }
        if (lop.equals(rop)) {
            return new Constant.IntConst(0);
        }
        if (rop instanceof Constant.IntConst && ((Constant.IntConst) rop).getIntVal() == 1) {
            return new Constant.IntConst(0);
        }
        return v;
    }

    public static Value specialJudgeFadd(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.FloatConst && Math.abs(((Constant.FloatConst) lop).getFloatVal()) < eps) {
            return rop;
        } else if (rop instanceof Constant.FloatConst && Math.abs(((Constant.FloatConst) rop).getFloatVal()) < eps) {
            return lop;
        }
        return v;
    }

    public static Value specialJudgeFsub(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (rop instanceof Constant.FloatConst && Math.abs(((Constant.FloatConst) rop).getFloatVal()) < eps) {
            return lop;
        }
        if (lop.equals(rop)) {
            return new Constant.FloatConst(0);
        }
        return v;
    }

    public static Value specialJudgeFmul(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.FloatConst) {
            if (Math.abs(((Constant.FloatConst) lop).getFloatVal()) < eps) {
                return new Constant.FloatConst(0);
            } else if (Math.abs(((Constant.FloatConst) lop).getFloatVal() - 1) < eps) {
                return rop;
            }
        }
        if (rop instanceof Constant.FloatConst) {
            if (Math.abs(((Constant.FloatConst) rop).getFloatVal()) < eps) {
                return new Constant.FloatConst(0);
            } else if (Math.abs(((Constant.FloatConst) rop).getFloatVal() - 1) < eps) {
                return lop;
            }
        }
        return v;
    }

    public static Value specialJudgeFdiv(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.FloatConst && Math.abs(((Constant.FloatConst) lop).getFloatVal()) < eps) {
            return new Constant.FloatConst(0);
        }
        if (lop.equals(rop)) {
            return new Constant.FloatConst(1);
        }
        if (rop instanceof Constant.FloatConst && Math.abs(((Constant.FloatConst) rop).getFloatVal() - 1) < eps) {
            return lop;
        }
        return v;
    }

    public static Value specialJudgeFrem(Instr.AluInstr v) {
        Value lop = ((Instr.AluInstr) v).getA1();
        Value rop = ((Instr.AluInstr) v).getA2();
        if (lop instanceof Constant.FloatConst && Math.abs(((Constant.FloatConst) lop).getFloatVal()) < eps) {
            return new Constant.FloatConst(0);
        }
        if (lop.equals(rop)) {
            return new Constant.FloatConst(0);
        }
        return v;
    }

    public static Value cmp2Const(Instr v) {
        if (v instanceof Instr.IcmpInstr) {
            Value left = ((Instr.IcmpInstr) v).getA1();
            Value right = ((Instr.IcmpInstr) v).getA2();
            if (left instanceof Constant.IntConst && right instanceof Constant.IntConst) {
                Constant.IntConst li = (Constant.IntConst) left;
                Constant.IntConst ri = (Constant.IntConst) right;
                switch (((Instr.IcmpInstr) v).getIcmpOp()) {
                    case EQ:
                        if (li.getIntVal() == ri.getIntVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case NE:
                        if (li.getIntVal() != ri.getIntVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case SLT:
                        if (li.getIntVal() < ri.getIntVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case SLE:
                        if (li.getIntVal() <= ri.getIntVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case SGT:
                        if (li.getIntVal() > ri.getIntVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case SGE:
                        if (li.getIntVal() >= ri.getIntVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                }
            }
            if (((Instr.IcmpInstr) v).getIcmpOp().equals(Instr.IcmpInstr.IcmpOp.EQ)) {
                if (left.equals(right)) {
                    return new Constant.BoolConst(1);
                }
            }
        } else if (v instanceof Instr.FcmpInstr) {
            Value left = ((Instr.FcmpInstr) v).getA1();
            Value right = ((Instr.FcmpInstr) v).getA2();
            if (left instanceof Constant.FloatConst && right instanceof Constant.FloatConst) {
                Constant.FloatConst li = (Constant.FloatConst) left;
                Constant.FloatConst ri = (Constant.FloatConst) right;
                switch (((Instr.FcmpInstr) v).getFcmpOp()) {
                    case OEQ:
                        if (li.getFloatVal() == ri.getFloatVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case ONE:
                        if (li.getFloatVal() != ri.getFloatVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case OLT:
                        if (li.getFloatVal() < ri.getFloatVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case OLE:
                        if (li.getFloatVal() <= ri.getFloatVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case OGT:
                        if (li.getFloatVal() > ri.getFloatVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                    case OGE:
                        if (li.getFloatVal() >= ri.getFloatVal()) {
                            return new Constant.BoolConst(1);
                        } else {
                            return new Constant.BoolConst(0);
                        }
                }
            }
            if (((Instr.FcmpInstr) v).getFcmpOp().equals(Instr.FcmpInstr.FcmpOp.OEQ)) {
                if (left.equals(right)) {
                    return new Constant.BoolConst(1);
                }
            }
        } else {
            throw new RuntimeException("not a cmp instr!");
        }
        return v;
    }

    public static Value zext2Const(Instr v) {
        var src = v.getUsedValues().get(0);
        if (src instanceof Constant.BoolConst) {
            return new Constant.IntConst(((Constant.BoolConst) src).getBoolVal());
        }
        return v;
    }

    public static Value fptosi2Const(Instr v) {
        Instr.FptosiInstr fp = (Instr.FptosiInstr) v;
        if (fp.getA1() instanceof Constant.FloatConst) {
            return new Constant.IntConst((int) ((Constant.FloatConst) fp.getA1()).getFloatVal());
        } else {
            return v;
        }
    }

    public static Value sitofp2Const(Instr v) {
        Instr.SitofpInstr si = (Instr.SitofpInstr) v;
        if (si.getA1() instanceof Constant.IntConst) {
            return new Constant.FloatConst(((Constant.IntConst) si.getA1()).getIntVal());
        }
        return v;
    }
}
