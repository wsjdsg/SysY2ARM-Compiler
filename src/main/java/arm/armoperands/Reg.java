package arm.armoperands;

public class Reg extends ArmOperand {
    public Reg(OpType ty) {
        super(ty);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PhysicalReg && this instanceof PhysicalReg) {
            return ((PhysicalReg) obj).getNum() == ((PhysicalReg) this).getNum();
        } else if (obj instanceof FloatPhysicalReg && this instanceof FloatPhysicalReg) {
            return ((FloatPhysicalReg) obj).getNum() == ((FloatPhysicalReg) this).getNum();
        } else if (obj instanceof VirtualReg && this instanceof VirtualReg) {
            return ((VirtualReg) obj).getNumber() == ((VirtualReg) this).getNumber();
        }
        return false;
    }
}
