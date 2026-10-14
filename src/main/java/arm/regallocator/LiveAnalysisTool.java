package arm.regallocator;

import arm.armDS.ArmBlock;
import arm.armDS.ArmFunction;
import arm.armDS.ArmInstr;
import arm.armDS.ArmTools;
import arm.armoperands.ArmOperand;
import arm.armoperands.PhysicalReg;
import arm.armoperands.Reg;
import mir.BasicBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LiveAnalysisTool {
    private HashMap<ArmBlock, BlockLiveInfo> allBlockLiveInfo; // 存所有块的活跃度信息
    private ArmFunction armFunction; // 表示这个工具对象是要分析哪个 function的
    private ArmTools armTools;
    private HashSet<ArmBlock> visitedBlock;

    public LiveAnalysisTool(ArmFunction armFunction, ArmTools armTools) {
        this.allBlockLiveInfo = new HashMap<>();
        this.armFunction = armFunction;
        this.armTools = armTools;
        this.visitedBlock = new HashSet<>();
        analyseFunction();
    }

    public void analyseFunction() {
        // 对armFunction属性进行分析, 将分析结果存进 allBlockLiveInfo
        for (var block : armFunction.getArmBlocks()) {
            allBlockLiveInfo.put(block, new BlockLiveInfo(block, armFunction));
        }
        armTools.getArmToIrFuncMap().get(armFunction);
        boolean isDiff = true;
        while (isDiff) { // 有变化就一直循环
            isDiff = false;
            for (ArmBlock armBlock : armFunction.getArmBlocks()) {
                BlockLiveInfo info = allBlockLiveInfo.get(armBlock);
                BasicBlock b = armTools.getArmToBasicBlockMap().get(armBlock);
                HashSet<ArmOperand> newLiveOut = new HashSet<>(); // TODO 这里考虑ret返回用r0传参, 标记为出口活跃吗
                if (armBlock.isRetValBlock()) {
                    newLiveOut.add(new PhysicalReg(0));
                }
//                if (armBlock.isRetFloatValBlock()) {
//                    newLiveOut.add(new FloatPhysicalReg(0));
//                }

                for (BasicBlock suc : b.getSuccessors()) {
                    ArmBlock armBlock1 = armTools.getBasicToArmBlockMap().get(suc);
                    newLiveOut.addAll(allBlockLiveInfo.get(armBlock1).getLiveIn());
                }
                if (!newLiveOut.equals(info.liveOut)) {
                    isDiff = true;
                    info.liveIn = new HashSet<>(info.liveUse);
                    info.liveOut = newLiveOut;
                    for (ArmOperand op : info.liveOut) {
                        if (!info.liveDef.contains(op)) {
                            info.liveIn.add(op);
                        }
                    }
                }
            }
        }
    }

    public HashMap<ArmBlock, BlockLiveInfo> getAllBlockLiveInfo() {
        return allBlockLiveInfo;
    }

//    public void analyseLiveIn(ArmBlock armBlock) {
//        // liveIn = liveUse + (liveOut - liveDef)
//        BlockLiveInfo thisInfo = allBlockLiveInfo.get(armBlock);
//        if (thisInfo.liveIn.size() > 0 || this.visitedBlock.contains(armBlock)) {
//            return;
//        }
//        analyseLiveOut(armBlock);
//        HashSet<ArmOperand> temp = new HashSet<>(thisInfo.liveOut);
//        temp.removeAll(thisInfo.liveDef);
//        temp.addAll(thisInfo.liveUse);
//        thisInfo.liveIn.addAll(temp);
//        this.visitedBlock.add(armBlock);
//    }
//
//    public void analyseLiveOut(ArmBlock armBlock) {
//        BlockLiveInfo thisInfo = allBlockLiveInfo.get(armBlock);
//        BasicBlock basicBlock = armTools.getArmToBasicBlockMap().get(armBlock);
//        if (thisInfo.liveOut.size() > 0 || basicBlock.getSuccessors().size() == 0 ||
//                this.visitedBlock.contains(armBlock)) {
//            return;
//        }
//        for (BasicBlock block : basicBlock.getSuccessors()) {
//            // 遍历这个块的所有后继块
//            analyseLiveIn(armTools.getBasicToArmBlockMap().get(block));
//            thisInfo.liveOut.addAll(allBlockLiveInfo.get(armTools.getBasicToArmBlockMap().get(block)).liveIn);
//        }
//        this.visitedBlock.add(armBlock);
//    }


    // 此类用于进行活跃度分析
    public class BlockLiveInfo {
        // 块活跃度信息
        private ArmFunction armFunction;
        private ArmBlock armBlock;
        private HashSet<ArmOperand> liveUse; // 假定块开始前赋值, 这个赋的值在块内能用得上的 变量集合
        private HashSet<ArmOperand> liveDef; // 在块内某处会被赋值的变量的 变量集合
        private HashSet<ArmOperand> liveIn; // 入口活跃变量 集合
        private HashSet<ArmOperand> liveOut; // 出口活跃变量 集合

        public BlockLiveInfo(ArmBlock armBlock, ArmFunction armFunction) { // 要分析的块儿
            this.liveUse = new HashSet<>();
            this.liveDef = new HashSet<>();
            this.liveIn = new HashSet<>();
            this.liveOut = new HashSet<>();
            this.armBlock = armBlock;
            this.armFunction = armFunction;
            analyseBLock();
        }

        public HashSet<ArmOperand> getLiveUse() {
            return liveUse;
        }

        public HashSet<ArmOperand> getLiveDef() {
            return liveDef;
        }

        public HashSet<ArmOperand> getLiveIn() {
            return liveIn;
        }

        public HashSet<ArmOperand> getLiveOut() {
            return liveOut;
        }

        public void analyseBLock() { // 分析属性基本块
            // 首先分析每个块的liveUse, liveDef
            analyseUseDef();
        }

        public void analyseUseDef() { // 建立在所有指令的 def use寄存器的基础上
            for (ArmInstr instr : armBlock.getArmInstructions()) {
                ArrayList<Reg> defRegs = instr.getDefRegs();
                ArrayList<Reg> useRegs = instr.getUseRegs();
                for (Reg use : useRegs) {
                    if (use.needColor() && !liveDef.contains(use)) {
                        liveUse.add(use);
                    }
                }
                for (Reg def : defRegs) { // 第一轮着色了一个虚拟寄存器, 第二轮又活跃 ??
                    if (def.needColor() && !liveUse.contains(def)) {
                        liveDef.add(def);
                    }
                }
            }
            liveIn.addAll(liveUse);
        }
    }
}
