package midend;

import mir.*;

import java.util.ArrayList;

public class InstrCombine {
    private ArrayList<Func> funcs = new ArrayList<>();

    public InstrCombine(MyModule module) {
        for (Func func : module.funcs) {
            funcs.add(func);
        }
    }

    public void runInstrCombine() {
        for (Func func : funcs) {
            instrCombineForFunc(func);
        }
    }

    public void instrCombineForFunc(Func func) {
        for (BasicBlock block : func.getBasicBlocks()) {
            for (Instr instr : block.getInstrs()) {
                if (instr.canCombineInt()) {
                    ArrayList<Instr.AluInstr> aluInstrs = new ArrayList<>();
                    for (Instr instr1 : instr.getUsers()) {
                        aluInstrs.add((Instr.AluInstr) instr1);
                    }
                    combineAllInt((Instr.AluInstr) instr, aluInstrs);
                    instr.remove();
                    for(Value value : instr.getUsedValues()) {
                        value.getUsers().remove(instr);
                    }
                    for(Instr user : instr.getUsers()) {
                        user.getUsedValues().remove(instr);
                    }
                } else if (instr.canCombineFloat()) {
                    ArrayList<Instr.AluInstr> aluInstrs = new ArrayList<>();
                    for (Instr instr1 : instr.getUsers()) {
                        aluInstrs.add((Instr.AluInstr) instr1);
                    }
                    combineAllFloat((Instr.AluInstr) instr, aluInstrs);
                    instr.remove();
                    for(Value value : instr.getUsedValues()) {
                        value.getUsers().remove(instr);
                    }
                    for(Instr user : instr.getUsers()) {
                        user.getUsedValues().remove(instr);
                    }
                }
            }
        }
    }

    public void combineAllInt(Instr.AluInstr instr, ArrayList<Instr.AluInstr> aluInstrs) {
        for (Instr.AluInstr instr1 : aluInstrs) {
            combineTwoIntInstrs(instr, instr1);
        }
    }

