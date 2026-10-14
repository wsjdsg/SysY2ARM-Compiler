//基本块类，下辖多条指令
package mir;

import midend.analysis.CloneRelation;
import midend.analysis.Loop;
import mir.type.SymType;
import tools.MyList;

import java.util.ArrayList;
import java.util.Objects;

public class BasicBlock extends Value implements Cloneable {
    private static int basicBlockNum = 0;
    private String name;
    private Func belongFunc;
    private MyList<Instr> instrs = new MyList<>();
    private Instr instrsHead;
    private Instr instrsTail;
    private BasicBlock dominator = null;
    private int domLevel;
    private Loop parentLoop;

    private ArrayList<BasicBlock> predecessors = new ArrayList<>(); //前驱
    private ArrayList<BasicBlock> successors = new ArrayList<>(); //后继

    public BasicBlock() {
        super(new SymType.BasicBlockType());
        name = "Empty_Block" + basicBlockNum;
        basicBlockNum++;
        belongFunc = null;
        instrsHead = new Instr();
        instrsTail = new Instr();
        instrsHead.setNext(instrsTail);
        instrsTail.setPrev(instrsHead);
        instrs.head = instrsHead;
        instrs.tail = instrsTail;
    }

    public BasicBlock(Func func) {
        super(new SymType.BasicBlockType());
        name = "BB" + basicBlockNum;
        basicBlockNum++;
        belongFunc = func;
        instrsHead = new Instr();
        instrsTail = new Instr();
        instrsHead.setNext(instrsTail);
        instrsTail.setPrev(instrsHead);
        instrs.head = instrsHead;
        instrs.tail = instrsTail;
        belongFunc.insertAtTail(this);
    }

    public BasicBlock(Func func, Boolean forAlloca) {
        super(new SymType.BasicBlockType());
        name = "BBalloca" + basicBlockNum;
        basicBlockNum++;
        belongFunc = func;
        instrsHead = new Instr();
        instrsTail = new Instr();
        instrsHead.setNext(instrsTail);
        instrsTail.setPrev(instrsHead);
        instrs.head = instrsHead;
        instrs.tail = instrsTail;
        belongFunc.insertAtHead(this);
    }

    public ArrayList<BasicBlock> getSuccessors() {
        return successors;
    }

    public void setSuccessors(ArrayList<BasicBlock> successors) {
        this.successors = successors;
    }

    public ArrayList<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public void setPredecessors(ArrayList<BasicBlock> predecessors) {
        this.predecessors = predecessors;
    }

    public int getDomLevel() {
        return domLevel;
    }

    public void setDomLevel(int domLevel) {
        this.domLevel = domLevel;
    }

    public Loop getParentLoop() {
        return parentLoop;
    }

    public void setParentLoop(Loop loop) {
        this.parentLoop = loop;
    }

    public BasicBlock getDominator() {
        return dominator;
    }

    public void setDominator(BasicBlock bb) {
        dominator = bb;
    }

    public int getLoopDepth() {
        if (parentLoop == null) {
            return 0;
        } else {
            return parentLoop.getLoopDepth();
        }
    }

    public boolean isHasTerminated() {
        if (instrs.isEmpty()) {
            return false;
        }
        return instrs.getLast().isTerminator();
    }

    public void setBelongFunc(Func belongFunc, boolean ifInsert) {
        name = "BB" + basicBlockNum;
        basicBlockNum++;
        this.belongFunc = belongFunc;
        belongFunc.insertAtHead(this);
    }

    public Object clone() throws CloneNotSupportedException {
        BasicBlock bb = (BasicBlock) super.clone();
        bb.name = "BB" + basicBlockNum;
        basicBlockNum++;
        return bb;
    }//shallow copy!warning

    public BasicBlock getCopyInCertainFunc(Func func) throws CloneNotSupportedException {
        BasicBlock bb = (BasicBlock) this.clone();
        bb.belongFunc = func;
        bb.instrs = null;
        ArrayList<BasicBlock> copy1 = new ArrayList<>();
        ArrayList<BasicBlock> copy2 = new ArrayList<>();
        copy1.addAll(successors);
        copy2.addAll(predecessors);
        bb.successors = copy1;
        bb.predecessors = copy2;
        return bb;
    }

    @Override
    public String getName() {
        return name;
    }

    public Func getBelongFunc() {
        return belongFunc;
    }

    public void setBelongFunc(Func func) {
        belongFunc = func;
    }

    public MyList<Instr> getInstrs() {
        return instrs;
    }

    public void setInstrs(MyList<Instr> instrs) {
        this.instrs = instrs;
    }

    public void insertAtTail(Instr instr) {
        instrs.insertAtTail(instr);
    }

    public void insertAtHead(Instr instr) {
        instrs.insertAtHead(instr);
    }

    public boolean hasTerminator() {
        return ((Instr) this.instrsTail.getPrev()).isTerminator();
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        return ((BasicBlock) o).name.equals(name);
    }

    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return this.name;
    }

    public void cloneBlockForLoopUnroll(Func func, Loop loop) {
        Loop cloneLoop = null;
        if (getParentLoop().equals(loop)) {
            cloneLoop = getParentLoop();
        } else {
            Loop srcLoop = getParentLoop();
            ArrayList<BasicBlock> latches = new ArrayList<>();
            BasicBlock entry = new BasicBlock();
            cloneLoop = CloneRelation.srcLooptoCloneLoop.containsKey(srcLoop) ?
                    CloneRelation.getCloneLoop(srcLoop) :
                    new Loop(entry, latches);
            cloneLoop.setParentLoop(CloneRelation.getCloneLoop(srcLoop.getParentLoop()));
            CloneRelation.putCloneLoop(srcLoop, cloneLoop);
        }

        BasicBlock cloneBlock = new BasicBlock(func);
        cloneLoop.addBlock(cloneBlock);
        cloneBlock.setParentLoop(cloneLoop);
        CloneRelation.putCloneVal(this, cloneBlock);

        //System.out.println("***" + this + " : " + cloneBlock);

        for (Instr instr : this.instrs) {
            instr.rememberClone(cloneBlock);
        }
    }

    public void fixPreSuc(BasicBlock srcBB) {
        ArrayList<BasicBlock> pres = new ArrayList<>();
        ArrayList<BasicBlock> sucs = new ArrayList<>();
        for (BasicBlock block : srcBB.getPredecessors()) {
            pres.add((BasicBlock) CloneRelation.getCloneVal(block));
        }
        for (BasicBlock block : srcBB.getSuccessors()) {
            sucs.add((BasicBlock) CloneRelation.getCloneVal(block));
        }
        this.setPredecessors(pres);
        this.setSuccessors(sucs);
    }

    public void fixInstrs() {
        for (Instr instr : this.instrs) {
            instr.fixInstr();
        }
    }

}
