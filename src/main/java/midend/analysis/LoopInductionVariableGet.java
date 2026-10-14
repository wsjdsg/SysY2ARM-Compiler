//找出循环中的归纳变量
/*
exp:
while(i < 10){
    i++
}
i 为该循环归纳变量
 */
package midend.analysis;

import mir.Func;
import mir.Instr;
import mir.Value;
import tools.MyList;

public class LoopInductionVariableGet {
    private MyList<Func> funcs = new MyList<>();

    public LoopInductionVariableGet(MyList<Func> funcs) {
        this.funcs = funcs;
    }

    public void getLoopInductionVariable() {
        for (Func func : funcs) {
            getInductionForFunc(func);
        }
    }

    public void getInductionForFunc(Func func) {
        for (Loop loop : func.getAllLoops()) {
            getInductionForLoop(loop);
        }
    }

    public void getInductionForLoop(Loop loop) {
        if (!loop.isStandardLoop()) {
            return;
        }
        if (!loop.getExits().contains(loop.getEntryBlock())) {
            return;
        }

        Instr brInstr = loop.getEntryBlock().getInstrs().getLast();
        if(!(brInstr instanceof Instr.BranchInstr)) {
            return;
        }

        //icmp eq $1 10
        Value valFromPhi;       //:$1
        Value valToCmpWith;     //:10

        Value condInstr = ((Instr.BranchInstr) brInstr).getCond();
        if(condInstr instanceof Instr.IcmpInstr) {
            if(((Instr.IcmpInstr) condInstr).getA1() instanceof Instr.PhiInstr) {
                valFromPhi = ((Instr.IcmpInstr) condInstr).getA1();
                valToCmpWith = ((Instr.IcmpInstr) condInstr).getA2();
            } else if (((Instr.IcmpInstr) condInstr).getA2() instanceof Instr.PhiInstr) {
                valFromPhi = ((Instr.IcmpInstr) condInstr).getA2();
                valToCmpWith = ((Instr.IcmpInstr) condInstr).getA1();
            } else {
                return;
            }
        } else if (condInstr instanceof Instr.FcmpInstr) {
            if(((Instr.FcmpInstr) condInstr).getA1() instanceof Instr.PhiInstr) {
                valFromPhi = ((Instr.FcmpInstr) condInstr).getA1();
                valToCmpWith = ((Instr.FcmpInstr) condInstr).getA2();
            } else if (((Instr.FcmpInstr) condInstr).getA2() instanceof Instr.PhiInstr) {
                valFromPhi = ((Instr.FcmpInstr) condInstr).getA2();
                valToCmpWith = ((Instr.FcmpInstr) condInstr).getA1();
            } else {
                return;
            }
        } else {
            return;
        }

        if(!((Instr) valFromPhi).belongBlock.equals(loop.getEntryBlock())) {
            return;
        }

        Value opVal1 = ((Instr.PhiInstr) valFromPhi).getOptions().get(0);
        Value opVal2 = ((Instr.PhiInstr) valFromPhi).getOptions().get(1);

        Value valFromAlu;       // i = i + 1;
        Value valFromDef;       // int i = ...;

        if(loop.getLatchBlocks().contains(loop.getEntryBlock().getPredecessors().get(0))) {
            valFromAlu = opVal1;
            valFromDef = opVal2;
        } else {
            valFromAlu = opVal2;
            valFromDef = opVal1;
        }

        if(!(valFromAlu instanceof Instr.AluInstr)) {
            return;
        }

        // i = i + 1;
        Value valToCalWith;     //:1

        if(((Instr.AluInstr) valFromAlu).getA1().equals(valFromPhi)) {
            valToCalWith = ((Instr.AluInstr) valFromAlu).getA2();
        } else if (((Instr.AluInstr) valFromAlu).getA2().equals(valFromPhi)) {
            valToCalWith = ((Instr.AluInstr) valFromAlu).getA2();
        } else {
            return;
        }

        Instr.AluInstr.AluOp aluOp = ((Instr.AluInstr) valFromAlu).getAluOp();
        if(!(aluOp.equals(Instr.AluInstr.AluOp.ADD) || aluOp.equals(Instr.AluInstr.AluOp.FADD) ||
                aluOp.equals(Instr.AluInstr.AluOp.SUB) || aluOp.equals(Instr.AluInstr.AluOp.FSUB) ||
                aluOp.equals(Instr.AluInstr.AluOp.MUL) || aluOp.equals(Instr.AluInstr.AluOp.FMUL))) {
            return;
        }

        if((aluOp.equals(Instr.AluInstr.AluOp.SUB) || aluOp.equals(Instr.AluInstr.AluOp.FSUB)) &&
        ((Instr.AluInstr) valFromAlu).getA2().equals(valFromPhi)) {
            return;
        }

        loop.valFromPhi = valFromPhi;
        loop.valFromAlu = valFromAlu;
        loop.valFromDef = valFromDef;
        loop.valToCalWith = valToCalWith;
        loop.valToCmpWith = valToCmpWith;
        loop.cmpInstr = condInstr;
        loop.idcHasSetted = true;

//        System.out.println(loop.valFromPhi);
//        System.out.println(loop.valFromAlu);
//        System.out.println(loop.valFromDef);
//        System.out.println(loop.valToCalWith);
//        System.out.println(loop.valToCmpWith);
//        System.out.println(loop.cmpInstr);
//        System.out.println(true);
    }

}
