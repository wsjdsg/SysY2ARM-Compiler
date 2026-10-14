package midend.analysis;

import mir.*;

import java.util.ArrayList;

/**
 * 从此中得到函数的相互调用关系以及hasSideEffect私有属性的值
 * 需要优先于函数内联与死代码删除
 * 1.没有副作用也没有返回值的函数没有用
 * 2.有返回值的没有副作用的函数所在的一切call可以认为是一个同一个Value
 */

public class SideEffectAnalysis {
    private MyModule module;

    public SideEffectAnalysis(MyModule module) {
        this.module = module;
    }

    /**
     * 得到函数调用关系并删除不会被调用的函数
     */
    public void getCallGraph() {
        for (Func func : module.getFuncs()) {
            func.getCallList().clear();
            func.getCallerList().clear();
        }
        for (Func func : module.getFuncs()) {
            for (BasicBlock bb : func.getBbs()) {
                for (Instr instr : bb.getInstrs()) {
                    if (instr instanceof Instr.CallInstr) {
                        Func calleeF = ((Instr.CallInstr) instr).getFunc();
                        if (!calleeF.getCallerList().contains(func)) {
                            calleeF.getCallerList().add(func);
                        }
                        if (!calleeF.isExternal() && !func.getCallList().contains(calleeF)) {
                            func.getCallList().add(calleeF);
                        }
                    }
                }
            }
        }
        ArrayList<Func> uselessFuncs = new ArrayList<>();
        for (Func func : module.getFuncs()) {
            if (func.getCallerList().isEmpty() && !func.getName().equals("main")) {
                uselessFuncs.add(func);
                for (Func f : func.getCallList()) {
                    f.getCallerList().remove(func);
                }
            }
        }
        module.getFuncs().removeAll(uselessFuncs);
    }

    /**
     * 调用库函数的认为是有副作用的
     * 读取全局变量或者写全局变量认为是有副作用的(做了很大的简化)
     * 如果一个函数是有副作用的，那么它的调用者函数也是有副作用的
     */

    public void runAnalysis() {
        getCallGraph();
        //得到使用全局变量的那些函数
        for (Func func : module.getFuncs()) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                for (Instr instr : bb.getInstrs()) {
                    if (instr instanceof Instr.StoreInstr) {
                        func.setHasSideEffect(true);
                        if (((Instr.StoreInstr) instr).getAddress() instanceof GlobalValue) {
                            func.setUseGv(true);
                        }
                    }
                    if (instr instanceof Instr.LoadInstr &&
                            ((Instr.LoadInstr) instr).getAddress() instanceof GlobalValue) {
                        func.setUseGv(true);
                    }
                }
            }
        }
        //读写全局变量的，使用库函数的，都是有副作用的
        for (Func func : module.getFuncs()) {
            if (func.isExternal()) {
                func.setHasSideEffect(true);
            }
        }
        for (Func f : module.getFuncs()) {
            if (f.hasSideEffect()) {
                extendSideEffect(f);
            }
        }
    }

    public void extendSideEffect(Func func) {
        func.setHasSideEffect(true);
        for (Func f : func.getCallerList()) {
            if (!f.hasSideEffect()) {
                extendSideEffect(f);
            }
        }
    }
}
