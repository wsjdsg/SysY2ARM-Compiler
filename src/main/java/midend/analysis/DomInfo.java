package midend.analysis;

/*
    支配树（Dominant Tree）
    核心是Lengauer-Tarjan算法
    但是由于没写带权并查集优化，所以只能在求出半支配点后，在DAG上根据拓扑序求节点precursors的LCA
    部分可以考虑把ArrayList改为HashSet做优化
*/

import mir.BasicBlock;
import mir.Func;
import mir.Instr;
import mir.MyModule;
import tools.MyListNode;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DomInfo {
    private final HashMap<Func, BasicBlock> DTRootNodes;
    private final HashMap<Func, HashMap<BasicBlock, ArrayList<BasicBlock>>> DTAdjacencyLists;
    private final HashMap<Func, HashMap<BasicBlock, BasicBlock>> DTPrecursors;
    private final HashMap<Func, HashMap<BasicBlock, HashSet<BasicBlock>>> Doms;
    private final HashMap<Func, HashMap<BasicBlock, HashSet<BasicBlock>>> DFs;

    public DomInfo() {
        this.DTRootNodes = new HashMap<>();
        this.DTAdjacencyLists = new HashMap<>();
        this.DTPrecursors = new HashMap<>();
        this.Doms = new HashMap<>();
        this.DFs = new HashMap<>();
    }

    public void calculate(MyModule myModel) {
        DTRootNodes.clear();
        DTAdjacencyLists.clear();
        DTPrecursors.clear();
        Doms.clear();
        DFs.clear();
        DFG dfg = new DFG();
        dfg.build(myModel);
        for (Func func : dfg.getAdjacencyLists().keySet()) {
            if (func.getBasicBlocks().size == 0) continue;
            ;
            // 建立DFS树
            DfsTree dfsTree = new DfsTree();
            dfsTree.build(func.getBasicBlocks().getFirst(), dfg.getAdjacencyLists().get(func));
            // 通过寻找半支配点变换为DAG
            dfsTree.toDAG(dfg.getPrecursors().get(func));
            // 对DAG进行拓扑排序
            ArrayList<BasicBlock> topoOrder = new ArrayList<>();
            topologicalSort(func.getBasicBlocks().getFirst(), topoOrder, dfsTree);
            // 根据DAG的拓扑序构建支配树
            buildDT(func, topoOrder, dfsTree);
            // 计算支配边界
            calculateDF(func, dfg);
        }
        //王士举新增，计算块在支配树中的支配深度
        for (Func func : dfg.getAdjacencyLists().keySet()) {
            if (!func.isExternal()) {
                dfs2DTLevel(DTRootNodes.get(func), 0);
            }
        }
        //新增，维护BasicBlock的支配者
        for (Func func : dfg.getAdjacencyLists().keySet()) {
            if (!func.isExternal()) {
                for (BasicBlock bb : func.getBasicBlocks()) {
                    bb.setDominator(DTPrecursors.get(func).get(bb));
                }
            }
        }
    }

    public void dfs2DTLevel(BasicBlock bb, int level) {
        bb.setDomLevel(level);
        for (BasicBlock subBB : DTAdjacencyLists.get(bb.getBelongFunc()).get(bb)) {
            dfs2DTLevel(subBB, level + 1);
        }
    }

    private void topologicalSort(BasicBlock startNode, ArrayList<BasicBlock> topoOrder, DfsTree dfsTree) {
        HashMap<BasicBlock, ArrayList<BasicBlock>> dagAdjacencyList = dfsTree.getAdjacencyList();
        HashMap<BasicBlock, ArrayList<BasicBlock>> dagPredecessor = new HashMap<>();
        HashSet<BasicBlock> noInDegreeNodes = new HashSet<>();
        for (BasicBlock bb : dfsTree.getPredecessor().keySet()) {
            dagPredecessor.put(bb, new ArrayList<>(dfsTree.getPredecessor().get(bb)));
            if (dfsTree.getPredecessor().get(bb).isEmpty()) {
                noInDegreeNodes.add(bb);
            }
        }
        noInDegreeNodes.add(startNode);
        while (!noInDegreeNodes.isEmpty()) {
            topoOrder.addAll(noInDegreeNodes);
            HashSet<BasicBlock> tempNoInDegreeNodes = new HashSet<>();
            for (BasicBlock bb : noInDegreeNodes) {
                for (BasicBlock next : dagAdjacencyList.get(bb)) {
                    dagPredecessor.get(next).remove(bb);
                    if (dagPredecessor.get(next).isEmpty()) {
                        tempNoInDegreeNodes.add(next);
                    }
                }
            }
            noInDegreeNodes = tempNoInDegreeNodes;
        }
    }

    private void buildDT(Func func, ArrayList<BasicBlock> topoOrder, DfsTree dfsTree) {
        HashMap<BasicBlock, ArrayList<BasicBlock>> dtAdjacencyList = new HashMap<>();
        HashMap<BasicBlock, HashSet<BasicBlock>> dom = new HashMap<>();
        HashMap<BasicBlock, BasicBlock> dtPrecursor = new HashMap<>();
        // 节点在支配树中的深度，用来寻找LCA用
        HashMap<BasicBlock, Integer> dtDepth = new HashMap<>();
        // 建立支配树
        for (BasicBlock bb : topoOrder) {
            dtAdjacencyList.put(bb, new ArrayList<>());
            dom.put(bb, new HashSet<>());
            dom.get(bb).add(bb);

            if (dfsTree.getPredecessor().get(bb).isEmpty()) {
                if (this.DTRootNodes.get(func) != null) {
                    System.out.println("错误发生在根据拓扑序建立支配树时");
                }
                this.DTRootNodes.put(func, bb);
                dtDepth.put(bb, 0);
            } else if (dfsTree.getPredecessor().get(bb).size() == 1) {
                BasicBlock father = dfsTree.getPredecessor().get(bb).get(0);
                dtAdjacencyList.get(father).add(bb);
                dtPrecursor.put(bb, father);
                dtDepth.put(bb, dtDepth.get(father) + 1);
                do {
                    dom.get(father).add(bb);
                    father = dtPrecursor.get(father);
                } while (father != null);
            } else if (dfsTree.getPredecessor().get(bb).size() == 2) {
                BasicBlock tempDagPrecursor1 = dfsTree.getPredecessor().get(bb).get(0);
                BasicBlock tempDagPrecursor2 = dfsTree.getPredecessor().get(bb).get(1);

                // 寻找LCA
                while (!tempDagPrecursor1.equals(tempDagPrecursor2)) {
                    if (dtDepth.get(tempDagPrecursor1) == dtDepth.get(tempDagPrecursor2)) {
                        tempDagPrecursor1 = dtPrecursor.get(tempDagPrecursor1);
                        tempDagPrecursor2 = dtPrecursor.get(tempDagPrecursor2);
                    } else {
                        if (dtDepth.get(tempDagPrecursor1) > dtDepth.get(tempDagPrecursor2)) {
                            tempDagPrecursor1 = dtPrecursor.get(tempDagPrecursor1);
                        } else {
                            tempDagPrecursor2 = dtPrecursor.get(tempDagPrecursor2);
                        }
                    }
                }

                BasicBlock father = tempDagPrecursor1;
                dtAdjacencyList.get(father).add(bb);
                dtPrecursor.put(bb, father);
                dtDepth.put(bb, dtDepth.get(father) + 1);
                do {
                    dom.get(father).add(bb);
                    father = dtPrecursor.get(father);
                } while (father != null);
            } else {
                System.out.println("建立DAG时出错");
            }
        }

        this.DTAdjacencyLists.put(func, dtAdjacencyList);
        this.DTPrecursors.put(func, dtPrecursor);
        this.Doms.put(func, dom);
    }

    public HashMap<Func, HashMap<BasicBlock, ArrayList<BasicBlock>>> getDTAdjacencyLists() {
        return this.DTAdjacencyLists;
    }

    public HashMap<Func, HashMap<BasicBlock, BasicBlock>> getDTPrecursors() {
        return this.DTPrecursors;
    }

    private void calculateDF(Func func, DFG dfg) {
        HashMap<BasicBlock, HashSet<BasicBlock>> df = new HashMap<>();
        for (BasicBlock bb1 : func.getBasicBlocks()) {
            HashSet<BasicBlock> tempDf = new HashSet<>();
            for (BasicBlock bb2 : func.getBasicBlocks()) {
                for (BasicBlock preBb2 : dfg.getPrecursors().get(func).get(bb2)) {
                    // 可能有错
                    if (Doms.get(func).get(bb1).contains(preBb2) && !Doms.get(func).get(bb1).contains(bb2)) {
                        tempDf.add(bb2);
                        break;
                    }
                }
            }
            df.put(bb1, tempDf);
        }
        this.DFs.put(func, df);
    }

    public HashMap<Func, HashMap<BasicBlock, HashSet<BasicBlock>>> getDoms() {
        return this.Doms;
    }

    public HashMap<Func, HashMap<BasicBlock, HashSet<BasicBlock>>> getDFs() {
        return this.DFs;
    }
}


