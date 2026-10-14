package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.VirtualReg;

public class ArmVConvert extends ArmInstr {
    // 32位, 可以从整数的视角看, 可以从浮点视角看.
    // vcvt.s32.f32 将一个 整数视角下看是a 的32位, 转换成 在浮点视角下看是a 的32位. (各个位发生变化)
    private CvtDataType fromType;
    private CvtDataType toType;
    private ArmOperand rhs;
    private ArmOperand dst;

    public ArmVConvert(ArmBlock ab, CvtDataType fromType,
                       CvtDataType toType, ArmOperand rhs, ArmOperand dst) {
        super(ab);
        ab.addArmInstrToEnd(this);
        this.fromType = fromType;
        this.toType = toType;
        setDst(dst);
        setRhs(rhs);
        this.dst.setIsDst(true);
    }

    @Override
    public String toString() {
        // 注意指令的格式, 不要写反了
        ArmTools.addOff(1);
        return "\tvcvt." + toType.getName() + "32." + fromType.getName() +
                "32\t" + dst + ",\t" + rhs + "\n";
    }

    public ArmOperand getRhs() {
        return rhs;
    }

    public void setRhs(ArmOperand rhs) {
        updateReg(this.rhs, rhs, true);
        this.rhs = rhs;
    }

    public ArmOperand getDst() {
        return dst;
    }

    public void setDst(ArmOperand dst) {
        updateReg(this.dst, dst, false);
        this.dst = dst;
        if (dst instanceof VirtualReg) {
            ((VirtualReg) dst).setDefInstr(this);
        }
    }

    public enum CvtDataType {
        i("s"),
        f("f");

        private String name;

        CvtDataType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
