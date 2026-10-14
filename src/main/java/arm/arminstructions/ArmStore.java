package arm.arminstructions;

import arm.armDS.ArmBlock;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;

public class ArmStore extends ArmInstr {
    private ArmOperand addr;
    private ArmOperand offset;
    private ArmOperand srcData; // 要存进内存的源数据

    public ArmStore(ArmBlock ab, ArmOperand srcData, ArmOperand addr, ArmOperand offset) {
        super(ab);
        setAddr(addr);
        setSrcData(srcData);
        setOffset(offset);
        ab.addArmInstrToEnd(this);
    }

    public ArmStore(ArmOperand data, ArmOperand addr, ArmOperand offset) {
        super(null);
        setAddr(addr);
        setSrcData(data);
        setOffset(offset);
    }

    @Override
    public String toString() {
        ArmTools.addOff(1);
        return "\t" + getArmInstrName() + getCond() + "\t" +
                srcData + ",\t[" + addr + ",\t" + offset + getShift() + "]\n";
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

    public ArmOperand getSrcData() {
        return srcData;
    }

    public void setSrcData(ArmOperand srcData) {
        updateReg(this.srcData, srcData, true);
        this.srcData = srcData;
    }
}
