package arm.regallocator;

import arm.armDS.*;
import arm.arminstructions.*;
import arm.armoperands.*;
import tools.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class FloatRegAllocator {
    private static final int INF = 0x3f3f3f3f; // 用一个比较大的数来表示无限大
    private final int k = 32; // 寄存器总数
    private ArmTools armTools;
    private ArmModule armModule;
    private HashMap<ArmFunction, LiveAnalysisTool> liveAnalysisTools; // 每个函数都要有一个活跃度分析工具
    private HashSet<ArmVMov> workListMoves;
    private HashMap<ArmOperand, HashSet<ArmVMov>> opMoveList; // 操作数存在着的移位指令
    private HashSet<Pair<ArmOperand, ArmOperand>> adjSet; // 存干扰边
    private HashMap<ArmOperand, HashSet<ArmOperand>> adjList; // 存各个节点的相邻节点
    private HashMap<ArmOperand, Integer> degree; // 存所有节点的度数
    private HashMap<ArmOperand, Integer> loopDepthMap;
    private HashSet<ArmOperand> initial; // 临时寄存器集合
    private HashSet<ArmOperand> spillWorkList;
    private HashSet<ArmOperand> freezeWorkList;
    private HashSet<ArmOperand> simplifyWorkList;
    private HashSet<ArmVMov> constrainedMoves;
    private HashMap<ArmOperand, ArmOperand> alias;
    private Stack<ArmOperand> selectStack; // 选择栈
    private HashSet<ArmVMov> coalescedMoves;
    private HashSet<ArmVMov> activeMoves;
    private HashSet<ArmVMov> frozenMoves;
    private HashSet<ArmOperand> coloredNodes;
    private HashSet<ArmOperand> spilledNodes;
    private HashSet<ArmOperand> coalescedNodes;
    private HashMap<VirtualReg, VirtualReg> newToOldVirMap;
    private HashMap<VirtualReg, Integer> newLiveLength; // 这两个数据结构是针对一个个函数而说的


    public FloatRegAllocator(ArmTools armTools) {
        // 对结构进行初始化, 各个容器
        this.armTools = armTools;
        this.liveAnalysisTools = new HashMap<>();
        this.armModule = armTools.getArmModule();
        regAllocate();
    }

    public void init(ArmFunction armFunction) {
        initial = armFunction.getVirMap().values().stream().filter(  // 某个函数的 浮点非全局 虚拟寄存器集合
                virtualReg -> !virtualReg.isGlobalVar() && virtualReg.isFloat()
        ).collect(Collectors.toCollection(HashSet::new)); // 函数内的所有虚拟寄存器
        this.workListMoves = new HashSet<>(); // 有可能合并的传送指令集合
        this.loopDepthMap = new HashMap<>();
        this.opMoveList = new HashMap<>();
        this.adjSet = new HashSet<>();
        this.adjList = new HashMap<>();
        this.degree = new HashMap<>();
        this.spillWorkList = new HashSet<>();
        this.freezeWorkList = new HashSet<>(); // 不再考虑合并的传送指令集合
        this.simplifyWorkList = new HashSet<>();
        this.selectStack = new Stack<>();
        this.coalescedMoves = new HashSet<>();
        this.activeMoves = new HashSet<>(); // 还没做好合并准备的传送指令集合
        this.constrainedMoves = new HashSet<>(); // 源操作数和目标操作数冲突的mov集合
        this.alias = new HashMap<>();
        this.frozenMoves = new HashSet<>();
        this.coloredNodes = new HashSet<>();
        this.spilledNodes = new HashSet<>();
        this.coalescedNodes = new HashSet<>();
    }

    public void regAllocate() {
        // 对 armModule进行寄存器分配
        // ArrayList<ArmFunction> l = armModule.getArmFunctions();
        for (var armFunction : armModule.getArmFunctions()) { // 遍历所有的函数
            // live analysis: 拿到所有集合, 每个集合内的元素有冲突

            newToOldVirMap = new HashMap<>();
            newLiveLength = new HashMap<>();
            boolean isOver = false;
            while (!isOver) {
                functionLiveAnalysis(armFunction); // 对一个函数进行 TODO 为啥要放在里面这句话, 函数是动态变化的？？
                init(armFunction);
                // System.out.println("1111111111111");
                // build interface graph: 遍历上述得到的所有集合, 将每个集合内的所有点两两相连
                buildGraph(armFunction);
                makeWorkList(); // 创建工作列表
                while (true) { // 工作列表非空的时候就一直循环
                    if (!simplifyWorkList.isEmpty()) {
                        simplify();
                    } else if (!workListMoves.isEmpty()) {
                        coalesce(); // 合并
                    } else if (!freezeWorkList.isEmpty()) {
                        freeze();
                    } else if (!spillWorkList.isEmpty()) {
                        selectSpill();
                    } else {
                        break;
                    }
                }
                assignColors(armFunction);
                if (!spilledNodes.isEmpty()) {
                    rewriteProgram(armFunction);
                } else { // 没有溢出则结束
                    isOver = true;
                }
            }
        }

        // 把所有物理寄存器的分配状态设置为false
        for (ArmFunction armFunction : armModule.getArmFunctions()) { // 遍历函数
            for (ArmBlock armBlock : armFunction.getArmBlocks()) { // 遍历基本块
                for (ArmInstr instr : armBlock.getArmInstructions()) { // 遍历指令
                    // 拿到指令的defs uses, 挑出其中的常规物理寄存器

                    ArrayList<Reg> temp = instr.getDefRegs();
                    for (Reg op : temp) {
                        if (op instanceof FloatPhysicalReg) {
                            op.setAllocated(false);
                        }
                    }
                    temp = instr.getUseRegs();
                    for (Reg op : temp) {
                        if (op instanceof FloatPhysicalReg) {
                            op.setAllocated(false);
                        }
                    }
                }
            }
        }
    }

    // 整数的replace涉及整数, 浮点的涉及浮点
    public void replaceReg(ArmInstr instr, ArmOperand src, ArmOperand toReg) {
        // TODO equals方法的重写 ?????
        // 浮点寄存器不用shift
        if (instr instanceof ArmFloatBinary) {
            if (((ArmFloatBinary) instr).getDst().equals(src)) {
                if (toReg.isFloat() != ((ArmFloatBinary) instr).getDst().isFloat()) {
                    return;
                }
                ((ArmFloatBinary) instr).setDst(toReg);
            }
            if (((ArmFloatBinary) instr).getLhs().equals(src)) {
                if (toReg.isFloat() != ((ArmFloatBinary) instr).getLhs().isFloat()) {
                    return;
                }
                ((ArmFloatBinary) instr).setLhs(toReg);
            }
            if (((ArmFloatBinary) instr).getRhs().equals(src)) {
                if (toReg.isFloat() != ((ArmFloatBinary) instr).getRhs().isFloat()) {
                    return;
                }
                ((ArmFloatBinary) instr).setRhs(toReg);
            }
        } else if (instr instanceof ArmVCompare) {
            if (((ArmVCompare) instr).getLhs().equals(src)) {
                if (toReg.isFloat() != ((ArmVCompare) instr).getLhs().isFloat()) {
                    return;
                }
                ((ArmVCompare) instr).setLhs(toReg);
            }
            if (((ArmVCompare) instr).getRhs().equals(src)) {
                if (toReg.isFloat() != ((ArmVCompare) instr).getRhs().isFloat()) {
                    return;
                }
                ((ArmVCompare) instr).setRhs(toReg);
            }
        } else if (instr instanceof ArmVLoad) {
            if (((ArmVLoad) instr).getOffset().equals(src)) {
                if (toReg.isFloat() != ((ArmVLoad) instr).getOffset().isFloat()) {
                    return;
                }
                ((ArmVLoad) instr).setOffset(toReg);
            }
            if (((ArmVLoad) instr).getAddr().equals(src)) {
                if (toReg.isFloat() != ((ArmVLoad) instr).getAddr().isFloat()) {
                    return;
                }
                ((ArmVLoad) instr).setAddr(toReg);
            }
            if (((ArmVLoad) instr).getDst().equals(src)) {
                if (toReg.isFloat() != ((ArmVLoad) instr).getDst().isFloat()) {
                    return;
                }
                ((ArmVLoad) instr).setDst(toReg);
            }
        } else if (instr instanceof ArmVMov) {
            if (((ArmVMov) instr).getDst().equals(src)) {
                if (toReg.isFloat() != ((ArmVMov) instr).getDst().isFloat()) {
                    return;
                }
                ((ArmVMov) instr).setDst(toReg);
            }
            if (((ArmVMov) instr).getRhs().equals(src)) {
                if (toReg.isFloat() != ((ArmVMov) instr).getRhs().isFloat()) {
                    return;
                }
                ((ArmVMov) instr).setRhs(toReg);
            }
        } else if (instr instanceof ArmVStore) {
            if (((ArmVStore) instr).getOffset().equals(src)) {
                if (toReg.isFloat() != ((ArmVStore) instr).getOffset().isFloat()) {
                    return;
                }
                ((ArmVStore) instr).setOffset(toReg);
            }
            if (((ArmVStore) instr).getAddr().equals(src)) {
                if (toReg.isFloat() != ((ArmVStore) instr).getAddr().isFloat()) {
                    return;
                }
                ((ArmVStore) instr).setAddr(toReg);
            }
            if (((ArmVStore) instr).getSrcData().equals(src)) {
                if (toReg.isFloat() != ((ArmVStore) instr).getSrcData().isFloat()) {
                    return;
                }
                ((ArmVStore) instr).setSrcData(toReg);
            }
        } else if (instr instanceof ArmVConvert) {
            if (((ArmVConvert) instr).getDst().equals(src)) {
                if (toReg.isFloat() != ((ArmVConvert) instr).getDst().isFloat()) {
                    return;
                }
                ((ArmVConvert) instr).setDst(toReg);
            }
            if (((ArmVConvert) instr).getRhs().equals(src)) {
                if (toReg.isFloat() != ((ArmVConvert) instr).getRhs().isFloat()) {
                    return;
                }
                ((ArmVConvert) instr).setRhs(toReg);
            }
        } else {
            return;
//            System.out.println("why is not float instr ??");
        }
    }

    public VirtualReg cloneReg(ArmFunction armFunction, VirtualReg virtualReg) {
        VirtualReg oldReg = virtualReg;
        VirtualReg newReg = new VirtualReg(armFunction, true);
        if (!virtualReg.isGlobalVar() && virtualReg.isFloatVirtual()) {
            // 不是全局, 是浮点
            while (newToOldVirMap.containsKey(oldReg)) {// 6 5 4 3 2 1
                oldReg = newToOldVirMap.get(oldReg); // 找到 oldReg的尽头
            }
            newToOldVirMap.put(newReg, oldReg);
//            newReg.setDefInstr(oldReg.getDefInstr()); // TODO  维护虚拟寄存器 的defInstr属性
        }
        return newReg;
    }

    public void rewriteProgram(ArmFunction armFunction) {
        // 处理 溢出的node
        for (ArmOperand n : spilledNodes) {
            boolean storeInStack = true;
            for (ArmBlock armBlock : armFunction.getArmBlocks()) {
                // 遍历所有block
                int stackSize = armFunction.getStackSize();
                // new 一个info对象记录一些信息
                Info info = new Info();
                int instrCount = 0;
                for (ArmInstr instr : armBlock.getArmInstructions()) {
                    // 取出defRegs useRegs
                    HashSet<Reg> defRegs = new HashSet<>(instr.getDefRegs());
                    HashSet<Reg> useRegs = new HashSet<>(instr.getUseRegs());
                    for (Reg defReg : defRegs) {
                        if (defReg.equals(n)) {
                            if (info.vReg == null) { // 循环第一次走到这里
                                info.vReg = cloneReg(armFunction, (VirtualReg) n);
                            }
                            replaceReg(instr, defReg, info.vReg);
                            info.lastDef = instr;
                        }
                    }
                    for (Reg useReg : useRegs) {
                        if (useReg.equals(n)) {
                            if (info.vReg == null) {
                                info.vReg = cloneReg(armFunction, (VirtualReg) n);
                            }
                            replaceReg(instr, useReg, info.vReg);
                            if (info.firstUse == null && info.lastDef == null) {
                                info.firstUse = instr;
                            }
                        }
                    }
                    if (instrCount > 30) {
                        checkPoint(armFunction, armBlock, storeInStack, info, stackSize, n);
                    }
                    instrCount++;
                }
                checkPoint(armFunction, armBlock, storeInStack, info, stackSize, n);
            }
            if (storeInStack) {
                armFunction.addStackSize(4);
            }
        }
    }

    public void fixOffset(ArmFunction armFunction, ArmInstr instr, int offset) {
        // 有些指令的立即数偏移值不合范围, 用这个函数来修一修
        ArmOperand off = new Imme(offset);
        if (offset < 1020) { // 可以直接作为偏移
            if (instr instanceof ArmVLoad) {
                ((ArmVLoad) instr).setOffset(off);
            } else if (instr instanceof ArmVStore) {
                ((ArmVStore) instr).setOffset(off);
            }
        } else {
            // 插进去一条mov指令
            VirtualReg virtualReg = new VirtualReg(armFunction);
            ArmMov mov = new ArmMov(virtualReg, off);
            instr.insertBeforeInstr(mov);

            ArmBinary add = new ArmBinary(ArmBinary.BinaryType.add, virtualReg,
                    new PhysicalReg("sp"), virtualReg);
//            add.insertBeforeInstr(instr); // TODO 这三行？？？
            instr.insertBeforeInstr(add);
            if (instr instanceof ArmVLoad) {
                ((ArmVLoad) instr).setAddr(virtualReg);
            } else if (instr instanceof ArmVStore) {
                ((ArmVStore) instr).setAddr(virtualReg);
            }
        }
    }

    // 将新的, 处理溢出的临时变量插入到基本块中
    // 溢出-访存操作
    public void checkPoint(ArmFunction armFunction,
                           ArmBlock armBlock, boolean storeStack, Info info, int offset, ArmOperand n) {
        if (storeStack) { // 需要存储在堆栈中
            if (info.firstUse != null) { // 在第一个使用点之前插入加载
                ArmVLoad load = new ArmVLoad(info.vReg, new PhysicalReg("sp"), new Imme(0));
                // load.insertBeforeInstr(info.firstUse);
                info.firstUse.insertBeforeInstr(load);
                fixOffset(armFunction, load, offset);
            }

            if (info.lastDef != null) {
                ArmVStore store = new ArmVStore(info.vReg, new PhysicalReg("sp"), new Imme(0));
//                store.insertAfterInstr(info.lastDef);
                info.lastDef.insertAfterInstr(store);
                fixOffset(armFunction, store, offset);
            }
        } else { // 不需要存储在堆栈中
            ArmInstr defInstr = ((VirtualReg) n).getDefInstr(); // 溢出变量的def指令
            HashMap<VirtualReg, VirtualReg> regMap = new HashMap<>();
            HashSet<ArmInstr> toInsertSet = new HashSet<>();
            LinkedList<ArmInstr> toInsertList = new LinkedList<>();
            ArmInstr preInstr = info.firstUse;

            toInsertList.addLast(defInstr);
            toInsertSet.add(defInstr); // 定义指令插

            VirtualReg defReg = (VirtualReg) defInstr.getDefRegs().get(0);
            defReg = newToOldVirMap.getOrDefault(defReg, defReg);
            regMap.put(defReg, info.vReg);

            while (!toInsertList.isEmpty()) { // 要插入的列表不是空的
                ArmInstr instr = toInsertList.pollFirst(); // instr是"要插入的"
                addDefiner(instr, toInsertList, toInsertSet);
                if (instr instanceof ArmBinary) {
                    // TODO 不简化的话接着这里写
                }
            }
        }

        if (info.vReg != null) {
            int firstPos = -1; // 第一个以vReg为操作数的下标
            int lastPos = -1; // 最后一个以vReg为操作数的下标
            int pos = 0; // 循环变量
            for (ArmInstr instr : armBlock.getArmInstructions()) {
                pos++;
                if (instr.getDefRegs().contains(info.vReg) ||
                        instr.getUseRegs().contains(info.vReg)) {
                    if (firstPos == -1) {
                        firstPos = pos;
                    }
                    lastPos = pos;
                }
            }
            if (firstPos != -1) { // 说明循环中有所发现, 则能求出生命长度
                newLiveLength.put(info.vReg, lastPos - firstPos + 1);
            }
        }
        info.clear();
    }

    public void addDefiner(ArmInstr instr, LinkedList<ArmInstr> list,
                           HashSet<ArmInstr> set) {
        // 分指令类型取出其中的相关操作数(Use), 然后加入到集合中
        // TODO 不管这里的VMov和VLoad问题
        if (instr instanceof ArmBinary) {
            addRegToInsert(list, set, ((ArmBinary) instr).getLhs());
            addRegToInsert(list, set, ((ArmBinary) instr).getRhs());
        } else if (instr instanceof ArmMov) { // TODO 这个函数在青春版的里面是没有用的, 所以这里先不管这个ArmVMov问题
            addRegToInsert(list, set, ((ArmMov) instr).getRhs());
        } else if (instr instanceof ArmLoad) {
            addRegToInsert(list, set, ((ArmLoad) instr).getAddr());
            addRegToInsert(list, set, ((ArmLoad) instr).getOffset());
        }
    }

    public void addRegToInsert(LinkedList<ArmInstr> list,
                               HashSet<ArmInstr> set, ArmOperand reg) {
        if (reg instanceof VirtualReg && !((VirtualReg) reg).isGlobalVar() &&
                reg.isFloat() && !set.contains(((VirtualReg) reg).getDefInstr())) {
            list.addLast(((VirtualReg) reg).getDefInstr());
            set.add(((VirtualReg) reg).getDefInstr());
        }
    }

    public void assignColors(ArmFunction armFunction) {
        HashMap<ArmOperand, FloatPhysicalReg> colored = new HashMap<>();
        while (!selectStack.isEmpty()) {
            var n = selectStack.pop();
            TreeSet<Integer> okColors = new TreeSet<>();
//            HashSet<Integer> okColors = new HashSet<>();
            for (int i = 0; i < 32; ++i) { // TODO 为什么这里进了14个寄存器, 但是全局的k却等于13呢
                okColors.add(i);
            }
            for (ArmOperand op : adjList.getOrDefault(n, new HashSet<>())) { // 从Select中弹出的一个寄存器的邻节点中
                // 拿到邻居节点, 因为它的颜色不能和邻居节点相同嘛
                ArmOperand temp = getAlias(op);
                if (coloredNodes.contains(temp) || temp instanceof FloatPhysicalReg) { // 已经被着色过了或者是物理寄存器, 这也算被着色过了
                    // 有点预着色的味道, 根据temp从okColors中分出颜色
                    // 如果邻居是物理寄存器, 那就直接拿到id并从颜色库中删掉, 如果是虚拟的, 那就拿到这个虚拟的分配到的物理的色号再删掉
                    if (temp instanceof FloatPhysicalReg) {
                        okColors.remove(((FloatPhysicalReg) temp).getNum()); // 分出一个颜色
                    } else if (temp instanceof VirtualReg) {
                        okColors.remove(colored.get(temp).getNum());
                    }
                }
            }
            if (okColors.isEmpty()) { // 没得分了, 溢出
                spilledNodes.add(n);
            } else {
                coloredNodes.add(n); // n终于在判断之后给了他一个颜色
                int newId = okColors.iterator().next(); // 取一个新的颜色
                colored.put(n, new FloatPhysicalReg(newId)); // 记录一下这个变量被分的颜色
            }
        }
        if (!spilledNodes.isEmpty()) { // 有溢出的节点就返回了, 重新开始各种Work列表
            return;
        }

        for (ArmOperand coalNode : coalescedNodes) { // 给那些合并节点上色
            ArmOperand alias = getAlias(coalNode);
//                if (coalNode.isFloat()) { // 当前合并节点是浮点
//                    continue;
//                }
            if (alias.isPreColored()) { // 别名预着色, 那么他自己也要涂这个颜色
                colored.put(coalNode, (FloatPhysicalReg) alias);
            } else { // 别名不是预着色, 那就取出之前给这个别名着的颜色赋给 n
                colored.put(coalNode, colored.get(alias)); // TODO 保证这个别名之前已经着色过了 ???
            }
        }

        // 用分配的颜色替换定义中的操作数
        for (ArmBlock armBlock : armFunction.getArmBlocks()) {
            for (ArmInstr instr : armBlock.getArmInstructions()) {
                ArrayList<ArmOperand> defRegs = new ArrayList<>(instr.getDefRegs());
                ArrayList<ArmOperand> useRegs = new ArrayList<>(instr.getUseRegs());
                // 替换: input->目标指令, 源寄存器, 目标替换寄存器.
//                boolean test = colored.containsKey();
                defRegs.stream().filter(colored::containsKey).forEach(
                        def -> replaceReg(instr, def, colored.get(def))
                );
                useRegs.stream().filter(colored::containsKey).forEach(
                        use -> replaceReg(instr, use, colored.get(use))
                );
            }
        }
    }

    public void simplify() { // 每次从图中删除一个低度数的与传送无关的节点
        ArmOperand op = simplifyWorkList.iterator().next(); // 取出一个可以分配寄存器的op
        simplifyWorkList.remove(op);
        selectStack.push(op); // push入选择栈
        HashSet<ArmOperand> adjacent = getAdjacent(op);
        for (var a : adjacent) {
            subDegree(a); // 因为删掉了, 所以把相邻的每个点的度都--
        }
    }

    public void subDegree(ArmOperand v) {
        // 将度--
        int de = getDegree(v);
        degree.put(v, de - 1);
        // if (变成了低度数节点)
        if (de == k) {
            HashSet<ArmOperand> adjOfV = getAdjacent(v); // v的相邻节点
            adjOfV.add(v);
            enableMoves(adjOfV); // v变成了低度数, 要给v相邻的所有节点的相关mov赋能
            spillWorkList.remove(v); // 度数小了就不会再溢出了
            if (isRelateToMov(v)) { // 减度数的这个点和mov相关
                freezeWorkList.add(v);
            } else {
                simplifyWorkList.add(v);
            }
        }
    }

    public void enableMoves(HashSet<ArmOperand> ops) { // 给这些操作数相关的mov赋能
        for (var op : ops) {
            HashSet<ArmVMov> movOfOp = nodeMoves(op);
            for (var mov : movOfOp) {
                activeMoves.remove(mov);
                workListMoves.add(mov); // 为mov赋能, 让mov准备好赋值
            }
        }
    }

    public HashSet<ArmVMov> nodeMoves(ArmOperand n) { // 返回一个node的不在两个全局集合中的有关mov指令集合
        HashSet<ArmVMov> ret = new HashSet<>(opMoveList.getOrDefault(n, new HashSet<>()));
//        HashSet<ArmVMov> ret = opMoveList.getOrDefault(n, new HashSet<>());
        // 把不在这两个列表中的mov删除掉, 留下交集
//        ret.removeIf(mov -> (!activeMoves.contains(mov) && !workListMoves.contains(mov)));
        activeMoves.forEach(ret::remove);
        workListMoves.forEach(ret::remove);
        return ret;
    }

    public HashSet<ArmOperand> getAdjacent(ArmOperand op) { // 排除了相邻的中的一些特殊的
        HashSet<ArmOperand> ret = new HashSet<>(
                adjList.getOrDefault(op, new HashSet<>())); // 本来存有取出来, 否则给一个空的HashSet
        ret.removeAll(selectStack); // 排除已经分配的
        ret.removeAll(coalescedNodes); // TODO 这两句排除的作用 ??? 简化的时候先不管带传送的指令可能是
        return ret;
    }

    // 通过度数等判断来检查合并安全性, 然后进行相应的合并节点操作, 维护相应的数据结构
    public void coalesce() { // 合并啦 !  工作列表不空, 就是有可以准备合并的mov, 就执行合并
        ArmVMov mov = workListMoves.iterator().next(); // 取出来一个mov
        ArmOperand dstAlias = getAlias(mov.getDst());
        ArmOperand rhsAlias = getAlias(mov.getRhs());
        ArmOperand u = dstAlias;
        ArmOperand v = rhsAlias;
        if (rhsAlias.isPreColored()) { // rhsAlias是物理寄存器, 要交换顺序
            u = rhsAlias;
            v = dstAlias;
        }
        workListMoves.remove(mov);
        if (u.equals(v)) {
            coalescedMoves.add(mov); // 合并的节点
            addSimplifyWorkList(u);
        } else if (v.isPreColored() || adjSet.contains(new Pair<>(u, v))) { // v预着色或者图中包含了这个边
            // 冲突的mov指令
            constrainedMoves.add(mov);
            addSimplifyWorkList(u);
            addSimplifyWorkList(v);
        } else if ((u.isPreColored() && adjOk(v, u)) ||
                (!u.isPreColored() && conservative(getAdjacent(u), getAdjacent(v)))) {
            constrainedMoves.add(mov);
            combine(u, v);
            addSimplifyWorkList(u);
        } else {
            activeMoves.add(mov);
        }
    }

    public void combine(ArmOperand u, ArmOperand v) { // 将一个节点合并到另一个节点上同时维护相应的数据结构
        if (freezeWorkList.contains(v)) {
            freezeWorkList.remove(v);
        } else {
            spillWorkList.remove(v);
        }
        coalescedNodes.add(v);
        alias.put(v, u);
        HashSet<ArmVMov> uMoves = opMoveList.getOrDefault(u, new HashSet<>());
        uMoves.addAll(opMoveList.getOrDefault(v, new HashSet<>()));
        var vAdj = getAdjacent(v);
        for (var adj : vAdj) {
            addEdgeBetween(adj, u);
            subDegree(adj);
        }
        if (getDegree(u) >= k && freezeWorkList.contains(u)) {
            freezeWorkList.remove(u);
            spillWorkList.add(u);
        }
    }

    public boolean conservative(HashSet<ArmOperand> nodes1, HashSet<ArmOperand> nodes2) {
        // 统计一个大节点集合中, 度数大于13的节点的个数是否小于13
        // 判断合并是否满足保守条件, 也就是不会让太多节点的度数都超过13
        int count = 0;
        HashSet<ArmOperand> set = new HashSet<>();
        set.addAll(nodes1);
        set.addAll(nodes2);
        for (var node : set) {
            if (getDegree(node) >= k) {
                count++;
            }
        }
        return count < k;
    }

    public boolean adjOk(ArmOperand op1, ArmOperand op2) {
        // 判断两个节点是否可以安全合并
        HashSet<ArmOperand> adj = getAdjacent(op2);
        for (ArmOperand o : adj) {
            boolean isOk = false;
            if (getDegree(o) < k || o.isPreColored() || adjSet.contains(new Pair<>(o, op1))) {
                isOk = true;
            }
            if (!isOk) {
                return false;
            }
        }
        return true;
    }

    public void addSimplifyWorkList(ArmOperand op) {
        if (!op.isPreColored() && !isRelateToMov(op) && getDegree(op) < k) {
            freezeWorkList.remove(op);
            simplifyWorkList.add(op);
        }
    }

    public ArmOperand getAlias(ArmOperand op) { // 沿着别名链找最终别名
        while (coalescedNodes.contains(op)) {
            op = alias.get(op);
        }
        return op;
    }

    public void freeze() {
        ArmOperand u = freezeWorkList.iterator().next();
        freezeWorkList.remove(u); // 从freeze里面摘出来一个u
        simplifyWorkList.add(u);
        freezeMoves(u); // 冻结u节点
    }

    public void freezeMoves(ArmOperand u) {
        HashSet<ArmVMov> moves = nodeMoves(u);
        for (ArmVMov m : moves) {
            if (activeMoves.contains(m)) {
                activeMoves.remove(m);
            } else {
                workListMoves.remove(m);
            }
            frozenMoves.add(m);
            ArmOperand v;
            if (m.getDst().equals(u)) { // m是个和u有关的mov, 且u还在此mov中充当dst
                v = m.getDst();
            } else {
                v = m.getRhs();
            }
            if (nodeMoves(v).size() == 0 && getDegree(v) < k) {
                freezeWorkList.remove(v);
                simplifyWorkList.add(v); // 放弃掉一个冻结节点, 又可以合并了
            }
        }
    }

    public void selectSpill() {
        ArmOperand m = selectSpillNode(); // 自己选择一个spillNode
        spillWorkList.remove(m);
        simplifyWorkList.add(m); // 将选择的spillNode变成运作状态
        freezeMoves(m);
    }

    // TODO : 这里用更好的选择算法的话应该能更好地提高性能
    public ArmOperand selectSpillNode() {
        // 选择: 循环深度大, 度数高的节点
        return spillWorkList.iterator().next();
//        return spillWorkList.stream().max((l, r) ->{
//            double value1 = degree.getOrDefault(l, 0).doubleValue() /
//                    Math.pow(1.4, loopDepthMap.getOrDefault(l, 0));
//
//            double value2 = degree.getOrDefault(r, 0).doubleValue() /
//                    Math.pow(1.4, loopDepthMap.getOrDefault(r, 0));
//
//            if (l instanceof VirtualReg &&
//                    newLiveLength.getOrDefault((VirtualReg) l, this.INF) < 5) {
//                value1 = 0;
//            }
//
//            if (r instanceof VirtualReg &&
//                    newLiveLength.getOrDefault((VirtualReg) r, this.INF) < 5) {
//                value2 = 0;
//            }
//            return Double.compare(value1, value2);
//        }).get();
    }

    public int getDegree(ArmOperand v) {
        if (degree.containsKey(v)) {
            return degree.get(v);
        }
        degree.put(v, 0);
        return 0;
    }

    public void makeWorkList() {
        for (ArmOperand v : initial) {
            // 遍历所有函数用到的虚拟寄存器
            if (getDegree(v) >= k) { // 这个节点的度 >= k
                spillWorkList.add(v); // 这个v没得着色了, 死对头太多了
            } else if (isRelateToMov(v)) { // 这个v和move指令相关
                freezeWorkList.add(v);
            } else { // 普遍的情形
                simplifyWorkList.add(v);
            }
        }
        initial = new HashSet<>(); // 这时候initial就没用了
    }

    public boolean isRelateToMov(ArmOperand v) {
        if (opMoveList.containsKey(v) && opMoveList.get(v).size() > 0) {
            return true;
        }
        return false;
    }

    public void functionLiveAnalysis(ArmFunction armFunction) {
        liveAnalysisTools.put(armFunction, new LiveAnalysisTool(armFunction, armTools));
    }

    public HashSet<ArmOperand> getBlockLiveOut(ArmFunction armFunction, ArmBlock armBlock) {
        return this.liveAnalysisTools.get(armFunction).
                getAllBlockLiveInfo().get(armBlock).getLiveOut();
    }

    public void addMoveList(ArmOperand op, ArmInstr mov) {
        // 维护moveList
        if (!opMoveList.containsKey(op)) {
            opMoveList.put(op, new HashSet<>());
        }
        opMoveList.get(op).add((ArmVMov) mov);
    }

    public void addNearNodeInfo(ArmOperand dst, ArmOperand addE) {
        if (!adjList.containsKey(dst)) {
            adjList.put(dst, new HashSet<>());
        }
        adjList.get(dst).add(addE);
    }

    public void buildGraph(ArmFunction armFunction) {
        for (ArmBlock armBlock : armFunction.getArmBlocks()) {
            HashSet<ArmOperand> thisLive = new HashSet<>(getBlockLiveOut(armFunction, armBlock));
            // 遍历这个块的指令
            if (armBlock.getArmInstructions().size == 0) {
                continue;
            }
            ArmInstr nowInstr = armBlock.getArmInstructions().getLast();
            while (!nowInstr.equals(armBlock.getArmInstructions().head)) {
                if (nowInstr instanceof ArmVMov && ((ArmVMov) nowInstr).isPureFloatMov()) {
                    ArmOperand dst = ((ArmVMov) nowInstr).getDst();
                    ArmOperand rhs = ((ArmVMov) nowInstr).getRhs();
                    // 初始时: 虚拟全部needColor, 物理全部needColor,
                    if (dst.needColor() && rhs.needColor()) {
                        // 都还没分配的时候
                        thisLive.remove(rhs); // TODO thisLive一定会包含rhs吗？？？
                        addMoveList(rhs, nowInstr); // rhs涉及到的指令加进容器
                        addMoveList(dst, nowInstr);
                        workListMoves.add((ArmVMov) nowInstr);
                    }
                }
                ArrayList<Reg> useRegs = nowInstr.getUseRegs();
                ArrayList<Reg> defRegs = nowInstr.getDefRegs(); // TODO 对这两个东西进行维护
                // 拿到这条指令的所有: 待分配, 非浮点寄存器
                for (Reg op : defRegs) {
                    if (op.needColor() && op.isFloat()) {
                        thisLive.add(op); // TODO why
                    }
                }
                // 加边
                for (Reg op : defRegs) {
                    if (op.needColor() && op.isFloat()) {
                        for (var live : thisLive) {
                            addEdgeBetween(live, op); // TODO why
                        }
                    }
                }

                for (Reg op : defRegs) {
                    if (op.needColor() && op.isFloat()) {
                        thisLive.remove(op);
                    }
                }

                for (Reg op : useRegs) {
                    if (op.needColor() && op.isFloat()) {
                        thisLive.add(op);
                    }
                }

                if (nowInstr.getPrev().equals(armBlock.getArmInstructions().head)) {
                    break; // 这里如果不加判断的话下面 强转会报错
                }
                nowInstr = (ArmInstr) nowInstr.getPrev();
            }
        }
    }

    public void addDegree(ArmOperand op) {
        if (!degree.containsKey(op)) {
            degree.put(op, 1);
        } else {
            degree.put(op, degree.get(op) + 1);
        }
    }

    public void addEdgeBetween(ArmOperand e1, ArmOperand e2) {
        Pair<ArmOperand, ArmOperand> edge = new Pair<>(e1, e2);
        // 二者不等且边还没加, 那么就可以加边
        if (!e1.equals(e2) && !adjSet.contains(edge)) {
            adjSet.add(new Pair<>(e1, e2)); // 每次加要成对加进去
            adjSet.add(new Pair<>(e2, e1));

            if (!e1.isPreColored()) { // TODO 这里要对isPreColored方法返回值进行维护
                addNearNodeInfo(e1, e2);
                addDegree(e1);
            }
            if (!e2.isPreColored()) {
                addNearNodeInfo(e2, e1);
                addDegree(e2);
            }
        }
    }

    private static class Info {
        public VirtualReg vReg = null; // public修饰, 直接当结构体
        public ArmInstr firstUse = null;
        public ArmInstr lastDef = null;

        public void clear() {
            vReg = null;
            firstUse = null;
            lastDef = null;
        }
    }
}