// dfs树
class DfsTree {
    private int nodeNumber;
    private ArrayList<BasicBlock> dfnOrder;
    private HashMap<BasicBlock, Integer> dfn;
    private HashMap<BasicBlock, ArrayList<BasicBlock>> adjacencyList;
    private HashMap<BasicBlock, ArrayList<BasicBlock>> predecessor;
    // key是一个点，value是它的半支配点
    private HashMap<BasicBlock, BasicBlock> semi;

    // 求dfs树和dfn序
    private HashSet<BasicBlock> visited;

    public void build(BasicBlock startNode, HashMap<BasicBlock, ArrayList<BasicBlock>> dfgMap) {
        this.nodeNumber = 0;
        this.dfnOrder = new ArrayList<>();
        this.dfn = new HashMap<>();
        this.adjacencyList = new HashMap<>();
        this.predecessor = new HashMap<>();
        this.predecessor.put(startNode, new ArrayList<>());
        this.visited = new HashSet<>();
        dfs(startNode, dfgMap);
    }

    public void dfs(BasicBlock node, HashMap<BasicBlock, ArrayList<BasicBlock>> dfgMap) {
        visited.add(node);
        dfnOrder.add(node);
        dfn.put(node, nodeNumber++);
        adjacencyList.put(node, new ArrayList<>());
        for (BasicBlock next : dfgMap.get(node)) {
            if (!visited.contains(next)) {
                adjacencyList.get(node).add(next);
                predecessor.put(next, new ArrayList<>());
                predecessor.get(next).add(node);
                dfs(next, dfgMap);
            }
        }
    }

