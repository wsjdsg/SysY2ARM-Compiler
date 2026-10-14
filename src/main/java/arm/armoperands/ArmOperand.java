package arm.armoperands;

public class ArmOperand {
    private boolean isDst; // 是否在指令中是被赋值的那个
    private boolean needColor = false;
    private boolean isAllocated = false; // 被分配, 在寄存器分配之前, new 出来的对象都是未被分配
    private OpType type;

    public ArmOperand(OpType ty) {
        this.type = ty;
        this.isDst = false;
        this.needColor = false;
    }

    public boolean isDst() {
        return isDst;
    }

    public boolean isPreColored() {
        return false;
    }

    public boolean needColor() {
        return needColor;
    }

    public void setNeedColor(boolean needColor) {
        this.needColor = needColor;
    }

    public boolean isAllocated() {
        return isAllocated;
    }

    public void setAllocated(boolean allocated) {
        isAllocated = allocated;
    }

    public boolean isReg() {
        return this instanceof Reg;
    }

    public void setIsDst(boolean isDst) {
        this.isDst = isDst;
    }

    public boolean isFloat() {
        return this.type == OpType.floatImme || this.type == OpType.floatPhy ||
                (this.type == OpType.virtual && ((VirtualReg) this).isFloatVirtual());
    }

    public boolean isInt() {
        return this.type == OpType.imme || this.type == OpType.phy ||
                (this.type == OpType.virtual && ((VirtualReg) this).isIntVirtual());
    }

    public OpType getType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Reg && this instanceof Reg) {
            return ((Reg) obj).equals(this);
        } else if (obj instanceof Imme && this instanceof Imme) {
            return obj.equals(this);
        } else if (obj instanceof FloatImme && this instanceof FloatImme) {
            return obj.equals(this);
        }
        return false;
    }


    // arm 指令的操作数, 可以是以下类型: 虚拟寄存器/物理寄存器/操作数
    public enum OpType {
        virtual,
        phy,
        floatPhy,
        imme,
        floatImme;
    }

//    @Override
//    public boolean equals(Object obj) {
//        if (!(obj instanceof ArmOperand)) {
//            return false;
//        }
//        if (((ArmOperand) obj).getType() != this.type) {
//            return false;
//        }
//        if (this.type == OpType.imme) {
//            return ((Imme) this).getImme() == ((Imme) obj).getImme();
//        }
//        if (this.type == OpType.floatImme) {
//            return ((FloatImme) this).getImme() == ((FloatImme) obj).getImme();
//        }
//        return this.equals(obj);
//    }
}
