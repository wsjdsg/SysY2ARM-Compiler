package midend.analysis;

import mir.BasicBlock;
import mir.Func;
import mir.MyModule;
import tools.MyList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 建立传入的MyModule对象的Loop信息
 */
public class LoopAnalysis {
    private static HashSet<Loop> alreadyAddedBBs = new HashSet<>();
    private static DomInfo domInfo = new DomInfo();

    public static void runLoopAnalysis(MyModule module) {
        domInfo = new DomInfo();
        domInfo.calculate(module);
        for (Func func : module.getFuncs()) {
            if (!func.isExternal()) {
                analyzeLoopInFunc(func, domInfo.getDTAdjacencyLists().get(func));
            }
        }
    }

    public static void analyzeLoopInFunc(Func func, HashMap<BasicBlock, ArrayList<BasicBlock>> dg) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            bb.setParentLoop(null);
        }
        func.getTopLoops().clear();
        func.getAllLoops().clear();
        ArrayList<BasicBlock> latchBlocks = new ArrayList<>();
        ArrayList<BasicBlock> ord = getPostOrder(func.getBbs(), dg);
        for (BasicBlock bb : ord) {
            for (BasicBlock pre : bb.getPredecessors()) {
                if (domInfo.getDoms().get(func).get(bb).contains(pre)) {
                    latchBlocks.add(pre);
                }
            }
            if (!latchBlocks.isEmpty()) {
                Loop loop = new Loop(bb, latchBlocks);
                getLoop(latchBlocks, loop);
                func.addLoop(loop);
                latchBlocks.clear();
            }
        }
        summarizeLoopInfo(func);
    }

    public static void summarizeLoopInfo(Func func) {
        alreadyAddedBBs.clear();
        for (Loop loop : func.getAllLoops()) {
            int d = 1;
            Loop tmp = loop;
            while (tmp.getParentLoop() != null) {
                d++;
                tmp = tmp.getParentLoop();
            }
            loop.setLoopDepth(d);
            if (d == 1) {
                func.addTopLoop(loop);
            }
        }
        for (BasicBlock bb : func.getBasicBlocks()) {
            Loop loop = bb.getParentLoop();
            while (loop != null) {
                loop.addBlock(bb);
                loop = loop.getParentLoop();
            }
        }
        for (Loop loop : func.getAllLoops()) {
            for (BasicBlock bb : loop.getAllBlocks()) {
                if (!loop.getAllBlocks().containsAll(bb.getSuccessors())) {
                    loop.addExit(bb);
                }
            }
        }
    }

    public static void getLoop(ArrayList<BasicBlock> latches, Loop loop) {
        ArrayDeque<BasicBlock> queue = new ArrayDeque<>(latches);
        while (!queue.isEmpty()) {
            BasicBlock bb = queue.pollFirst();
            Loop subLoop = bb.getParentLoop();
            if (subLoop == null) {
                bb.setParentLoop(loop);
                loop.addBlock(bb);
                if (bb.equals(loop.getEntryBlock())) {
                    continue;
                }
                queue.addAll(bb.getPredecessors());
            } else {
                //处理子循环
                Loop outLoop = subLoop.getParentLoop();
                while (outLoop != null) {
                    subLoop = outLoop;
                    outLoop = outLoop.getParentLoop();
                }
                if (subLoop.equals(loop)) {
                    continue; //已经设置过了，没必要再设置了
                }
                subLoop.setParentLoop(loop);
                loop.addSubLoop(subLoop);
                for (BasicBlock pre : subLoop.getEntryBlock().getPredecessors()) {
                    //不能是有回边的块，不然会陷入循环之中
                    if (pre.getParentLoop() != subLoop) {
                        queue.add(pre);
                    }
                }
            }
        }
    }

    public static ArrayList<BasicBlock> getPostOrder(MyList<BasicBlock> bbs,
                                                     HashMap<BasicBlock, ArrayList<BasicBlock>> dg) {
        ArrayList<BasicBlock> ans = new ArrayList<>();
        postDfs(bbs.getFirst(), dg, ans);
        return ans;
    }

    public static void postDfs(BasicBlock bb, HashMap<BasicBlock, ArrayList<BasicBlock>> dg,
                               ArrayList<BasicBlock> ans) {
        if (dg.get(bb).size() == 0) {
            ans.add(bb);
            return;
        }
        for (int i = 0; i < dg.get(bb).size(); i++) {
            postDfs(dg.get(bb).get(i), dg, ans);
        }
        ans.add(bb);
    }
}
