package arm.armoperands;

import arm.armDS.ArmFunction;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;

import java.util.Objects;

public class VirtualReg extends Reg {
    private static int maxNumber = 1; // 最大虚拟寄存器编号, 虚拟寄存器从 1号开始编号
    private int number; // 虚拟寄存器编号
    private String irName; // 对应的ir变量名
    private boolean isFloatVirtual;
    private boolean isGlobalVar = false;
    private ArmInstr defInstr;

    public VirtualReg() {
        super(OpType.virtual);
    }

    // 不是全局就需要分配
    public VirtualReg(String irName, boolean isFloat) { // 不说不加进函数中就是加进去
        super(OpType.virtual);
        this.irName = irName;
        this.number = (maxNumber++);
        this.isFloatVirtual = isFloat;
        this.setNeedColor(true);
        ArmTools.getCurHandleFunction().addVirtual(this);
    }

    /*public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        VirtualReg other = (VirtualReg) obj;

        return isGlobalVar() == other.isGlobalVar() && this.getNumber() == other.getNumber()


    }*/

    public VirtualReg(ArmFunction armFunction) {
        super(OpType.virtual);
        this.number = (maxNumber++);
        this.setNeedColor(true);
        armFunction.addVirtual(this);
    }

    public VirtualReg(ArmFunction armFunction, boolean isFloat) {
        super(OpType.virtual);
        this.number = (maxNumber++);
        this.isFloatVirtual = isFloat;
        this.setNeedColor(true);
        armFunction.addVirtual(this);
    }

    public VirtualReg(boolean isFloatVirtual) {
        super(OpType.virtual);
        this.number = (maxNumber++);
        this.irName = "$" + String.valueOf(this.number); // 没有参数的构造器搞出来的virtualReg不由ir变量名决定
        this.isFloatVirtual = isFloatVirtual;
        this.setNeedColor(true);
        ArmTools.getCurHandleFunction().addVirtual(this);
    }

    public static VirtualReg NewVirtualGlobal(String globalName) { // 传进来的是不带前缀的
        VirtualReg ret = new VirtualReg();
        ret.isGlobalVar = true;
        ret.isFloatVirtual = false;
        ret.irName = globalName;
        return ret;
    }

    public ArmInstr getDefInstr() {
        return defInstr;
    }

    public void setDefInstr(ArmInstr defInstr) {
        this.defInstr = defInstr;
    }

    public int getNumber() {
        return number;
    }

    public boolean isGlobalVar() {
        return isGlobalVar;
    }

    @Override
    public String toString() {
        if (this.isGlobalVar())
            return /*"print virtual reg (not global var) " + */this.irName;
        if (isFloatVirtual)
            return "$F" + number;
        return "$" + number;
    }

    public boolean isIntVirtual() { // TODO 全局变量虚拟寄存器, 关于 isInt isFloat方法的维护 ?????
        return !isFloatVirtual();
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    public boolean isFloatVirtual() {
        return isFloatVirtual;
    }
}
