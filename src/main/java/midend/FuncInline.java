package midend;

import midend.analysis.CFGBuilder;
import mir.*;

import java.util.ArrayList;

// 务必保证内联放在循环分析之前，否则的化还得维护Loop数据结构

/**
 * 需滞后于副作用分析！！！！！！！！！！！
 * requirements:
 * 1.经过了mem2reg过程
 * 2.函数形参无alloc，load，store
 * 3.维护def-use关系
 * 4.维护函数调用关系
 * ans:
 * 保证经此优化def-use关系不变，内联过的函数被删除
 */

public class FuncInline {
    private MyModule module;
    private boolean changed;

    public FuncInline(MyModule module) {
        this.module = module;
    }

    public void run() throws CloneNotSupportedException {
        analyseCall();
        CFGBuilder.analyseCFG(module);
        inline();
    }

    public void analyseCall() {
        for (Func f : module.getFuncs()) {
            if (!f.isExternal()) {
                for (BasicBlock bb : f.getBasicBlocks()) {
                    for (Instr instr : bb.getInstrs()) {
                        if (instr instanceof Instr.CallInstr) {
                            if (!((Instr.CallInstr) instr).getFunc().isExternal()) {
                                f.addCall(((Instr.CallInstr) instr).getFunc());
                                ((Instr.CallInstr) instr).getFunc().addCaller(f);
                            }
                        }
                    }
                }
            }
        }
    }


    //认为调用关系与被调用关系只考虑自定义函数之间的关系
    public void inline() throws CloneNotSupportedException {
        changed = true;
        while (changed) {
            changed = false;
            ArrayList<Func> toBeInlined = new ArrayList<>();
            for (Func func : module.getFuncs()) {
                if (func.isExternal() || func.getName().equals("main")) {
                    continue;
                }
                if (func.getCallList().isEmpty()) {
                    toBeInlined.add(func);
                } //未调用自定义函数
            }
            for (Func func : toBeInlined) {
                inlineSingleFunc(func);
                for (Func f : func.getCallerList()) {
                    f.getCallList().remove(func);
                } //维护调用关系
            }
            module.getFuncs().removeAll(toBeInlined);
            //被内联以后就没有存在的必要了
        }
    }

    /**
     * 找到所有call f,将此指令替换
     */
    public void inlineSingleFunc(Func f) throws CloneNotSupportedException {
        changed = true;
        ArrayList<Instr> calls = new ArrayList<>();
        for (Func func : f.getCallerList()) {
            for (BasicBlock bb : func.getBasicBlocks()) {
                for (Instr instr : bb.getInstrs()) {
                    if (instr instanceof Instr.CallInstr) {
                        Instr.CallInstr callInstr = (Instr.CallInstr) instr;
                        if (callInstr.getFunc().equals(f)) {
                            calls.add(instr);
                        }
                    }
                }
            }
        }
        for (Instr instr : calls) {
            replaceCallWithFunc(instr);
        }
    }

    /**
     * procedure：
     * 1.将call指令所在块按call之前的和之后分为两半
     * 2.前一半后插br指令跳转至func的第一个块
     * 3.func中的所有ret指令，替换为br 后一半
     * 4.将形参直接替换为实参
     * 5.若有phi指令用到原来call所在块，将其改为后一半
     * 6.后插phi指令，用于处理多个ret 返回值的情况
     * 7.维护def-use关系，维护函数调用关系
     */

