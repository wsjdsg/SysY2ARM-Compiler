package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;

public class ArmJump extends ArmInstr { // 无条件跳转
    private ArmBlock goal; // 跳转目标是一个块

    public ArmJump(ArmBlock curArmBlock, ArmBlock jumpGoal) {
        super(curArmBlock);
        this.goal = jumpGoal;
        curArmBlock.addArmInstrToEnd(this);
    }

    public ArmBlock getGoal() {
        return goal;
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\t" + getArmInstrName() + "\t" + goal + "\n";
    }

    public void setGoal(ArmBlock goal) {
        this.goal = goal;
    }
}
