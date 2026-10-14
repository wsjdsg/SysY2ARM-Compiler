package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.VirtualReg;

public class ArmFma extends ArmInstr {
    // fma dst, src1 * src2 + src3
    ArmOperand mulOp1;
    ArmOperand mulOp2;
    ArmOperand addOp;
    ArmOperand dst;

    // TODO 有对这个进行优化吗 ??? 乘法优化????
    public ArmFma(ArmBlock armBlock, ArmOperand mulOp1,
                  ArmOperand mulOp2, ArmOperand addOp, ArmOperand dst) {
        super(armBlock);
        setMulOp1(mulOp1);
        setMulOp2(mulOp2);
        setAddOp(addOp);
        setDst(dst);
        armBlock.addArmInstrToEnd(this);
        this.dst.setIsDst(true);
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\t" + getArmInstrName() + getCond() + "\t" +
                dst + ",\t" + mulOp1 + ",\t" + mulOp2 + ",\t" + addOp + "\n";
    }

    public ArmOperand getMulOp1() {
        return mulOp1;
    }

    public void setMulOp1(ArmOperand mulOp1) {
        updateReg(this.mulOp1, mulOp1, true);
        this.mulOp1 = mulOp1;
    }

    public ArmOperand getMulOp2() {
        return mulOp2;
    }

    public void setMulOp2(ArmOperand mulOp2) {
        updateReg(this.mulOp2, mulOp2, true);
        this.mulOp2 = mulOp2;
    }

    public ArmOperand getAddOp() {
        return addOp;
    }

    public void setAddOp(ArmOperand addOp) {
        updateReg(this.addOp, addOp, true);
        this.addOp = addOp;
    }

    public ArmOperand getDst() {
        return dst;
    }

    public void setDst(ArmOperand dst) {
        updateReg(this.dst, dst, false);
        this.dst = dst;
        if (dst instanceof VirtualReg) {
            ((VirtualReg) dst).setDefInstr(this);
        }
    }
}
