package arm.armDS;

import arm.arminstructions.ArmMovInstr;
import arm.armoperands.FloatPhysicalReg;
import arm.armoperands.PhysicalReg;
import arm.armoperands.Reg;
import arm.armoperands.VirtualReg;
import tools.MyList;
import tools.MyListNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ArmFunction extends MyListNode {
    private int stackSize;
    private String functionName;
    private MyList<ArmBlock> armBlocks;
    private HashMap<Integer, VirtualReg> virMap; // <寄存器编号, 实体>
    private ArrayList<PhysicalReg> usedProtectedRegs;
    private ArrayList<FloatPhysicalReg> usedProtectedFloatRegs;
    private boolean isUseLr;
    private int extraStackSize = 0;
    private ArrayList<ArmMovInstr> funcArgMoves;

    public ArmFunction(String functionName) {
        this.stackSize = 0;
        this.functionName = functionName;
        this.armBlocks = new MyList<>();
        this.virMap = new HashMap<>();
        this.usedProtectedRegs = new ArrayList<>();
        this.usedProtectedFloatRegs = new ArrayList<>();
        this.funcArgMoves = new ArrayList<>();
    }

    public void addFuncArgMove(ArmMovInstr mov) {
        funcArgMoves.add(mov);
    }

    public ArrayList<ArmMovInstr> getFuncArgMoves() {
        return funcArgMoves;
    }

    public void tryAddExtraStackSize(int size) {
        if ((stackSize + size + funcArgMoves.size() * 4) % 8 != 0) {
            extraStackSize = 4;
        }
    }

    public boolean isUseLr() {
        return isUseLr;
    }

    public void setUseLr(boolean useLr) {
        isUseLr = useLr;
    }

    public ArrayList<PhysicalReg> getUsedProtectedRegs() {
        return usedProtectedRegs;
    }

    public ArrayList<FloatPhysicalReg> getUsedProtectedFloatRegs() {
        return usedProtectedFloatRegs;
    }

    public HashMap<Integer, VirtualReg> getVirMap() {
        return virMap;
    }

    public void addUsedProtectedRegs(PhysicalReg reg) {
        usedProtectedRegs.add(reg);
    }

    public void addUsedProtectedFloatReg(FloatPhysicalReg reg) {
        usedProtectedFloatRegs.add(reg);
    }

    public HashSet<Integer> getAllRegId(boolean isFloat) {
        HashSet<Integer> ret = new HashSet<>();
        for (ArmBlock armBlock : this.getArmBlocks()) {
            for (ArmInstr instr : armBlock.getArmInstructions()) {
                ArrayList<Reg> temp = new ArrayList<>();
                temp.addAll(instr.getDefRegs());
                temp.addAll(instr.getUseRegs());
                for (Reg reg : temp) {
                    if (!isFloat && reg instanceof PhysicalReg) {
                        ret.add(((PhysicalReg) reg).getNum());
                    }
                    if (isFloat && reg instanceof FloatPhysicalReg) {
                        ret.add(((FloatPhysicalReg) reg).getNum());
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        return getFunctionName();
    }

    public void addVirtual(VirtualReg reg) {
        virMap.put(reg.getNumber(), reg);
    }

    public void addArmBlock(ArmBlock armBlock) {
        armBlocks.insertAtTail(armBlock);
    }

    public String getFunctionName() {
        return functionName;
    }

    public void addStackSize(int delta) {
        // delta可以是负数, 这时候就是做减法
        stackSize += delta;
    }

    public int getStackSize() {
        return stackSize + extraStackSize;
    }

    public MyList<ArmBlock> getArmBlocks() {
        return armBlocks;
    }
}
