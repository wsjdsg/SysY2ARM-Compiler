package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmFunction;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.FloatPhysicalReg;
import arm.armoperands.Imme;
import arm.armoperands.PhysicalReg;
import arm.armoperands.Reg;

import java.util.ArrayList;

public class ArmReturn extends ArmInstr {
    // 函数的最末尾, 不是一条单纯的指令, 而是由若干条指令组合起来的复合指令
    // 在转arm的时候要toString成好几个指令的组合
    private Reg useReg;

    public ArmReturn(ArmBlock ab) {
        super(ab);
        this.useReg = new PhysicalReg(0); // r0存返回值
        ab.addArmInstrToEnd(this);
    }

    @Override
    public String toString() { // toString是对Object类的方法的重写
        // TODO 这里的 toString方法很有讲究, 要认真写
        StringBuilder sb = new StringBuilder();
        ArmFunction f = getCurArmBlock().getCurArmFunction();
        int stackSize = f.getStackSize();

        if (stackSize > 0) { // 让函数栈指针加回去
            ArmTools.addOff(1);
            if (ArmTools.isCorrectArmImme(stackSize)) {
                sb.append("\t").append("add").append("\tsp,\tsp,\t#").append(stackSize).append("\n");
            } else if (ArmTools.isCorrectArmImme(-stackSize)) {
                sb.append("\t").append("sub").append("\tsp,\tsp,\t#").append(stackSize).append("\n");
            } else {
                // 不能编码的话, 搞出来5号寄存器
                ArmTools.addOff(1);
                String s = new ArmMov(getCurArmBlock(), new PhysicalReg(5), new Imme(stackSize)).toString();
                sb.append(s).append("\tadd\tsp,\tsp,\t").append(new PhysicalReg(5)).append("\n");
            }
        }
        // 释放寄存器
        ArrayList<Integer> floatRegIds = new ArrayList<>();
        for (FloatPhysicalReg reg : f.getUsedProtectedFloatRegs()) {
            floatRegIds.add(reg.getNum());
        }
        if (floatRegIds.size() > 0) {
            // 提取出来所有连续的
            ArrayList<ArrayList<Integer>> connectList = new ArrayList<>();
            for (int i = 0; i < floatRegIds.size(); i++) {
                if (i == 0) {
                    connectList.add(new ArrayList<>());
                    connectList.get(0).add(floatRegIds.get(i));
                } else if (floatRegIds.get(i) - floatRegIds.get(i - 1) == 1) {
                    connectList.get(connectList.size() - 1).add(floatRegIds.get(i));
                } else {
                    connectList.add(new ArrayList<>());
                    connectList.get(connectList.size() - 1).add(floatRegIds.get(i));
                }
            }

//            for (ArrayList<Integer> item : connectList) {
//                sb.append("\tvpop\t{");
//                for (int j = 0; j < item.size(); j++) {
//                    sb.append("s").append(item.get(j)).append(j == item.size() - 1 ? "}\n" : ",\t");
//                }
//            }
            int size = connectList.size();
            for (int i = size - 1; i >= 0; --i) {
                sb.append("\tvpop\t{");
                var item = connectList.get(i);
                for (int j = 0; j < item.size(); j++) {
                    sb.append("s").append(item.get(j)).append(j == item.size() - 1 ? "}\n" : ",\t");
                }
            }
        }
        boolean isUsedLr = false;
        // 遍历函数使用的所有需要保存的普通寄存器
        ArrayList<String> regs = new ArrayList<>(); // 寄存器名字
        for (PhysicalReg reg : f.getUsedProtectedRegs()) {
            if (reg.getName().equals("lr")) {
                regs.add("pc"); // TODO 恢复pc, 弹出pc是怎么说的 ????
                isUsedLr = true; // 函数使用了lr寄存器
            } else { // 除了lr 之外的寄存器全部加进去
                regs.add(reg.getName());
            }
        }

        if (regs.size() > 0) {
            ArmTools.addOff(1);
            sb.append("\tpop\t{");
            for (int i = 0; i < regs.size(); ++i) { // 弹出所有普通寄存器除了lr
                sb.append(regs.get(i)).append(i == regs.size() - 1 ? "}\n" : ",\t");
            }
        }

        if (!isUsedLr) { // 要是没使用lr, 直接显示跳转回去, 也不用恢复pc了
            ArmTools.addOff(1);
            sb.append("\tbx\tlr\n");
        }
        return sb.toString();
    }

    public Reg getUseReg() {
        return this.useReg;
    }

    public void setUseReg(Reg useReg) {
        this.useReg = useReg;
    }
}
