package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.Reg;
import arm.armoperands.VirtualReg;

public class ArmBinary extends ArmInstr {
    private ArmOperand dst; // 赋值对象
    private ArmOperand lhs; // 左操作数
    private ArmOperand rhs; // 右操作数
    private BinaryType type; // 二元指令类型

    public ArmBinary(ArmBlock ab, BinaryType type, ArmOperand dst, ArmOperand lhs, ArmOperand rhs) {
        super(ab);
        this.type = type;
        setDst(dst);
        setLhs(lhs);
        setRhs(rhs);
        this.dst.setIsDst(true);
        ab.addArmInstrToEnd(this);
        setShift(new Shift(null, 0));
    }

    public ArmBinary(BinaryType type, ArmOperand dst, ArmOperand lhs, ArmOperand rhs) {
        super(null);
        this.type = type;
        setDst(dst);
        setLhs(lhs);
        setRhs(rhs);
        this.dst.setIsDst(true);
        setShift(new Shift(null, 0));
    }

    public ArmBinary(ArmBlock ab, BinaryType type, ArmOperand dst, ArmOperand lhs, ArmOperand rhs,
                     BinaryType shiftType, int shiftBitCount) {
        super(ab);
        this.type = type;
        setDst(dst);
        setLhs(lhs);
        setRhs(rhs);
        this.dst.setIsDst(true);
        ab.addArmInstrToEnd(this);
        setShift(new Shift(shiftType, shiftBitCount));
    }

    public ArmOperand getDst() {
        return dst;
    }

    public void setDst(ArmOperand dst) {
        updateReg(this.dst, dst, false); // 必须放在下面一条语句前面
        this.dst = dst;
        if (dst instanceof VirtualReg) { // 顺带维护一下def instr
            ((VirtualReg) dst).setDefInstr(this);
        }
    }

    public ArmOperand getLhs() {
        return lhs;
    }

    public void setLhs(ArmOperand lhs) {
        updateReg(this.lhs, lhs, true);
        this.lhs = lhs;
    }

    public ArmOperand getRhs() {
        return rhs;
    }

    public void setRhs(ArmOperand rhs) {
        updateReg(this.rhs, rhs, true);
        this.rhs = rhs;
    }

    public BinaryType getType() {
        return type;
    }

    public void setType(BinaryType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\t" + getArmInstrName() + getCond() + "\t" + dst + ",\t" +
                lhs + ",\t" + rhs + getShift() + "\n";
    }

    public enum BinaryType {
        none(""),
        add("add"),
        sub("sub"),
        rsb("rsb"),
        mul("mul"),
        sdiv("sdiv"), // 符号除, 都是符号除
        srem("unexpected arm binary instr type -> srem"),
        bic("unexpected arm binary instr type -> bic"),
        asr("asr"), // 算数右移
        sl("lsl"), // 逻辑左移(就是左移)
        lsr("lsr"), // 逻辑右移
        ror("ror"), // 循环右移
        rrx("rrx");  // 扩展循环右移

        public String name;

        BinaryType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class Shift { // 位移对象
        private BinaryType shiftType; // 这个仅可能取binaryType中的位移type
        private int shiftBitCount = -1; // 位移位数
        private Reg reg = null;
        private ArmInstr instr; // 位移对象附加在的指令

        public Shift(BinaryType type, int shiftBitCount) {
            this.shiftType = type;
            this.shiftBitCount = shiftBitCount;
        }

        public Shift() { // 空
            this.shiftType = null;
        }

        // TODO 如果涉及到位移位数是寄存器的话, 要用指令的 updateReg 方法来维护一下寄存器
        public Reg getReg() {
            return reg;
        }

        public void setReg(Reg reg) {
            this.reg = reg;
        }

        @Override
        public String toString() {
            if (shiftType == null || shiftBitCount == 0) {
                return "";
            }
            // 不是 null , 转 string
            if (reg == null) {
                return ", " + shiftType.getName() + " #" + shiftBitCount;
            } else {
                return ", " + shiftType.getName() + " " + reg;
            }
        }
    }
}
