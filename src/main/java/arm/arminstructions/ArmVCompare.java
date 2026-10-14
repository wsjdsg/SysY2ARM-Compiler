package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;

public class ArmVCompare extends ArmInstr {
    private ArmOperand lhs;
    private ArmOperand rhs;
    private ArmCond armCond;
    private VCmpType vCmpType;

    public ArmVCompare(ArmBlock armBlock, VCmpType type, ArmCond armCond, ArmOperand lhs, ArmOperand rhs) {
        super(armBlock);
        setLhs(lhs);
        setRhs(rhs);
        this.armCond = armCond;
        this.vCmpType = type;
        armBlock.addArmInstrToEnd(this);
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        if (rhs == null) {
            return "\tvcmp.f32\t" + lhs + ",\t0.0\n" +
                    "\tvmrs\tAPSR_nzcv, fpscr\n";
        }
        return "\tvcmp.f32\t" + lhs + ",\t" + rhs + getShift() + "\n" +
                "\tvmrs\tAPSR_nzcv, fpscr\n";
    }

    public ArmOperand getLhs() {
        return lhs;
    }

    public void setLhs(ArmOperand lhs) {
        updateReg(this.lhs, lhs, true);
        this.lhs = lhs;
    }

    public ArmOperand getRhs() {
        return rhs;
    }

    public void setRhs(ArmOperand rhs) {
        updateReg(this.rhs, rhs, true);
        this.rhs = rhs;
    }

    public ArmCond getArmCond() {
        return armCond;
    }

    public void setArmCond(ArmCond armCond) {
        this.armCond = armCond;
    }

    public VCmpType getvCmpType() {
        return vCmpType;
    }

    public void setvCmpType(VCmpType vCmpType) {
        this.vCmpType = vCmpType;
    }

    public enum VCmpType {
        vcmp,
        vcmpe
    }
}
