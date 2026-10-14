package arm.regallocator;

import arm.armDS.ArmFunction;
import arm.armDS.ArmModule;
import arm.armDS.ArmTools;
import arm.arminstructions.ArmMovInstr;
import arm.armoperands.FloatPhysicalReg;
import arm.armoperands.Imme;
import arm.armoperands.PhysicalReg;

import java.util.HashSet;

public class MaintainStack {
    public MaintainStack(ArmTools armTools) {
        ArmModule armModule = armTools.getArmModule();
        for (ArmFunction armFunction : armModule.getArmFunctions()) {
            // 维护每个函数的栈
            maintainFunctionStack(armFunction);
        }
    }

    public void maintainFunctionStack(ArmFunction armFunction) {
        HashSet<Integer> physicalRegIds = armFunction.getAllRegId(false);
        // 4号到12号寄存器如果有的话, 那就加入到函数的保护栈中
        for (int i = 4; i <= 12; ++i) {
            if (physicalRegIds.contains(i)) {
                armFunction.addUsedProtectedRegs(new PhysicalReg(i));
            }
        }
        if (physicalRegIds.contains(14)) { // lr 寄存器
            armFunction.addUsedProtectedRegs(new PhysicalReg(14));
            armFunction.setUseLr(true);
        }
        // 浮点
        HashSet<Integer> fRegs = armFunction.getAllRegId(true);
        for (int i = 4; i < 32; ++i) {
            if (fRegs.contains(i)) {
                armFunction.addUsedProtectedFloatReg(new FloatPhysicalReg(i));
            }
        }

        int allSize = 4 * (armFunction.getUsedProtectedFloatRegs().size() +
                armFunction.getUsedProtectedRegs().size());

        armFunction.tryAddExtraStackSize(allSize); // 所有需要被保存的寄存器的数量 * 4
        // System.out.println("stack size->" + armFunction.getStackSize());
        for (ArmMovInstr mov : armFunction.getFuncArgMoves()) {
            // 关于函数栈的mov
            // 原来的imme + 函数栈大小 +
            int newImme = ((Imme) mov.getRhs()).getImme() + armFunction.getStackSize()
                    + 4 * (armFunction.getUsedProtectedRegs().size() + armFunction.getUsedProtectedFloatRegs().size());
            mov.setRhs(new Imme(newImme));
        }
    }
}
