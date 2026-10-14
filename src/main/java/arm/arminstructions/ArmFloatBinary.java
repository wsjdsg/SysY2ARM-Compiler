package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.VirtualReg;
import mir.Instr;

public class ArmFloatBinary extends ArmInstr {
    private FloatBinaryType type;
    private ArmOperand lhs;
    private ArmOperand rhs;
    private ArmOperand dst;

    public ArmFloatBinary(ArmBlock armBlock, FloatBinaryType type, ArmOperand dst, ArmOperand lhs, ArmOperand rhs) {
        super(armBlock);
        this.type = type;
        armBlock.addArmInstrToEnd(this);
        setLhs(lhs);
        setRhs(rhs);
        setDst(dst);
        this.dst.setIsDst(true);
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\t" + getArmInstrName() + getCond() + ".f32\t" +
                dst + ",\t" + lhs + ",\t" + rhs + "\n";
    }

    public static FloatBinaryType parserIrFloatBinary(Instr.AluInstr.AluOp op) {
        switch (op) {
            case FADD: return FloatBinaryType.VADD;
            case FSUB: return FloatBinaryType.VSUB;
            case FMUL: return FloatBinaryType.VMUL;
            case FDIV: return FloatBinaryType.VDIV;
            case FREM: return FloatBinaryType.VMOD;
            default: return FloatBinaryType.VADD;
        }
    }

    public enum FloatBinaryType {
        VADD("vadd"),
        VSUB("vsub"),
        VMUL("vmul"),
        VDIV("vdiv"),
        VMOD("vmod");

        String name;
        FloatBinaryType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public FloatBinaryType getType() {
        return type;
    }

    public void setType(FloatBinaryType type) {
        this.type = type;
    }

    public void setLhs(ArmOperand lhs) {
        updateReg(this.lhs, lhs, true);
        this.lhs = lhs;
    }

    public void setRhs(ArmOperand rhs) {
        updateReg(this.rhs, rhs, true);
        this.rhs = rhs;
    }

    public void setDst(ArmOperand dst) {
        updateReg(this.dst, dst, false);
        this.dst = dst;
        if (dst instanceof VirtualReg) {
            ((VirtualReg) dst).setDefInstr(this);
        }
    }

    public ArmOperand getLhs() {
        return lhs;
    }

    public ArmOperand getRhs() {
        return rhs;
    }

    public ArmOperand getDst() {
        return dst;
    }
}
