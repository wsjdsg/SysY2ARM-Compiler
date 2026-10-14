package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.VirtualReg;

public class ArmVLoad extends ArmInstr implements ArmLoadInstr {
    private ArmOperand dst; // 目标
    private ArmOperand addr; // 右值
    private ArmOperand offset; // load 指令是有偏移的

    public ArmVLoad(ArmBlock ab, ArmOperand dst, ArmOperand addr, ArmOperand offset) {
        super(ab);
        setDst(dst);
        setAddr(addr);
        setOffset(offset);
        ab.addArmInstrToEnd(this);
        dst.setIsDst(true);
    }

    public ArmVLoad(ArmOperand dst, ArmOperand addr, ArmOperand offset) {
        super(null);
        setDst(dst);
        setAddr(addr);
        setOffset(offset);
    }

    public ArmVLoad(ArmBlock ab, ArmOperand dst, ArmOperand addr, ArmOperand offset, boolean isInsertHead) {
        super(ab);
        setDst(dst);
        setAddr(addr);
        setOffset(offset);
        if (isInsertHead) {
            ab.addArmInstrToHead(this);
        } else {
            System.out.println("this ArmVLoad must insert head!!!");
        }
        dst.setIsDst(true);
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\tvldr" + getCond() + "\t" + dst +
                ",\t[" + addr + ",\t" + offset + getShift() + "]\n";
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
