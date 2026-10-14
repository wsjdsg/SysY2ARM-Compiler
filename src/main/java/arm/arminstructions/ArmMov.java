package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.Imme;
import arm.armoperands.VirtualReg;

public class ArmMov extends ArmInstr implements ArmMovInstr {
    private ArmOperand dst;
    private ArmOperand rhs;
    private ArmMovType movType; // mov的类型

    public ArmMov(ArmOperand dst, ArmOperand src) {
        super(null); // 后面插入的时候会给他块
        setDst(dst);
        setRhs(src);
    }

    public ArmMov(ArmBlock ab, ArmOperand dst, ArmOperand rhs) {
        super(ab);
        this.movType = ArmMovType.mov; // 如果不传参数, 那就是默认mov类型
        setDst(dst);
        setRhs(rhs);
        ab.addArmInstrToEnd(this);
        this.dst.setIsDst(true);
    }

    public ArmMov(ArmBlock ab, ArmOperand dst, ArmOperand rhs, boolean isInsertHead) {
        super(ab);
        this.movType = ArmMovType.mov;
        setDst(dst);
        setRhs(rhs);
        if (isInsertHead) {
            ab.addArmInstrToHead(this);
        } else {
            System.out.println("this ArmMov must insert head!!!");
        }
        this.dst.setIsDst(true);
    }

    public ArmMov(ArmBlock ab, ArmOperand dst, ArmOperand rhs, ArmCond cond) {
        super(ab, cond);
        this.movType = ArmMovType.mov; // 如果不传参数, 那就是默认mov类型
        setDst(dst);
        setRhs(rhs);
        ab.addArmInstrToEnd(this);
        this.dst.setIsDst(true);
    }

    public void setMovType(ArmMovType movType) {
        this.movType = movType;
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

    @Override
    public String toString() {
        // TODO 要考虑movt movw, 前端不会考虑编码的问题
        //  就会一股脑把立即数或者寄存器传进来
        if (rhs instanceof Imme) {
            if (ArmTools.isCorrectArmImme(~((Imme) rhs).getImme())) {
                ArmTools.addOff(1); // 这种情况可以利用arm现成的指令去算
                int oppo = ~((Imme) rhs).getImme();
                return "\tmvn" + getCond() + "\t" + dst + ",\t#" + oppo + "\n";
            } else if (!ArmTools.isCorrectArmImme(((Imme) rhs).getImme())) {
                // 不能编码
                int high = ((((Imme) rhs).getImme() >>> 16) & 0xffff);
                int low = (((Imme) rhs).getImme() & 0xffff);
                ArmTools.addOff(1);
                StringBuilder ret = new StringBuilder();
                // 低位肯定得mov, 高位的话看看是不是0
                ret.append("\tmovw").append(getCond()).
                        append("\t").append(dst).append(",\t#").append(low).append("\n");
                if (high != 0) {
                    ArmTools.addOff(1);
                    ret.append("\tmovt").append(getCond()).
                            append("\t").append(dst).append(",\t#").append(high).append("\n");
                }
                return ret.toString();
            } else { // 符合立即数编码, 但是又不属于上面的特殊情况
                ArmTools.addOff(1);
                StringBuilder ret = new StringBuilder();
                ret.append("\tmov").append(getCond()).
                        append("\t").append(dst).append(",\t#").append(((Imme) rhs).getImme()).append(getShift()).append("\n");
                return ret.toString();
            }
        } else { // 右值 不是立即数, 而且也不是浮点
            ArmTools.addOff(1);
            StringBuilder sb = new StringBuilder();
            sb.append("\tmov").append(getCond()).
                    append("\t").append(dst).append(",\t").append(rhs).append(getShift()).append("\n");
            return sb.toString();
        }
    }

    public enum ArmMovType {
        mov,
        movt, // 将16位立即数加载到目标寄存器高位, 保持低位不变
        mvn,
        movw // 加载到目标寄存器低位, 并将其高16位清空
    }
}