    // 简化原有DFG，在dfs树上添加半支配点(semi(s), s)点对，构成有向无环图DAG
    // TODO: 实在不会并查集优化了，以后再写吧，晕....
    public void toDAG(HashMap<BasicBlock, ArrayList<BasicBlock>> dfgPredecessor) {
        this.semi = new HashMap<>();
        for (int i = dfnOrder.size() - 1; i >= 0; i--) {
            BasicBlock nowNode = dfnOrder.get(i);
            ArrayList<BasicBlock> preList = dfgPredecessor.get(nowNode);
            if (preList.isEmpty()) {
                continue;
            }
            BasicBlock tempSemi = preList.get(0);
            for (BasicBlock pre : preList) {
                if (dfn.get(pre) < dfn.get(nowNode)) {
                    if (dfn.get(pre) < dfn.get(tempSemi)) {
                        tempSemi = pre;
                    }
                } else {
                    BasicBlock loopPre = pre;
                    while (dfn.get(loopPre) > dfn.get(nowNode)) {
                        loopPre = semi.get(loopPre);
                    }
                    if (dfn.get(loopPre) < dfn.get(tempSemi)) {
                        tempSemi = loopPre;
                    }
                }
            }
            semi.put(nowNode, tempSemi);
            if (!adjacencyList.get(tempSemi).contains(nowNode)) {
                adjacencyList.get(tempSemi).add(nowNode);
                predecessor.get(nowNode).add(tempSemi);
            }
        }
    }

