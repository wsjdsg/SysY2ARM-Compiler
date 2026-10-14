package midend.analysis;

import mir.BasicBlock;
import mir.Value;

import java.util.ArrayList;

public class Loop {
    private static int idCounter;
    /*-----------------迭代变量信息----------------------*/
    public Value valFromPhi = null;
    public Value valToCmpWith = null;
    public Value valFromAlu = null;
    public Value valFromDef = null;
    public Value valToCalWith = null;
    public Value cmpInstr = null;
    public int loopTimes = 0;
    public boolean idcHasSetted = false;
    /*---------------纯计算变量信息----------------------*/
    public Value valEntering = null;
    public Value aluForCal = null;
    public Value phiForCal = null;
    public int aluConstIdx = 0;
    public boolean calMesHasSetted = false;
    private int id;
    private BasicBlock entryBlock; //循环入口块
    private ArrayList<BasicBlock> allBlocks = new ArrayList<>();
    /*-------------------------------------------------*/
    private ArrayList<Loop> subLoops = new ArrayList<>();
    private ArrayList<BasicBlock> latchBlocks = new ArrayList<>();  //返回循环入口（条件判断）的块
    private ArrayList<BasicBlock> exits = new ArrayList<>();//在循环内，但有机会跳转到循环外的块
    private Loop parentLoop = null;
    private int loopDepth;
    /*------------------------------------------------*/


    public Loop(BasicBlock entryBlock, ArrayList<BasicBlock> latchBlocks) {
        id = idCounter++;
        this.entryBlock = entryBlock;
        entryBlock.setParentLoop(this);
        this.latchBlocks.addAll(latchBlocks);
    }

    public Loop() {
    }

    public void addExit(BasicBlock bb) {
        if (!exits.contains(bb)) {
            exits.add(bb);
        }
    }

    public ArrayList<BasicBlock> getAllBlocks() {
        return allBlocks;
    }

    public ArrayList<Loop> getSubLoops() {
        return subLoops;
    }

    public Loop getParentLoop() {
        return parentLoop;
    }

    public void setParentLoop(Loop loop) {
        parentLoop = loop;
    }

    public BasicBlock getEntryBlock() {
        return entryBlock;
    }

    public ArrayList<BasicBlock> getLatchBlocks() {
        return latchBlocks;
    }

    public ArrayList<BasicBlock> getExits() {
        return exits;
    }

    public void addSubLoop(Loop loop) {
        if (!subLoops.contains(loop)) {
            subLoops.add(loop);
        }
    }

    public void addBlock(BasicBlock block) {
        if (!allBlocks.contains(block)) {
            allBlocks.add(block);
        }
    }

    public int getLoopDepth() {
        return loopDepth;
    }

    public void setLoopDepth(int loopDepth) {
        this.loopDepth = loopDepth;
    }

    public boolean isStandardLoop() {
        return this.entryBlock.getPredecessors().size() == 2
                && entryBlock.getSuccessors().size() == 2
                && latchBlocks.size() == 1
                && exits.size() == 1
                && exits.get(0).equals(entryBlock);
    }

}
