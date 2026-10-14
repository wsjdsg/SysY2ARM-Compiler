package midend;

import mir.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * GVN不可单独优化，单独优化是错误的
 * 在此步进行了激进的替换操作
 * 需要在GVN之后开启GCM优化
 */
public class GVN {
    private MyModule module;
    private HashMap<Integer, ArrayList<Instr>> T = new HashMap<>();

    public GVN(MyModule module) {
        this.module = module;
    }

    /**
     * 首先对基本块进行一个拓扑排序
     * 按拓扑顺序进行优化
     * 如果一个指令的结果是一个常数，这个指令不必保留，只需要将所有的use都直接替换为常数即可
     * 什么样的东西是不可以被化简到最终（常数）的？我称之为不可知
     * 1.函数体中的形参涉及的运算
     * 2.alloc分配出来的地址
     * 3.全局变量
     * 4.load出来的东西
     * 5.GEP
     * 用到不可知指令的instr是不可知的
     * 但是如果特殊的，操作数一样且运算符号一样，那就是一样的，可以替换
     */

    public void runGVN() throws Exception {
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                runGVN2Func(func);
            }
        }
    }

    //TODO phi指令的取舍问题

    /**
     * 这里是否对每个基本块首先消除不必要的phi持保留意见，暂时先跳过phi
     *
     * @param func
     */
    public void runGVN2Func(Func func) throws Exception {
        T.clear();
        ArrayList<BasicBlock> bbs = getBBsInOrd(func);
        for (BasicBlock bb : bbs) {
            //这里没有对phi指令进行简化
            //TODO 看情况简化下吧
            for (Instr instr : bb.getInstrs()) {
                //指令有使用者，才会对其进行查表操作，否则就不动了
                if (instr.getUsers().size() != 0) {
                    //企图化简为常数
                    Value consFoldSimplified = InstrToConstant.simplify(instr);
                    if (!consFoldSimplified.equals(instr)) {
                        instr.remove();
                        for (Value v : instr.getUsedValues()) {
                            v.getUsers().remove(instr);
                        }
                        instr.getUsedValues().clear();
                        instr.replaceAllUsesWith(consFoldSimplified);
                        continue;
                    }
                    //化简失败，仍为原指令
                    Value found = find(instr);
                    if (!found.equals(instr)) {
                        bb.getInstrs().remove(instr);
                        for (Value v : instr.getUsedValues()) {
                            v.getUsers().remove(instr);
                        }
                        instr.getUsedValues().clear();
                        instr.replaceAllUsesWith(found);
                    }
                }
            }
        }
    }

    public Value find(Instr v) throws Exception {
        Integer hashCode = HashCalculator.getHash(v);
        if (T.containsKey(hashCode)) {
            ArrayList<Instr> arr = T.get(hashCode);
            Value ans = findInArr(v, arr);
            if (ans == null) {
                arr.add(v);
                return v;
            } else {
                return ans;
            }
        } else {
            ArrayList<Instr> tmp = new ArrayList<>();
            tmp.add(v);
            T.put(hashCode, tmp);
            return v;
        }
    }

    public Instr findInArr(Instr v, ArrayList<Instr> arr) {
        for (int i = 0; i < arr.size(); i++) {
            Instr tmp = arr.get(i);
            if (v.getUsedValues().size() == tmp.getUsedValues().size()
                    && v.getClass() == tmp.getClass()) {
                if (v instanceof Instr.AluInstr) {
                    Instr.AluInstr alu = (Instr.AluInstr) v;
                    Instr.AluInstr aluTmp = (Instr.AluInstr) tmp;
                    if (alu.getAluOp().equals(aluTmp.getAluOp())) {
                        switch (alu.getAluOp()) {
                            case ADD:
                            case MUL:
                            case FADD:
                            case FMUL:
                                if ((alu.getA1().equals(aluTmp.getA1()) && alu.getA2().equals(aluTmp.getA2())) ||
                                        alu.getA1().equals(aluTmp.getA2()) && alu.getA2().equals(aluTmp.getA1())) {
                                    return aluTmp;
                                } else {
                                    return null;
                                }
                            default:
                                if (alu.getA1().equals(aluTmp.getA1()) && alu.getA2().equals(aluTmp.getA2())) {
                                    return aluTmp;
                                } else {
                                    return null;
                                }
                        }
                    }
                } else if (v instanceof Instr.IcmpInstr) {
                    Instr.IcmpInstr icmp1 = (Instr.IcmpInstr) v;
                    Instr.IcmpInstr icmp2 = (Instr.IcmpInstr) tmp;
                    if (icmp1.getIcmpOp().equals(icmp2.getIcmpOp())) {
                        if (icmp1.getA1().equals(icmp2.getA1()) && icmp1.getA2().equals(icmp2.getA2())) {
                            return icmp2;
                        }
                    }
                    return null;
                } else if (v instanceof Instr.FcmpInstr) {
                    Instr.FcmpInstr icmp1 = (Instr.FcmpInstr) v;
                    Instr.FcmpInstr icmp2 = (Instr.FcmpInstr) tmp;
                    if (icmp1.getFcmpOp().equals(icmp2.getFcmpOp())) {
                        if (icmp1.getA1().equals(icmp2.getA1()) && icmp1.getA2().equals(icmp2.getA2())) {
                            return icmp2;
                        }
                    }
                    return null;
                } else if (v instanceof Instr.ZextInstr) {
                    Instr.ZextInstr zv = (Instr.ZextInstr) v;
                    Instr.ZextInstr ztmp = (Instr.ZextInstr) tmp;
                    if (zv.getType().equals(ztmp.getType()) && zv.getA1().equals(ztmp.getA1())) {
                        return ztmp;
                    }
                    return null;
                } else if (v instanceof Instr.FptosiInstr) {
                    Instr.FptosiInstr fpv = (Instr.FptosiInstr) v;
                    Instr.FptosiInstr fptmp = (Instr.FptosiInstr) tmp;
                    if (fpv.getA1().equals(fptmp.getA1())) {
                        return fptmp;
                    }
                    return null;
                } else if (v instanceof Instr.SitofpInstr) {
                    Instr.SitofpInstr fpv = (Instr.SitofpInstr) v;
                    Instr.SitofpInstr fptmp = (Instr.SitofpInstr) tmp;
                    if (fpv.getA1().equals(fptmp.getA1())) {
                        return fptmp;
                    }
                    return null;
                } else if (v instanceof Instr.GepInstr) {
                    Instr.GepInstr gep1 = (Instr.GepInstr) v;
                    Instr.GepInstr gep2 = (Instr.GepInstr) tmp;
                    for (int j = 0; j < gep1.getUsedValues().size(); j++) {
                        if (!gep1.getUsedValues().get(j).equals(gep2.getUsedValues().get(j))) {
                            return null;
                        }
                    }
                    return gep2;
                } else {
                    if (v.equals(tmp)) {
                        return tmp;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    public ArrayList<BasicBlock> getBBsInOrd(Func func) {
        //这样做有个问题就是循环的问题有点棘手
        HashSet<BasicBlock> visited = new HashSet<>();
        ArrayList<BasicBlock> ans = new ArrayList<>();
        ArrayDeque<BasicBlock> queue = new ArrayDeque<>();
        queue.add(func.getBasicBlocks().getFirst());
        visited.add(func.getBasicBlocks().getFirst());
        while (queue.size() != 0) {
            BasicBlock bb = queue.pollFirst();
            ans.add(bb);
            for (BasicBlock block : bb.getSuccessors()) {
                if (!visited.contains(block)) {
                    visited.add(block);
                    queue.add(block);
                }
            }
        }
        return ans;
    }
}
