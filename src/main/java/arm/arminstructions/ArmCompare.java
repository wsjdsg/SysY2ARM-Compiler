package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;

public class ArmCompare extends ArmInstr {
    private CmpType cmpType;
    private ArmCond cmpCond;
    private ArmOperand lhs;
    private ArmOperand rhs;

    // 实现 ir 中的 icmp指令
    public ArmCompare(ArmBlock armBlock, CmpType type, ArmCond cond, ArmOperand lhs, ArmOperand rhs) {
        super(armBlock);
        armBlock.addArmInstrToEnd(this);
        this.cmpType = type;
        this.cmpCond = cond;
        setLhs(lhs);
        setRhs(rhs);
    }

    public enum CmpType {
        cmp("cmp"),
        cmn("cmn");

        CmpType(String name) {
            this.name = name;
        }
        private String name;

        public String getName() {
            return name;
        }
    }

    public CmpType getCmpType() {
        return cmpType;
    }

    public ArmCond getCmpCond() {
        return cmpCond;
    }

    public ArmOperand getLhs() {
        return lhs;
    }

    public void setCmpType(CmpType cmpType) {
        this.cmpType = cmpType;
    }

    public void setCmpCond(ArmCond cmpCond) {
        this.cmpCond = cmpCond;
    }

    public void setLhs(ArmOperand lhs) {
        updateReg(this.lhs, lhs, true);
        this.lhs = lhs;
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\t" + getArmInstrName() + "\t" + lhs + ",\t" + rhs + getShift() + "\n";
    }

    public void setRhs(ArmOperand rhs) {
        updateReg(this.rhs, rhs, true);
        this.rhs = rhs;
    }

    public ArmOperand getRhs() {
        return rhs;
    }
}
