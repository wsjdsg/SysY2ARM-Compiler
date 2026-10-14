package arm.arminstructions;

import arm.armoperands.ArmOperand;

public interface ArmMovInstr {
    public ArmOperand getRhs();

    public void setRhs(ArmOperand rhs);

    public ArmOperand getDst();
}
