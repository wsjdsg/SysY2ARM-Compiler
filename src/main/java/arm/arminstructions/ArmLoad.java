package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.VirtualReg;

public class ArmLoad extends ArmInstr implements ArmLoadInstr {
    private ArmOperand dst; // 目标
    private ArmOperand addr; // 右值
    private ArmOperand offset; // load 指令是有偏移的

    public ArmLoad(ArmOperand dst, ArmOperand addr, ArmOperand offset) {
        super(null); // 给一个空块, 后面还是会给他赋块的
        setDst(dst);
        setAddr(addr);
        setOffset(offset);
    }

    public ArmLoad(ArmBlock ab, ArmOperand dst, ArmOperand addr, ArmOperand offset) {
        super(ab);
        setDst(dst);
        setAddr(addr);
        setOffset(offset);
        ab.addArmInstrToEnd(this);

        this.dst.setIsDst(true);
    }

    public ArmLoad(ArmBlock ab, ArmOperand dst, ArmOperand addr, ArmOperand offset, boolean isInsertHead) {
        super(ab);
        setDst(dst);
        setAddr(addr);
        setOffset(offset);
        if (isInsertHead) {
            ab.addArmInstrToHead(this);
        } else {
            System.out.println("this ArmLoad must be insert head!!!");
        }
        this.dst.setIsDst(true);
    }

    @Override
    public String toString() {
        if (getAddr() instanceof VirtualReg && ((VirtualReg) getAddr()).isGlobalVar()) {
            ArmTools.addOff(2); // TODO 这里是什么意思 ???   load-全局
            String instr1 = "\tmovw" + getCond() + "\t" + dst + ",\t:lower16:" + addr + "\n";
            String instr2 = "\tmovt" + getCond() + "\t" + dst + ",\t:upper16:" + addr + "\n";
            return instr1 + instr2;
        } else {
            ArmTools.addOff(1);
            return "\t" + getArmInstrName() + getCond() + "\t" +
                    dst + ",\t[" + addr + ",\t" + offset + getShift() + "]\n";
        }
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

    public ArmOperand getAddr() {
        return addr;
    }

    public void setAddr(ArmOperand addr) {
        updateReg(this.addr, addr, true);
        this.addr = addr;
    }

    public ArmOperand getOffset() {
        return offset;
    }

    public void setOffset(ArmOperand offset) {
        updateReg(this.offset, offset, true);
        this.offset = offset;
    }
}
