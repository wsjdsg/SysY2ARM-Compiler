package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import mir.Func;

public class ArmCall extends ArmInstr { // call和 bl 还是有所不同的, 因此单独给他建一个类
    private Func goalFunc;

    public ArmCall(ArmBlock ab) {
        super(ab);
    }

    public void setGoalFunc(Func func) {
        this.goalFunc = func;
    }
    // 一条跳转指令, 没有操作数

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        ArmTools.addOff(1); // 可能是传参
        String outName = "";
        if (getGoalFunc().getName().equals("starttime") ||
                getGoalFunc().getName().equals("stoptime")) {
            outName += "_sysy_";
            outName += getGoalFunc().getName();
        } else {
            outName += getGoalFunc().getName();
        }

        sb.append("\tbl\t").append(outName).append("\n");
        return sb.toString();
        // TOdo 这里的入栈操作 ??
    }

    public Func getGoalFunc() {
        return goalFunc;
    }


}
