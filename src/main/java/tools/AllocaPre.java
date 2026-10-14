package tools;

import mir.BasicBlock;
import mir.Func;
import mir.Instr;
import mir.MyModule;

import java.util.HashSet;

public class AllocaPre {
    public static void allocaPre(MyModule module) {
        for (Func func : module.getFuncs()) {
            if (func.getFuncBody() != null) {
                allocaPreForFunc(func);
            }
        }
    }

    private static void allocaPreForFunc(Func func) {
        HashSet<Instr> allocaInstrs = new HashSet<>();
        for (BasicBlock block : func.getBasicBlocks()) {
            for (Instr instr : block.getInstrs()) {
                if (instr instanceof Instr.AllocaInstr) {
                    instr.remove();
                    allocaInstrs.add(instr);
                }
            }
        }


        BasicBlock firstBlock = func.getBasicBlocks().getFirst();
        BasicBlock allocaBlock = new BasicBlock(func, true);

        for (Instr instr : allocaInstrs) {
            instr.belongBlock = allocaBlock;
            allocaBlock.getInstrs().insertAtTail(instr);
        }

        Instr.JumpInstr jumpInstr = new Instr.JumpInstr(firstBlock, allocaBlock);
    }

}