    public void combineTwoIntInstrs(Instr.AluInstr instr1, Instr.AluInstr instr2) {
        int const1 = (int) (instr1.getA1() instanceof Constant ?
                ((Constant) instr1.getA1()).getConstValue() : ((Constant) instr1.getA2()).getConstValue());
        int const2 = (int) (instr2.getA1() instanceof Constant ?
                ((Constant) instr2.getA1()).getConstValue() : ((Constant) instr2.getA2()).getConstValue());
        int const1Idx = instr1.getA1() instanceof Constant ? 0 : 1;
        int const2Idx = instr2.getA1() instanceof Constant ? 0 : 1;

        boolean instr1IsADD = (instr1.getAluOp() == Instr.AluInstr.AluOp.ADD);
        boolean instr2IsADD = (instr2.getAluOp() == Instr.AluInstr.AluOp.ADD);

        if (const1Idx == 0 && const2Idx == 0) {
            if (instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const2 + const1);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
            } else if (instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const2 - const1);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
            } else if (!instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const2 + const1);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
                instr2.setAluOp(Instr.AluInstr.AluOp.SUB);
            } else if (!instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const2 - const1);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
                instr2.setAluOp(Instr.AluInstr.AluOp.ADD);
            }
        } else if (const1Idx == 0 && const2Idx == 1) {
            if (instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 + const2);
                instr2.modifyUse(intConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(1), 0);
            } else if (instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 - const2);
                instr2.modifyUse(intConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(1), 0);
                instr2.setAluOp(Instr.AluInstr.AluOp.ADD);
            } else if (!instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 + const2);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
                instr2.setAluOp(Instr.AluInstr.AluOp.SUB);
            } else if (!instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 - const2);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
            }
        } else if (const1Idx == 1 && const2Idx == 0) {
            if (instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 + const2);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            } else if (instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const2 - const1);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            } else if (!instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const2 - const1);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            } else if (!instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 + const2);
                instr2.modifyUse(intConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            }
        } else if (const1Idx == 1 && const2Idx == 1) {
            if (instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 + const2);
                instr2.modifyUse(intConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
            } else if (instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const1 - const2);
                instr2.modifyUse(intConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
                instr2.setAluOp(Instr.AluInstr.AluOp.ADD);
            } else if (!instr1IsADD && instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(const2 - const1);
                instr2.modifyUse(intConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
            } else if (!instr1IsADD && !instr2IsADD) {
                Constant.IntConst intConst = new Constant.IntConst(-const1 - const2);
                instr2.modifyUse(intConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
                instr2.setAluOp(Instr.AluInstr.AluOp.ADD);
            }
        }
    }

    public void combineAllFloat(Instr.AluInstr instr, ArrayList<Instr.AluInstr> aluInstrs) {
        for (Instr.AluInstr instr1 : aluInstrs) {
            combineTwoFloatInstrs(instr, instr1);
        }
    }

    public void combineTwoFloatInstrs(Instr.AluInstr instr1, Instr.AluInstr instr2) {
        float const1 = (float) (instr1.getA1() instanceof Constant ?
                ((Constant) instr1.getA1()).getConstValue() : ((Constant) instr1.getA2()).getConstValue());
        float const2 = (float) (instr2.getA1() instanceof Constant ?
                ((Constant) instr2.getA1()).getConstValue() : ((Constant) instr2.getA2()).getConstValue());
        int const1Idx = instr1.getA1() instanceof Constant ? 0 : 1;
        int const2Idx = instr2.getA1() instanceof Constant ? 0 : 1;

        boolean instr1IsFADD = (instr1.getAluOp() == Instr.AluInstr.AluOp.FADD);
        boolean instr2IsFADD = (instr2.getAluOp() == Instr.AluInstr.AluOp.FADD);

        if (const1Idx == 0 && const2Idx == 0) {
            if (instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 + const1);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
            } else if (instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 - const1);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
            } else if (!instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 + const1);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
                instr2.setAluOp(Instr.AluInstr.AluOp.FSUB);
            } else if (!instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 - const1);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
                instr2.setAluOp(Instr.AluInstr.AluOp.FADD);
            }
        } else if (const1Idx == 0 && const2Idx == 1) {
            if (instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const1 + const2);
                instr2.modifyUse(floatConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(1), 0);
            } else if (instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const1 - const2);
                instr2.modifyUse(floatConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(1), 0);
                instr2.setAluOp(Instr.AluInstr.AluOp.FADD);
            } else if (!instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 + const1);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
                instr2.setAluOp(Instr.AluInstr.AluOp.FSUB);
            } else if (!instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const1 - const2);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(1), 1);
            }
        } else if (const1Idx == 1 && const2Idx == 0) {
            if (instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const1 + const2);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            } else if (instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 - const1);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            } else if (!instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 - const1);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            } else if (!instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const1 + const2);
                instr2.modifyUse(floatConst, 0);
                instr2.modifyUse(instr1.getUsedValues().get(0), 1);
            }
        } else if (const1Idx == 1 && const2Idx == 1) {
            if (instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const1 + const2);
                instr2.modifyUse(floatConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
            } else if (instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const1 - const2);
                instr2.modifyUse(floatConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
                instr2.setAluOp(Instr.AluInstr.AluOp.FADD);
            } else if (!instr1IsFADD && instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(const2 - const1);
                instr2.modifyUse(floatConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
            } else if (!instr1IsFADD && !instr2IsFADD) {
                Constant.FloatConst floatConst = new Constant.FloatConst(-const1 - const2);
                instr2.modifyUse(floatConst, 1);
                instr2.modifyUse(instr1.getUsedValues().get(0), 0);
                instr2.setAluOp(Instr.AluInstr.AluOp.FADD);
            }
        }

    }

}
