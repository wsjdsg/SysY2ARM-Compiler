package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.VirtualReg;

public class ArmVMov extends ArmInstr implements ArmMovInstr {
    // vmov s0, r0   指令仅仅是将位模式进行传递, 比如本指令仅仅是将r0的位传给了s0
    ArmOperand dst;
    ArmOperand rhs; // 浮点立即数

    public ArmVMov(ArmBlock ab, ArmOperand dst, ArmOperand rhs) {
        super(ab);
        setDst(dst);
        setRhs(rhs);
        ab.addArmInstrToEnd(this);
        this.dst.setIsDst(true);
    }

    public ArmVMov(ArmBlock ab, ArmOperand dst, ArmOperand rhs, boolean isInsertHead) { // 插入这个块的头部而不是尾部
        super(ab);
        setDst(dst);
        setRhs(rhs);
        if (isInsertHead) {
            ab.addArmInstrToHead(this);
        } else {
            System.out.println("this ArmVMov must insert head!!!");
        }
        this.dst.setIsDst(true);
    }

    public boolean isPureFloatMov() {
        return dst.isFloat() && rhs.isFloat();
    }

    @Override
    public String toString() {
        /*
         * 可编码浮点立即数->s0
         * r0->s0
         * s0->r0
         *
         * */
        ArmTools.addOff(1);
        // 要么rhs是一个合法的浮点立即数 , 要么 rhs 是一个寄存器
//        if (rhs instanceof FloatImme) {
        return "\tvmov" + getCond() + "\t" + dst + ",\t" + rhs + "\n";
//        }
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

    public ArmOperand getRhs() {
        return rhs;
    }

    public void setRhs(ArmOperand rhs) {
        updateReg(this.rhs, rhs, true);
        this.rhs = rhs;
    }
}
