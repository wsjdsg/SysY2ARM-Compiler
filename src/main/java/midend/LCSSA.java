package midend;

import midend.analysis.DomInfo;
import midend.analysis.Loop;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public class LCSSA {

    ArrayList<Func> funcs = new ArrayList<>();

    public LCSSA(MyModule module) {
        for (Func func : module.getFuncs()) {
            funcs.add(func);
        }
    }

    public void runLCSSA(MyModule module) {
        DomInfo domInfo = new DomInfo();
        domInfo.calculate(module);

        for (Func func : funcs) {
            addPhiForFunc(func);
        }
        DeadCodeElimination deadCodeElimination = new DeadCodeElimination(module);
        deadCodeElimination.run();
        SimpleDCE simpleDCE = new SimpleDCE(module);
        simpleDCE.run();
    }

    public void addPhiForFunc(Func func) {
        for (Loop loop : func.getAllLoops()) {
            addPhiForLoop(loop);
        }
    }

    public void addPhiForLoop(Loop loop) {
        HashSet<BasicBlock> hasExiteds = new HashSet<>();

        for (BasicBlock exit : loop.getExits()) {
            for (BasicBlock mayHasExited : exit.getSuccessors()) {
                if (mayHasExited.getParentLoop() == null || !mayHasExited.getParentLoop().equals(loop)) {
                    hasExiteds.add(mayHasExited);
                }
            }
        }

        for (BasicBlock block : loop.getAllBlocks()) {
            if (block.getParentLoop() != null && block.getParentLoop().equals(loop)) {
                for (Instr instr : block.getInstrs()) {
                    if (ifHasUserOutside(loop, instr)) {
                        for (BasicBlock hasExited : hasExiteds) {
                            addPhiAtHasExited(instr, hasExited, loop);
                        }
                    }
                }
            }
        }

    }

    public void addPhiAtHasExited(Value value, BasicBlock hasExited, Loop loop) {
        ArrayList<Value> options = new ArrayList<>();

        for (BasicBlock block : hasExited.getPredecessors()) {
            options.add(value);
        }

        Instr.PhiInstr newPhi = new Instr.PhiInstr(value.getType(), options, hasExited);

        for (BasicBlock block : hasExited.getPredecessors()) {
            newPhi.insertOption(block);
        }

        HashSet<Instr> defInstrs = new HashSet<>();
        addDefsForInstr(defInstrs, newPhi);
        addDefsForInstr(defInstrs, (Instr) value);

        HashMap<Instr, Integer> userToIdx = new HashMap<>();

        for (Instr user : value.getUsers()) {
            BasicBlock block = user.belongBlock;
            if (user instanceof Instr.PhiInstr) {
                if (block.getParentLoop() != null && block.getParentLoop().equals(loop)) {
                    continue;
                }

                int idx = ((Instr.PhiInstr) user).getOptions().indexOf(value);
                block = ((Instr.PhiInstr) user).getOptionBlocks().get(idx);
            }

            if (block.getParentLoop() == null || !block.getParentLoop().equals(loop)) {
                userToIdx.put(user, user.getUsedValues().indexOf(value));
            }
        }

        userToIdx.put(newPhi, -1);
        Stack<Value> stack = new Stack<>();
        userRelateModify(stack, hasExited.getBelongFunc().getBasicBlocks().getFirst(),
                userToIdx, defInstrs);
    }

    public void userRelateModify(Stack<Value> stack, BasicBlock block,
                                 HashMap<Instr, Integer> userToIdx, HashSet<Instr> defInstrs) {
        int cnt = 0;
        for (Instr instr : block.getInstrs()) {
            if (!(instr instanceof Instr.PhiInstr) && userToIdx.containsKey(instr)) {
                instr.modifyUse(getPeek(stack), userToIdx.get(instr));
            }

            if (defInstrs.contains(instr)) {
                stack.push(instr);
                cnt++;
            }
        }

        for (BasicBlock sucBlock : block.getSuccessors()) {
            for (Instr instr : sucBlock.getInstrs()) {
                if (!(instr instanceof Instr.PhiInstr)) {
                    break;
                }

                if (userToIdx.containsKey(instr)) {
                    if (userToIdx.get(instr) == -1) {
                        instr.modifyUse(getPeek(stack), sucBlock.getPredecessors().indexOf(block));
                    } else if (((Instr.PhiInstr) instr).getOptionBlocks().indexOf(block) == userToIdx.get(instr)) {
                        instr.modifyUse(getPeek(stack), userToIdx.get(instr));
                    }
                }
            }
        }

        ArrayList<BasicBlock> beDomed = new ArrayList<>();
        Func func = block.getBelongFunc();

        for (BasicBlock block1 : func.getBasicBlocks()) {
            if (!block1.equals(block) && block1.getDominator() != null
                    && block1.getDominator().equals(block)) {
                beDomed.add(block1);
            }
        }

        for (BasicBlock block1 : beDomed) {
            userRelateModify(stack, block1, userToIdx, defInstrs);
        }

        for (int i = 0; i < cnt; i++) {
            stack.pop();
        }
    }

    public Value getPeek(Stack<Value> stack) {
        if (stack.isEmpty()) {
            return new GlobalValue.UndefGlobalVal();
        }
        return stack.peek();
    }

    public void addDefsForInstr(HashSet<Instr> defInstr, Instr instr) {
        if (defInstr.contains(instr)) {
            return;
        }

        defInstr.add(instr);
        if (instr instanceof Instr.PhiInstr) {
            for (Value value : instr.getUsedValues()) {
                if (value instanceof Instr) {
                    defInstr.add((Instr) value);
                }
            }
        }
    }

    public boolean ifHasUserOutside(Loop loop, Instr instr) {
        for (Instr user : instr.getUsers()) {
            BasicBlock block = user.belongBlock;

            if (user instanceof Instr.PhiInstr) {
                block = ((Instr.PhiInstr) user).getOptionBlocks().get(((Instr.PhiInstr) user).getOptions().indexOf(instr));
            }

            if (block.getParentLoop() == null ||
                    !block.equals(loop)) {
                return true;
            }
        }
        return false;
    }

}
