package midend;

import mir.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;

/**
 * 死代码删除
 * 需先经过副作用分析，判断函数有无副作用
 */
public class DeadCodeElimination {
    private MyModule module;
    private HashSet<Instr> visited = new HashSet<>();
    private HashMap<Instr, Boolean> isUseful = new HashMap<>();

    public DeadCodeElimination(MyModule module) {
        this.module = module;
    }

    public void run() {
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                isUseful.clear();
                visited.clear();
                for (BasicBlock bb : func.getBasicBlocks()) {
                    for (Instr instr : bb.getInstrs()) {
                        isUseful.put(instr, false);
                    }
                }
                removeUselessInstrsInCertainFunc(func);
            }
        }
    }

    /**
     * 最直接的影响就是内存的改变以及IO，指令流可能影响store及IO
     * store指令修改内存，显然不可删除
     * Br，Jump,Ret指令删除以后影响了执行流，可能会影响到store指令的执行
     * call指令如果调用了具有副作用的函数，也是不可删除的
     */
    public boolean isUseful(Instr instr) {
        return instr instanceof Instr.BranchInstr ||
                instr instanceof Instr.RetInstr ||
                instr instanceof Instr.JumpInstr ||
                instr instanceof Instr.StoreInstr ||
                (instr instanceof Instr.CallInstr &&
                        ((Instr.CallInstr) instr).getFunc().hasSideEffect());
    }

    /**
     * 思路：找到“有用”的指令，有用的指令用到的指令也是有用的指令
     * 如果不是有用的指令，就是可以删除的指令
     * 删除以后并不影响执行结果(副作用)
     *
     * @param func
     */
    public void removeUselessInstrsInCertainFunc(Func func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                if (isUseful(instr)) {
                    findRelatingUsefulInstrs(instr);
                }
            }
        }
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instr instr : bb.getInstrs()) {
                if (!isUseful.get(instr)) {
                    for (Value v : instr.getUsedValues()) {
                        v.getUsers().remove(instr);
                    }
                    instr.getUsedValues().clear();
                    bb.getInstrs().remove(instr);
                }
            }
        }
    }

    /**
     * 相当于找一个闭包
     *
     * @param instr
     */
    public void findRelatingUsefulInstrs(Instr instr) {
        ArrayDeque<Instr> queue = new ArrayDeque<>();
        queue.add(instr);
        visited.add(instr);
        while (!queue.isEmpty()) {
            Instr i = queue.pollFirst();
            isUseful.put(i, true);
            for (Value v : i.getUsedValues()) {
                if (v instanceof Instr && !visited.contains((Instr) v) && !isUseful((Instr) v)) {
                    visited.add((Instr) v);
                    queue.add((Instr) v);
                }
            }
        }
    }
}
