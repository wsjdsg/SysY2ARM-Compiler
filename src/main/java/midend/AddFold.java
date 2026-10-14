package midend;

import mir.*;
import mir.type.SymType;

import java.util.ArrayList;

/**
 * 仅仅针对
 * sum=a1+a2+a3+...+an
 * a1=a2=a3=a4...的情况进行一下处理
 */
public class AddFold {
    private MyModule module;

    public AddFold(MyModule module) {
        this.module = module;
    }

    public void run() {
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                for (BasicBlock bb : func.getBasicBlocks()) {
                    searchOpt(bb);
                }
            }
        }
    }

    public void searchOpt(BasicBlock bb) {
        for (Instr instr : bb.getInstrs()) {
            int cnt = 0;
            ArrayList<Instr> rms = new ArrayList<>();
            if (instr instanceof Instr.AluInstr &&
                    ((Instr.AluInstr) instr).getAluOp() == Instr.AluInstr.AluOp.ADD) {
                Value lop = ((Instr.AluInstr) instr).getA1();
                Value rop = ((Instr.AluInstr) instr).getA2();
                rms.add(instr);
                if (lop.equals(rop) && lop instanceof Instr) {
                    cnt = 2;
                    while ((instr.getNext() instanceof Instr.AluInstr
                            && ((Instr.AluInstr) instr.getNext()).getAluOp() == Instr.AluInstr.AluOp.ADD)) {
                        Instr.AluInstr next = (Instr.AluInstr) instr.getNext();
                        if (!(instr.getUsers().size() == 1 && instr.getUsers().get(0).equals(next))) {
                            break;
                        }
                        if (!(next.getA1().equals(instr) && next.getA2().equals(lop))) {
                            break;
                        } else {
                            cnt++;
                        }
                        rms.add(next);
                        instr = next;
                    }
                    if (cnt >= 100) {
                        for (Instr instr1 : rms) {
                            instr1.remove();
                        }
                        Instr.AluInstr aluInstr = new Instr.AluInstr(SymType.BasicType.Basic_INT, bb, Instr.AluInstr.AluOp.MUL,
                                lop, new Constant.IntConst(cnt));
                        instr.getNext().insertBefore(aluInstr);
                        instr.replaceAllUsesWith(aluInstr);
                        break;
                    }
                }
            }
        }
    }
}
