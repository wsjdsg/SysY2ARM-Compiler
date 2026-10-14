package midend;

import mir.Instr;
import mir.Value;

public class HashCalculator {
    public static Integer getHash(Value v) throws Exception {
        if (v instanceof Instr.AluInstr) {
            return calcALU(v);
        } else if (v instanceof Instr.IcmpInstr || v instanceof Instr.FcmpInstr) {
            return calcCmp(v);
        } else if (v instanceof Instr.ZextInstr) {
            return calcZext(v);
        } else if (v instanceof Instr.FptosiInstr) {
            return calcFPtosi(v);
        } else if (v instanceof Instr.SitofpInstr) {
            return calcSitofp(v);
        } else if (v instanceof Instr.GepInstr) {
            return calcGEP(v);
        } else {
            return v.hashCode();
            //call,bitcast,load,alloc,
        }
    }

    public static Integer calcALU(Value v) {
        assert v instanceof Instr.AluInstr;
        Instr.AluInstr alu = (Instr.AluInstr) v;
        Instr.AluInstr.AluOp op = ((Instr.AluInstr) v).getAluOp();
        if (op.equals(Instr.AluInstr.AluOp.ADD)) {
            return alu.getA1().hashCode() + alu.getA2().hashCode();
        } else if (op.equals(Instr.AluInstr.AluOp.FADD)) {
            return (alu.getA1().hashCode() + alu.getA2().hashCode()) << 1;
        } else if (op.equals(Instr.AluInstr.AluOp.SUB)) {
            return alu.getA1().hashCode() - alu.getA2().hashCode();
        } else if (op.equals(Instr.AluInstr.AluOp.FSUB)) {
            return (alu.getA1().hashCode() - alu.getA2().hashCode()) << 1;
        } else if (op.equals(Instr.AluInstr.AluOp.MUL)) {
            return alu.getA1().hashCode() * alu.getA2().hashCode();
        } else if (op.equals(Instr.AluInstr.AluOp.FMUL)) {
            return (alu.getA1().hashCode() * alu.getA2().hashCode()) << 1;
        } else if (op.equals(Instr.AluInstr.AluOp.DIV)) {
            return alu.getA2().hashCode() ^ alu.getA1().hashCode();
        } else if (op.equals(Instr.AluInstr.AluOp.FDIV)) {
            return alu.getA1().hashCode() ^ alu.getA2().hashCode() << 1;
        } else if (op.equals(Instr.AluInstr.AluOp.REM)) {
            return (alu.getA1().hashCode() ^ alu.getA2().hashCode()) << 10 | 10086;
        } else if (op.equals(Instr.AluInstr.AluOp.FREM)) {
            return (alu.getA1().hashCode() ^ alu.getA2().hashCode() << 5) | 1145;
        } else {
            throw new RuntimeException("not a alu op");
        }

    }

    public static Integer calcCmp(Value v) {
        if (v instanceof Instr.IcmpInstr) {
            return ((Instr.IcmpInstr) v).getA1().hashCode() + ((Instr.IcmpInstr) v).getA2().hashCode();
        } else if (v instanceof Instr.FcmpInstr) {
            return (((Instr.FcmpInstr) v).getA1().hashCode() + ((Instr.FcmpInstr) v).getA2().hashCode()) << 1;
        } else {
            throw new RuntimeException("not a cmp instr");
        }
    }

    public static Integer calcZext(Value v) {
        return ((Instr.ZextInstr) v).getA1().hashCode() *
                ((Instr.ZextInstr) v).getA1().hashCode();
    }

    public static Integer calcFPtosi(Value v) {
        return ((Instr.FptosiInstr) v).getA1().hashCode() << 3;
    }

    public static Integer calcSitofp(Value v) {
        return ((Instr.SitofpInstr) v).getA1().hashCode() << 5;
    }

    public static Integer calcGEP(Value v) {
        Instr.GepInstr gep = (Instr.GepInstr) v;
        Integer a = Integer.valueOf(0);
        for (int i = 0; i < gep.getUsedValues().size(); i++) {
            a = a + gep.getUsedValues().get(i).hashCode();
        }
        return a;
    }
}