    public void replaceCallWithFunc(Instr instr) throws CloneNotSupportedException {
        assert instr instanceof Instr.CallInstr;
        Instr.CallInstr callInstr = (Instr.CallInstr) instr;
        BasicBlock oriBb = callInstr.getBelongBlock();
        Func calleeFunc = callInstr.getFunc();
        Func copyFunc = FunctionCloner.cloneFunc(calleeFunc);
        Func oriFunc = callInstr.getBelongBlock().getBelongFunc();
        BasicBlock nextBlock = new BasicBlock(oriFunc);
        oriFunc.getBbs().remove(nextBlock);
        oriFunc.getBbs().insertAfter(nextBlock, oriBb); //将新块插入旧块之后，方便看

        boolean canChange = false;
        /**
         * not sure when iterating
         * 这里就是把指令拆到两个块中
         */
        ArrayList<Instr> tobeRemoved = new ArrayList<>();
        for (Instr tmp : oriBb.getInstrs()) {
            if (!canChange && tmp.equals(callInstr)) {
                canChange = true;
                continue;
            }
            if (canChange) {
                tobeRemoved.add(tmp);
                tmp.setBelongBlock(nextBlock);
            }
        }
        for (Instr instr1 : tobeRemoved) {
            instr1.remove();
            nextBlock.insertAtTail(instr1);
        }
        // 现在按 call 指令将原来的basic block分成了两块


/**
 * 维护phi指令
 */
//TODO 修改PHi数据结构了
        for (BasicBlock successor : oriBb.getSuccessors()) {
            for (Instr tmp : successor.getInstrs()) {
                if (tmp instanceof Instr.PhiInstr &&
                        ((Instr.PhiInstr) tmp).getOptionBlocks().contains(oriBb)) {
                    ((Instr.PhiInstr) tmp).getOptionBlocks().set(((Instr.PhiInstr) tmp).getOptionBlocks().indexOf(oriBb), nextBlock);
                    nextBlock.addUser(tmp);
                    oriBb.getUsers().remove(tmp);
                }
            }
        }

/**
 * 维护前驱后继关系
 */
        for (BasicBlock bb : oriBb.getSuccessors()) {
            nextBlock.getSuccessors().add(bb);
            bb.getPredecessors().remove(oriBb);
            bb.getPredecessors().add(nextBlock);
        }
        oriBb.getSuccessors().clear();
/**
 * 插入 br指令跳转进入函数的块
 */
        Instr.JumpInstr j = new Instr.JumpInstr(copyFunc.getBbs().getFirst(), oriBb);

/**
 * 替换形参为实参
 */
        for (int i = 0; i < copyFunc.getParams().size(); i++) {
            var realPara = callInstr.getUsedValues().get(i + 1); //实参
            var formalPara = copyFunc.getParams().get(i);
            formalPara.replaceAllUsesWith(realPara);
            //根本不需要考虑什么，直接替换就可以的！！！！！！！
//            if (realPara.getType().isFloatType() ||
//                    realPara.getType().isIntType()) {
//                formalPara.replaceAllUsesWith(realPara);
//            } else {
//                for (Instr user : formalPara.getUsers()) {
//                    if (user instanceof Instr.StoreInstr) {
//                        Instr.StoreInstr store = (Instr.StoreInstr) user;
//                        Instr.AllocaInstr alloc = (Instr.AllocaInstr) store.getUsedValues().get(1);
//                        alloc.getBelongBlock().getInstrs().remove(alloc);
//                        for (Instr user1 : alloc.getUsers()) {
//                            if (user1 instanceof Instr.LoadInstr) {
//                                user1.replaceAllUsesWith(realPara);
//                            }
//                            user1.getBelongBlock().getInstrs().remove(user1);
//                        }
//                    }
//                    if (user instanceof Instr.GepInstr) {
//                        Instr.GepInstr gep = (Instr.GepInstr) user;
//                        user.getUsedValues().set(user.getUsedValues().indexOf(formalPara), realPara);
//                    }
//                }
//            }
        }
/**
 * 将所有的ret替换为br nextblock ，并在nextblock头部插入phi，来处理返回值
 */
        ArrayList<Instr.RetInstr> retInstrs = new ArrayList<>();
        for (BasicBlock bb : copyFunc.getBasicBlocks()) {
            for (Instr instr1 : bb.getInstrs()) {
                if (instr1 instanceof Instr.RetInstr) {
                    retInstrs.add((Instr.RetInstr) instr1);
                }
            }
        }
        if (copyFunc.getRetType().isVoidType()) {
            for (Instr.RetInstr ret : retInstrs) {
                ret.getBelongBlock().getInstrs().remove(ret);
                Instr.JumpInstr jumpInstr = new Instr.JumpInstr(nextBlock, ret.getBelongBlock());
            }
        } else {
            if (retInstrs.size() == 1) {
                Instr.RetInstr ret = retInstrs.get(0);
                ret.remove();
                Instr.JumpInstr jumpInstr = new Instr.JumpInstr(nextBlock, ret.getBelongBlock());
                callInstr.replaceAllUsesWith(ret.getRetValue());
            } else {
                Instr.PhiInstr phiInstr = new Instr.PhiInstr(copyFunc.getRetType(), nextBlock);
                for (Instr.RetInstr ret : retInstrs) {
                    ret.getBelongBlock().getInstrs().remove(ret);
                    phiInstr.addUsedValue(ret.getRetValue());
                    ret.getRetValue().getUsers().add(phiInstr);
                    phiInstr.insertOption(ret.getBelongBlock());
                    Instr.JumpInstr jumpInstr = new Instr.JumpInstr(nextBlock, ret.getBelongBlock());
                }
                callInstr.replaceAllUsesWith(phiInstr);
            }
        }
        for (Instr.RetInstr ret : retInstrs) {
            for (Value v : ret.getUsedValues()) {
                v.getUsers().remove(ret);
            }
        }
        //此时copyFunc中的块仍未加入原来的oriFunc中
        ArrayList<BasicBlock> bbs = new ArrayList<>();
        for (BasicBlock bb : copyFunc.getBasicBlocks()) {
            copyFunc.getBasicBlocks().remove(bb);
            bb.setBelongFunc(oriFunc);
            bbs.add(bb);
        }
        for (BasicBlock bb : bbs) {
            oriFunc.getBbs().insertBefore(bb, nextBlock);
        }
        oriBb.getInstrs().remove(callInstr);
        for (Value v : callInstr.getUsedValues()) {
            v.getUsers().remove(callInstr);
        }
    }
}
