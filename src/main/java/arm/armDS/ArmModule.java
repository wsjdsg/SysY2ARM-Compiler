package arm.armDS;

import tools.MyList;

import java.util.ArrayList;

public class ArmModule {
    public MyList<ArmFunction> armFunctions = new MyList<>();
    public ArmFunction mainFunction; // 是上面的列表中的一个, 主函数
    //

    public void addFunctions(ArmFunction armFunction) {
        if (armFunction.getFunctionName().equals("main")) {
            this.mainFunction = armFunction;
        }
        this.armFunctions.insertAtTail(armFunction);
    }

    public ArmFunction getMainFunction() {
        return mainFunction;
    }

    public MyList<ArmFunction> getArmFunctions() {
        return armFunctions;
    }

    @Override
    public String toString() {
        // TODO
        return null;
    }
}
