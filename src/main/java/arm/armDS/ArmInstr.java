package arm.armDS;

import arm.arminstructions.*;
import arm.armoperands.ArmOperand;
import arm.armoperands.Reg;
import mir.type.SymType;
import tools.MyListNode;

import java.util.ArrayList;

public class ArmInstr extends MyListNode {
    private InstrName name;
    private SymType type; // 指令类型
    private ArrayList<Reg> useRegs; // 存起来使用的寄存器
    private ArrayList<Reg> defRegs; // 存起来赋值的寄存器
    private ArmBlock curArmBlock;
    private ArmCond cond;
    private ArmBinary.Shift shift = null; // 位移对象

    public ArmInstr(ArmBlock ab, ArmCond cond) {
        this.curArmBlock = ab;
        this.useRegs = new ArrayList<>();
        this.defRegs = new ArrayList<>();
        this.shift = new ArmBinary.Shift();
        this.cond = cond;
    }

    public ArmInstr(ArmBlock ab) {
        this.curArmBlock = ab;
        this.useRegs = new ArrayList<>();
        this.defRegs = new ArrayList<>();
        this.shift = new ArmBinary.Shift();
        this.cond = ArmCond.any;
    }

    public ArmBinary.Shift getShift() {
        return shift;
    }

    public void setShift(ArmBinary.Shift shift) {
        this.shift = shift;
    }

    public ArmBlock getCurArmBlock() {
        return curArmBlock;
    }

    public void setCurArmBlock(ArmBlock curArmBlock) {
        this.curArmBlock = curArmBlock;
    }

    public void insertBeforeInstr(ArmInstr instr) {
        instr.setCurArmBlock(curArmBlock);
        this.insertBefore(instr);
    }

    // public ArrayList<ArmOperand> getArmOperands() {
    // 要保证所有的子类重写这个方法
    //    return armOperands;
    // }

    // public void addArmOperand(ArmOperand armOperand) {
    //    armOperands.add(armOperand);
    // }

    public void insertAfterInstr(ArmInstr instr) {
        instr.setCurArmBlock(this.curArmBlock);
        this.insertAfter(instr);
    }

    public ArmCond getCond() {
        return cond;
    }

    public void addDefReg(Reg reg) {
        this.defRegs.add(reg);
    }

    public String getArmInstrName() {
        if (this instanceof ArmBinary) {
            return ((ArmBinary) this).getType().getName();
        } else if (this instanceof ArmBranch) {
            return "b";
        } else if (this instanceof ArmCall) {
            return "bl";
        } else if (this instanceof ArmCompare) {
            // TODO cmn好像没用过 ???
            return ((ArmCompare) this).getCmpType().getName();
        } else if (this instanceof ArmFloatBinary) {
            return ((ArmFloatBinary) this).getType().getName();
        } else if (this instanceof ArmFma) {
            // TODO 好像没用过减号的 ???
            return "mla";
        } else if (this instanceof ArmJump) {
            return "b";
        } else if (this instanceof ArmLoad) {
            return "ldr";
        } else if (this instanceof ArmMov) {
            return "mov";
        } else if (this instanceof ArmReturn) {
            return "bx";
        } else if (this instanceof ArmStore) {
            return "str";
        } else if (this instanceof ArmVCompare) {
            return "vcmp.f32";
        } else if (this instanceof ArmVConvert) {
            return "vcvt";
        } else if (this instanceof ArmVLoad) {
            return "vldr";
        } else if (this instanceof ArmVMov) {
            return "vmov";
        } else if (this instanceof ArmVStore) {
            return "vstr";
        }
        System.out.println("function getArmInstrName gets error instr -> " +
                this);
        return "";
    }

    public void tryAddUseOrDefReg(ArmOperand op, boolean isUse) {
        if (op.isReg()) {
            if (isUse) {
                addUseReg((Reg) op);
            } else {
                addDefReg((Reg) op);
            }
        }
    }

    public void updateReg(ArmOperand oldOp, ArmOperand newOp, boolean isUse) {
        if (oldOp == null) {
            // 对象刚 new出来的时候调构造函数
            tryAddUseOrDefReg(newOp, isUse);
            return;
        }
        if (oldOp.isReg()) { // old是寄存器的话, 在add之前要先将旧的remove
            if (isUse) {
                useRegs.remove(oldOp);
            } else {
                defRegs.remove(oldOp);
            }
        }
        tryAddUseOrDefReg(newOp, isUse); // remove掉之后仍然要加新的
    }

    public ArrayList<Reg> getDefRegs() {
        return defRegs;
    }

    public void addUseReg(Reg reg) {
        this.useRegs.add(reg);
    }

    public ArrayList<Reg> getUseRegs() {
        return useRegs;
    }

    public void setArmBlock(ArmBlock ab) {
        ab.addArmInstrToEnd(this);
    }

    public enum InstrName {
        ret,
        load,
        store
    }

    public enum ArmCond {
        any(""), // 无条件执行
        noway(""), // 一定不执行, 仅仅做个标记
        eq("eq"),  // ==
        ne("ne"),  // !=
        slt("lt"),  // <  s->signed 有符号
        sle("le"),  // <=
        sgt("gt"),  // >
        sge("ge");   // >=

        public String name;

        ArmCond(String name) {
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
}
