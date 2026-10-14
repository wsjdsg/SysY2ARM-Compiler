package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;

public class ArmBranch extends ArmInstr {
    // 有条件, 选择标签跳转
    private ArmBlock trueArmBlock;
    // private ArmBlock falseArmBlock; // 如果
    private ArmCond branchCond; // 跳转条件

    public ArmBranch(ArmBlock curBlock, ArmCond branchCond, ArmBlock trueArmBlock) {
        super(curBlock);
        this.branchCond = branchCond;
        this.trueArmBlock = trueArmBlock;
        curBlock.addArmInstrToEnd(this);
    }

    public ArmBlock getTrueArmBlock() {
        return trueArmBlock;
    }

    public void setTrueArmBlock(ArmBlock trueArmBlock) {
        this.trueArmBlock = trueArmBlock;
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\t" + getArmInstrName() + branchCond + "\t" + trueArmBlock + "\n";
    }

    public ArmCond getBranchCond() {
        return branchCond;
    }

    public void setBranchCond(ArmCond branchCond) {
        this.branchCond = branchCond;
    }
}
