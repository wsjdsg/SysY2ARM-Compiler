package arm.armDS;

import tools.MyList;
import tools.MyListNode;

public class ArmBlock extends MyListNode {
    private MyList<ArmInstr> armInstructions;
    private ArmFunction curArmFunction; // 所在的函数
    // 下面这俩是本armBlock的可能的后继块, 一个块的后继块可能有0个, 1个(规定true块来存后继), 2个(下面这俩都不为null)
    private ArmBlock trueNextBlock;
    private ArmBlock falseNextBlock;
    private String blockName;
    private int loopDepth; // ir会传来循环深度
    private boolean isRetValBlock = false;
    private boolean isRetFloatValBlock;

    public ArmBlock(ArmFunction curArmFunction, String blockName) {
        this.trueNextBlock = null;
        this.falseNextBlock = null;
        this.armInstructions = new MyList<>();
        this.curArmFunction = curArmFunction;
        this.blockName = blockName;
    }

    @Override
    public String toString() {
        return blockName;
    }

    public int getLoopDepth() {
        return loopDepth;
    }

    public void setLoopDepth(int loopDepth) {
        this.loopDepth = loopDepth;
    }

    public MyList<ArmInstr> getArmInstructions() {
        return armInstructions;
    }

    public String getBlockName() {
        return blockName;
    }

    public ArmBlock getTrueNextBlock() {
        return this.trueNextBlock;
    }

    public void setTrueNextBlock(ArmBlock trueNextBlock) {
        this.trueNextBlock = trueNextBlock;
    }

    public ArmBlock getFalseNextBlock() {
        return this.falseNextBlock;
    }

    public void setFalseNextBlock(ArmBlock falseNextBlock) {
        this.falseNextBlock = falseNextBlock;
    }

    public void addArmInstrToEnd(ArmInstr instr) {
        armInstructions.insertAtTail(instr);
    }

    public void addArmInstrToHead(ArmInstr instr) {
        armInstructions.insertAtHead(instr);
    }

    public ArmFunction getCurArmFunction() {
        return curArmFunction;
    }

    public void setIsRetValBlock(boolean isRetValBlock) {
        this.isRetValBlock = isRetValBlock;
    }

    public void setIsRetFloatValBlock(boolean is) {
        this.isRetFloatValBlock = is;
    }

    public boolean isRetFloatValBlock() {
        return isRetFloatValBlock;
    }

    public boolean isRetValBlock() {
        return isRetValBlock;
    }

}