    public ArrayList<BasicBlock> getDfnOrder() {
        if (this.dfnOrder == null) {
            System.out.println("还未构建，请先调用build方法");
        }
        return this.dfnOrder;
    }

    public HashMap<BasicBlock, ArrayList<BasicBlock>> getAdjacencyList() {
        if (this.adjacencyList == null) {
            System.out.println("还未构建，请先调用build方法");
        }
        return this.adjacencyList;
    }

    public HashMap<BasicBlock, ArrayList<BasicBlock>> getPredecessor() {
        if (this.predecessor == null) {
            System.out.println("还未构建，请先调用build方法");
        }
        return this.predecessor;
    }

    public HashMap<BasicBlock, BasicBlock> getSemi() {
        if (this.semi == null) {
            System.out.println("还未构建，请以此调用build方法和toDAG方法");
        }
        return this.semi;
    }
}

/*
    数据流图（data flow graph）或者叫支配图、控制流图
    表征LLVM IR中每个basic block之间的关系
*/
class DFG {
    private HashMap<Func, HashMap<BasicBlock, ArrayList<BasicBlock>>> precursors;
    private HashMap<Func, HashMap<BasicBlock, ArrayList<BasicBlock>>> adjacencyLists;

    public DFG() {
        this.precursors = new HashMap<>();
        this.adjacencyLists = new HashMap<>();
    }

    public void build(MyModule model) {
        for (Func f : model.funcs) {
            HashMap<BasicBlock, ArrayList<BasicBlock>> adjacencyList = new HashMap<>();
            HashMap<BasicBlock, ArrayList<BasicBlock>> precursor = new HashMap<>();

            this.precursors.put(f, precursor);
            this.adjacencyLists.put(f, adjacencyList);

            for (BasicBlock bb : f.getBasicBlocks()) {
                precursor.put(bb, new ArrayList<>());
                adjacencyList.put(bb, new ArrayList<>());
            }

            for (BasicBlock bb : f.getBasicBlocks()) {
                Instr endInstr = bb.getInstrs().getLast();
                if (endInstr.isBranch()) {
                    BasicBlock branchTarget1 = ((Instr.BranchInstr) endInstr).getThenAct();
                    BasicBlock branchTarget2 = ((Instr.BranchInstr) endInstr).getElseAct();

                    if (!branchTarget1.equals(bb)) {
                        adjacencyList.get(bb).add(branchTarget1);
                        precursor.get(branchTarget1).add(bb);
                    }

                    if (!branchTarget2.equals(bb)) {
                        adjacencyList.get(bb).add(branchTarget2);
                        precursor.get(branchTarget2).add(bb);
                    }
                }
                if (endInstr.isJump()) {
                    BasicBlock jumpTarget = ((Instr.JumpInstr) endInstr).getTarget();
                    adjacencyList.get(bb).add(jumpTarget);
                    precursor.get(jumpTarget).add(bb);
                }
            }
        }
    }

    public HashMap<Func, HashMap<BasicBlock, ArrayList<BasicBlock>>> getAdjacencyLists() {
        return adjacencyLists;
    }

    public HashMap<Func, HashMap<BasicBlock, ArrayList<BasicBlock>>> getPrecursors() {
        return precursors;
    }
}