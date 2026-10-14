//指令类
//参考 llvm 指令集（Value 中的 tag）进行构造
//-------------------------------------------注意！！！---------------------------------------//
//--------------------phi 指令的配套设施（路径记录快、toString方法……）还未完善。2023.07.08-----------//

package mir;

import midend.analysis.CloneRelation;
import mir.type.SymType;

import java.util.ArrayList;
import java.util.Objects;

public class Instr extends Value implements Cloneable {
    private static int emptyInstrNum = 0;
    private static int instrNum = 0;
    public BasicBlock belongBlock;

    private ArrayList<Value> usedValues = new ArrayList<>(); //用到了哪些value

    public Instr() {
        super();
        name = "Empty_Instr" + emptyInstrNum;
        emptyInstrNum++;
        pre = LOCAL_PRE;
    }

    public Instr(SymType type, BasicBlock block) {
        super(type);
        belongBlock = block;
        if (!block.isHasTerminated() && !(this instanceof PhiInstr)) {
            belongBlock.insertAtTail(this);
        }
        name = LOCAL_NAME_PRE + instrNum;
        instrNum++;
        pre = LOCAL_PRE;
    }

    public void addUsedValue(Value value) {
        usedValues.add(value);
    }

    public void addUsedValue(Value value, BasicBlock bb) {
        usedValues.add(value);
        if (this instanceof PhiInstr) {
            ((PhiInstr) this).insertOption(bb);
        }
    }

    public ArrayList<Value> getUsedValues() {
        return usedValues;
    }

    public Object clone() throws CloneNotSupportedException {
        Instr instr = (Instr) super.clone();
        instr.name = LOCAL_NAME_PRE + instrNum;
        instrNum++;
        return instr;
    }

    public Instr getCopyInCertainBB(BasicBlock bb) throws CloneNotSupportedException {
        Instr instr = (Instr) this.clone();
        instr.belongBlock = bb;
        instr.usedValues = new ArrayList<>(usedValues);
        return instr;
    } //算是深拷贝了

    public BasicBlock getBelongBlock() {
        return belongBlock;
    }

    public void setBelongBlock(BasicBlock bb) {
        belongBlock = bb;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        return ((Instr) o).name.equals(name);
    }

    public int hashCode() {
        return Objects.hash(name);
    }

    public boolean isTerminator() {
        return this instanceof BranchInstr ||
                this instanceof JumpInstr ||
                this instanceof RetInstr;
    }

    public Instr rememberClone(BasicBlock block) {
        return null;
    }

    public void fixInstr() {
        ArrayList<Value> newUsedValues = new ArrayList<>();
        for (Value value : usedValues) {
            newUsedValues.add(CloneRelation.getCloneVal(value));
            if (!value.equals(CloneRelation.getCloneVal(value))) {
                CloneRelation.getCloneVal(value).addUser(this);
                value.getUsers().remove(this);
            }
        }
        this.usedValues = newUsedValues;
        if (this instanceof PhiInstr) {
            ArrayList<BasicBlock> newOpBlocks = new ArrayList<>();
            for (BasicBlock block : ((PhiInstr) this).getOptionBlocks()) {
//                System.out.println("*****" + block + "***" + CloneRelation.getCloneVal(block));/////////
                newOpBlocks.add((BasicBlock) CloneRelation.getCloneVal(block));
            }
            ((PhiInstr) this).setOptionBlocks(newOpBlocks);
        }
    }

    public void modifyUse(Value value, int idx) {
        Value old = this.usedValues.remove(idx);
        old.getUsers().remove(this);
        this.usedValues.add(idx, value);
//        if (value.getName().equals("%l773")) {
//            System.out.println("oldValue: " + old + " " + value + " " + this);
//        }
        value.addUser(this);
    }

    public boolean instrCanCombineInt() {
        if (!this.isBincal()) {
            return false;
        }
        if (!this.getType().isIntType()) {
            return false;
        }
        if (((AluInstr) this).aluOp != AluInstr.AluOp.ADD &&
                ((AluInstr) this).aluOp != AluInstr.AluOp.SUB) {
            return false;
        }
        if (this.getUsedValues().size() == 0) {
            throw new RuntimeException(getName() + " \n" + this.getPrev() + " \n" + this.getNext() + "\n" + belongBlock + "\n");
        }
        if (((AluInstr) this).getA1() instanceof Constant && (((AluInstr) this).getA2() instanceof Constant) ||
                !(((AluInstr) this).getA1() instanceof Constant) && !((((AluInstr) this).getA2() instanceof Constant))) {
            return false;
        }

        return true;
    }

    public boolean canCombineInt() {
        if (!instrCanCombineInt()) {
            return false;
        }
        for (Instr instr : this.getUsers()) {
            //System.out.println(instr.getName());
            //System.out.println(instr);
            if (!instr.instrCanCombineInt()) {
                return false;
            }
        }
        return true;
    }

    public boolean instrCanCombineFloat() {
        if (!this.isBincal()) {
            return false;
        }
        if (!this.getType().isFloatType()) {
            return false;
        }
        if (((AluInstr) this).aluOp != AluInstr.AluOp.FADD &&
                ((AluInstr) this).aluOp != AluInstr.AluOp.FSUB) {
            return false;
        }
        if (((AluInstr) this).getA1() instanceof Constant && (((AluInstr) this).getA2() instanceof Constant) ||
                !(((AluInstr) this).getA1() instanceof Constant) && !((((AluInstr) this).getA2() instanceof Constant))) {
            return false;
        }

        return true;
    }

    public boolean canCombineFloat() {
        if (!instrCanCombineFloat()) {
            return false;
        }
        for (Instr instr : this.getUsers()) {
            if (!instr.instrCanCombineFloat()) {
                return false;
            }
        }
        return true;
    }


    public static class AluInstr extends Instr {
        private AluOp aluOp;

        public AluInstr(SymType type, BasicBlock block, AluOp op, Value a1, Value a2) {
            super(type, block);
            tag = Tag.bincal;
            this.aluOp = op;
            addUsedValue(a1);
            addUsedValue(a2);
            a1.addUser(this);
            a2.addUser(this);
        }
        //private Value a1;
        //private Value a2;

        public AluOp getAluOp() {
            return aluOp;
        }

        public void setAluOp(AluOp aluOp) {
            this.aluOp = aluOp;
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        public Value getA2() {
            return getUsedValues().get(1);
        }

        public String toString() {
            return getName() + " = " + aluOp.name + " " + getA1().getType()
                    + " " + getA1().getName() + ", " + getA2().getName();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new AluInstr(getType(), block, getAluOp(), getA1(), getA2());
            CloneRelation.srcValtoCloneVal.put(this, cloneInstr);
            return cloneInstr;
        }

        public enum AluOp {
            ADD("add"),
            FADD("fadd"),
            SUB("sub"),
            FSUB("fsub"),
            MUL("mul"),
            FMUL("fmul"),
            DIV("sdiv"),
            FDIV("fdiv"),
            REM("srem"),
            FREM("frem"),
            AND("and"),
            OR("or");

            private String name;

            AluOp(String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }
    }

    public static class IcmpInstr extends Instr {
        private IcmpOp icmpOp;

        public IcmpInstr(BasicBlock block, IcmpOp op, Value a1, Value a2) {
            super(SymType.BasicType.getBasic_BOOL(), block);
            tag = Tag.icmp;
            this.icmpOp = op;
            addUsedValue(a1);
            addUsedValue(a2);
            a1.addUser(this);
            a2.addUser(this);
        }
        //private Value a1;
        //private Value a2;

        public IcmpOp getIcmpOp() {
            return icmpOp;
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        public Value getA2() {
            return getUsedValues().get(1);
        }

        public String toString() {
            return getName() + " = icmp " + icmpOp.name + " " + getA1().getType()
                    + " " + getA1().getName() + ", " + getA2().getName();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new IcmpInstr(block, getIcmpOp(), getA1(), getA2());
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }

        public enum IcmpOp {
            EQ("eq"),
            NE("ne"),
            SLT("slt"),
            SLE("sle"),
            SGT("sgt"),
            SGE("sge");

            private String name;

            IcmpOp(String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }
    }

    public static class FcmpInstr extends Instr {
        private FcmpOp fcmpOp;

        public FcmpInstr(BasicBlock block, FcmpOp op, Value a1, Value a2) {
            super(SymType.BasicType.getBasic_BOOL(), block);
            tag = Tag.fcmp;
            this.fcmpOp = op;
            addUsedValue(a1);
            addUsedValue(a2);
            a1.addUser(this);
            a2.addUser(this);
        }
        //private Value a1;
        //private Value a2;

        public FcmpOp getFcmpOp() {
            return fcmpOp;
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        public Value getA2() {
            return getUsedValues().get(1);
        }

        public String toString() {
            return getName() + " = fcmp " + fcmpOp.name + " " + getA1().getType()
                    + " " + getA1().getName() + ", " + getA2().getName();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new FcmpInstr(block, getFcmpOp(), getA1(), getA2());
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }

        public enum FcmpOp {
            OEQ("oeq"),
            ONE("one"),
            OLT("olt"),
            OLE("ole"),
            OGT("ogt"),
            OGE("oge");

            private String name;

            FcmpOp(String name) {
                this.name = name;
            }

            public String getName() {
                return this.name;
            }
        }
    }

    public static class StoreInstr extends Instr {
        //private Value value;
        //private Value address;

        public StoreInstr(Value value, Value address, BasicBlock block) {
            super(value.getType(), block);
            tag = Tag.store;
            getUsedValues().add(value);
            getUsedValues().add(address);
            this.belongBlock = block;
            value.addUser(this);
            address.addUser(this);
        }

        public Value getValue() {
            return getUsedValues().get(0);
        }

        public Value getAddress() {
            return getUsedValues().get(1);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        public String toString() {
            return "store " + getValue().getType() + " " + getValue().getName() + ", "
                    + getAddress().getType() + " " + getAddress().getName();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new StoreInstr(getValue(), getAddress(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class LoadInstr extends Instr {
        //private Value address;

        public LoadInstr(Value address, BasicBlock block) {
            super(((SymType.PointerType) address.getType()).getPointToType(), block);
            tag = Tag.load;
            addUsedValue(address);
            this.belongBlock = block;
            address.addUser(this);
        }

        public Value getAddress() {
            return getUsedValues().get(0);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return getName() + " = load " + type.toString() + ", "
                    + getAddress().getType() + " " + getAddress().getName();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new LoadInstr(getAddress(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class AllocaInstr extends Instr {
        private SymType contentType;

        public AllocaInstr(SymType type, BasicBlock block) {
            super(new SymType.PointerType(type), block);
            tag = Tag.alloca;
            this.contentType = type;
            this.belongBlock = block;
        }

        public SymType getContentType() {
            return contentType;
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return getName() + " = alloca " + this.contentType;
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new AllocaInstr(contentType, block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class GepInstr extends Instr {
        private SymType pointToType;
        //private Value pointer;
        //private ArrayList<Value> dimLens;

        private String strName = null;

        public GepInstr(SymType type, Value pointer, ArrayList<Value> dimLens, BasicBlock block, String strName) {
            super(new SymType.PointerType(type), block);
            tag = Tag.gep;
            this.pointToType = type;
            addUsedValue(pointer);
            this.belongBlock = block;
            this.strName = strName;
            pointer.addUser(this);
            for (Value value : dimLens) {
                addUsedValue(value);
                value.addUser(this);
            }
        }

        public GepInstr(SymType type, Value pointer, ArrayList<Value> dimLens, BasicBlock block) {
            super(new SymType.PointerType(type), block);
            tag = Tag.gep;
            this.pointToType = type;
            addUsedValue(pointer);
            this.belongBlock = block;
            pointer.addUser(this);
            for (Value value : dimLens) {
                addUsedValue(value);
                value.addUser(this);
            }
        }

        public Value getPointer() {
            return getUsedValues().get(0);
        }

        public SymType getPointToType() {
            return pointToType;
        }

        public ArrayList<Value> getDimLens() {
            return new ArrayList<>(getUsedValues().subList(1, getUsedValues().size()));
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getName()).append(" = getelementptr inbounds ");
            sb.append(((SymType.PointerType) getPointer().getType()).getPointToType());
            sb.append(", ").append(getPointer().getType()).append(" ");

            if (this.strName == null) {
                sb.append(getPointer().getName());
            } else {
                sb.append(this.strName);
            }

            ArrayList<Value> dimlens = getDimLens();
            for (Value dimLen : dimlens) {
                sb.append(", ").append(dimLen.getType()).append(" ").append(dimLen.getName());
            }

            return sb.toString();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new GepInstr(pointToType, getPointer(), getDimLens(), block, strName);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class CallInstr extends Instr {
        //private Func func;
        //private ArrayList<Value> params = new ArrayList<>();    //实际参数

        public CallInstr(Func func, ArrayList<Value> params, BasicBlock block) {
            super(func.getRetType(), block);
            tag = Tag.call;
            addUsedValue(func);
            this.belongBlock = block;
            func.addUser(this);
            for (Value value : params) {
                addUsedValue(value);
                value.addUser(this);
            }
        }

        public Func getFunc() {
            return (Func) getUsedValues().get(0);
        }

        public ArrayList<Value> getParams() {
            return new ArrayList<Value>(getUsedValues().subList(1, getUsedValues().size()));
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            String ret = "void";
            String assign = "";
            if (!type.isVoidType()) {
                ret = type.toString();
                assign = getName() + " = ";
            }
            StringBuilder paramsStr = new StringBuilder();
            for (Value param : getParams()) {
                paramsStr.append(param.getType()).append(" ").append(param.getName()).append(", ");
            }
            String str;
            if (getParams().isEmpty()) {
                str = "";
            } else {
                str = paramsStr.toString().substring(0, paramsStr.toString().length() - 2);
            }
            if (getFunc().getName().equals("starttime") || getFunc().getName().equals("stoptime")) {
                return assign + "call " + ret + " @_sysy_" + getFunc().getName() + "(i32 0)";
            }
            return assign + "call " + ret + " @" + getFunc().getName() + "(" + str + ")";
        }

        public boolean isPure() {
            Func func = getFunc();
            if (func.hasSideEffect()) {
                return false;
            }
            for (Func.Param param : func.getParams()) {
                if (param.getType().isPointerType()) {
                    return false;
                }
            } //只有指针可以办到
            return !func.isUseGv();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new CallInstr(getFunc(), getParams(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class PhiInstr extends Instr implements Cloneable {
        private SymType type;
        //private ArrayList<Value> options;

        private ArrayList<BasicBlock> optionBlocks;
        private int optionNum;

        public PhiInstr(SymType type, ArrayList<Value> options, BasicBlock block) {
            super(type, block);
            tag = Tag.phi;
            this.type = type;
            this.belongBlock = block;
            belongBlock.insertAtHead(this);
            for (Value value : options) {
                addUsedValue(value);
                value.addUser(this);
            }
            this.optionBlocks = new ArrayList<>();
            this.optionNum = 0;
        }

        public PhiInstr(SymType type, BasicBlock belongBlock) {
            super(type, belongBlock);
            tag = Tag.phi;
            this.type = type;
            this.belongBlock = belongBlock;
            belongBlock.insertAtHead(this);
            this.optionBlocks = new ArrayList<>();
            this.optionNum = 0;
        }

        public ArrayList<BasicBlock> getOptionBlocks() {
            return optionBlocks;
        }

        public void setOptionBlocks(ArrayList<BasicBlock> optionBlocks) {
            this.optionBlocks = optionBlocks;
            this.optionNum = optionBlocks.size();
        }

        public Object clone() throws CloneNotSupportedException {
            PhiInstr phiInstr = (PhiInstr) super.clone();
            phiInstr.optionBlocks = new ArrayList<>(optionBlocks);
            return phiInstr;
        }

        @Override
        public SymType getType() {
            return type;
        }

        public ArrayList<Value> getOptions() {
            return getUsedValues();
        }

        @Override
        public BasicBlock getBelongBlock() {
            return super.getBelongBlock();
        }

        public void insertOption(BasicBlock bb) {
            this.optionBlocks.add(bb);
            this.optionNum++;
        }

        public void removeOption(BasicBlock bb) {
            this.optionBlocks.remove(bb);
            this.optionNum--;
        }

        @Override
        public Instr rememberClone(BasicBlock newblock) {
            Instr cloneInstr = new PhiInstr(getType(), getOptions(), newblock);
            for (BasicBlock block : getOptionBlocks()) {
                ((PhiInstr) cloneInstr).insertOption(block);
            }
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }

        public String toString() {
            String str = getName() + " = phi " + type.toString() + " ";
            for (int i = 0; i < optionNum; i++) {
                if (i == 0) {
                    str = str + "[ " + getUsedValues().get(i).getName() + " , " + "%" + optionBlocks.get(i) + " ]";
                } else {
                    str = str + ", " + "[ " + getUsedValues().get(i).getName() + " , %" + optionBlocks.get(i) + " ]";
                }
            }
            return str;
        }
    }

    public static class BranchInstr extends Instr {
        //private Value cond;
        //private BasicBlock thenAct;
        //private BasicBlock elseAct;

        public BranchInstr(Value cond, BasicBlock thenAct, BasicBlock elseAct, BasicBlock block) {
            super(new SymType.VoidType(), block);
            tag = Tag.branch;
            addUsedValue(cond);
            addUsedValue(thenAct);
            addUsedValue(elseAct);
            this.belongBlock = block;
            cond.addUser(this);
            thenAct.addUser(this);
            elseAct.addUser(this);
        }

        public Value getCond() {
            return getUsedValues().get(0);
        }

        public BasicBlock getThenAct() {
            return (BasicBlock) getUsedValues().get(1);
        }

        public BasicBlock getElseAct() {
            return (BasicBlock) getUsedValues().get(2);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return "br " + getCond().getType() + " " + getCond().getName() +
                    ", label %" + getThenAct().getName() + ", label %" + getElseAct().getName();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new BranchInstr(getCond(), getThenAct(), getElseAct(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }

        public boolean ifThenDirect() {
            if (getThenAct() == null) {
                return false;
            }
            return getThenAct().equals(this.belongBlock.getNext());
        }

        public boolean ifElseDirect() {
            if (getElseAct() == null) {
                return false;
            }
            return getElseAct().equals(this.belongBlock.getNext());
        }
    }

    public static class JumpInstr extends Instr {
        //private BasicBlock target;

        public JumpInstr(BasicBlock target, BasicBlock block) {
            super(new SymType.VoidType(), block);
            tag = Tag.jump;
            addUsedValue(target);
            this.belongBlock = block;
            if (!block.getSuccessors().contains(target)) {
                block.getSuccessors().add(target);
            }
            if (!target.getPredecessors().contains(block)) {
                target.getPredecessors().add(block);
            }
            target.addUser(this);
        }

        public BasicBlock getTarget() {
            return (BasicBlock) getUsedValues().get(0);
        }

        public void setTarget(BasicBlock block) {
            BasicBlock old = (BasicBlock) this.getUsedValues().remove(0);
            old.getUsers().remove(this);
            this.getUsedValues().add(block);
            block.addUser(this);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return "br label %" + getTarget().getName();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new JumpInstr(getTarget(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }

        public boolean ifTargetDirect() {
            if (getTarget() == null) {
                return false;
            }
            return getTarget().equals(this.belongBlock.getNext());
        }
    }

    public static class RetInstr extends Instr {
        //private Value retValue = null;

        public RetInstr(Value retValue, BasicBlock block) {
            super(retValue.getType(), block);
            tag = Tag.ret;
            addUsedValue(retValue);
            this.belongBlock = block;
            retValue.addUser(this);
        }

        public RetInstr(BasicBlock block) {
            super(new SymType.VoidType(), block);
            tag = Tag.ret;
            this.belongBlock = block;
        }

        public Value getRetValue() {
            return getUsedValues().isEmpty() ? null : getUsedValues().get(0);
        }

        public void setRetValue(Value value) {
            if (getUsedValues().isEmpty()) {
                getUsedValues().add(value);
            } else {
                Value old = getUsedValues().remove(0);
                old.getUsers().remove(this);
                getUsedValues().add(0, value);
            }
            value.addUser(this);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            if (getRetValue() == null) {
                return "ret void";
            } else {
                return "ret " + getRetValue().getType() + " " + getRetValue().getName();
            }
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = getRetValue() == null ? new RetInstr(block)
                    : new RetInstr(getRetValue(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class FptosiInstr extends Instr {
        //private Value a1;

        public FptosiInstr(Value a1, BasicBlock block) {
            super(SymType.BasicType.getBasic_INT(), block);
            tag = Tag.fptosi;
            addUsedValue(a1);
            this.belongBlock = block;
            a1.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return getName() + " = fptosi " + getA1().getType() + " " + getA1().getName() + " to " + this.getType();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new FptosiInstr(getA1(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class SitofpInstr extends Instr {
        //private Value a1;

        public SitofpInstr(Value a1, BasicBlock block) {
            super(SymType.BasicType.getBasic_FLOAT(), block);
            tag = Tag.sitofp;
            addUsedValue(a1);
            this.belongBlock = block;
            a1.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        public String toString() {
            return getName() + " = sitofp " + getA1().getType() + " " + getA1().getName() + " to " + this.getType();
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new SitofpInstr(getA1(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class BitcastInstr extends Instr {
        //private Value a1;
        private SymType desType;

        public BitcastInstr(Value a1, SymType desType, BasicBlock block) {
            super(desType, block);
            tag = Tag.bitcast;
            addUsedValue(a1);
            this.desType = desType;
            this.belongBlock = block;
            a1.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        public SymType getDesType() {
            return desType;
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return getName() + " = bitcast " + getA1().getType() + " " + getA1().getName() + " to " + desType;
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new BitcastInstr(getA1(), getDesType(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class ZextInstr extends Instr {
        //private Value a1;

        public ZextInstr(Value a1, BasicBlock block) {
            super(SymType.BasicType.getBasic_INT(), block);
            tag = Tag.zext;
            addUsedValue(a1);
            this.belongBlock = block;
            a1.addUser(this);
        }

        public ZextInstr(Value a1, BasicBlock block, SymType type) {
            super(type, block);
            tag = Tag.zext;
            addUsedValue(a1);
            this.belongBlock = block;
            a1.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return getName() + " = zext " + getA1().getType() + " " + getA1().getName() + " to " + this.type;
        }

        @Override
        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new ZextInstr(getA1(), block, getType());
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class TruncInstr extends Instr {
        public TruncInstr(Value a1, BasicBlock block) {
            super(SymType.BasicType.getBasic_Char(), block);
            tag = Tag.trunc;
            addUsedValue(a1);
            this.belongBlock = block;
            a1.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return getName() + " = trunc " + getA1().getType() + " " + getA1().getName() + " to " + this.type;
        }

        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new TruncInstr(getA1(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class AshrInstr extends Instr {
        //private Value a1;
        //private Value a2;

        public AshrInstr(SymType type, Value a1, Value a2, BasicBlock block) {
            super(type, block);
            tag = Tag.ashr;
            addUsedValue(a1);
            addUsedValue(a2);
            this.belongBlock = block;
            a1.addUser(this);
            a2.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        public Value getA2() {
            return getUsedValues().get(1);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        @Override
        public String toString() {
            return getName() + " = ashr " + getA1().getType() + " " + getA2().getName() + ", " + getA2().getName();
        }

        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new AshrInstr(getType(), getA1(), getA2(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class LshrInstr extends Instr {
        //private Value a1;
        //private Value a2;

        public LshrInstr(SymType type, Value a1, Value a2, BasicBlock block) {
            super(type, block);
            tag = Tag.lshr;
            addUsedValue(a1);
            addUsedValue(a2);
            this.belongBlock = block;
            a1.addUser(this);
            a2.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        public Value getA2() {
            return getUsedValues().get(1);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        public String toString() {
            return getName() + " = lshr " + getA1().getType() + " " + getA1().getName() + ", " + getA2().getName();
        }

        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new LshrInstr(getType(), getA1(), getA2(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class ShlInstr extends Instr {
        //private Value a1;
        //private Value a2;

        public ShlInstr(SymType type, Value a1, Value a2, BasicBlock block) {
            super(type, block);
            tag = Tag.shl;
            addUsedValue(a1);
            addUsedValue(a2);
            this.belongBlock = block;
            a1.addUser(this);
            a2.addUser(this);
        }

        public Value getA1() {
            return getUsedValues().get(0);
        }

        public Value getA2() {
            return getUsedValues().get(1);
        }

        @Override
        public BasicBlock getBelongBlock() {
            return belongBlock;
        }

        public String toString() {
            return getName() + " = shl " + getA1().getType() + " " + getA1().getName() + ", " + getA2().getName();
        }

        public Instr rememberClone(BasicBlock block) {
            Instr cloneInstr = new ShlInstr(getType(), getA1(), getA2(), block);
            CloneRelation.putCloneVal(this, cloneInstr);
            return cloneInstr;
        }
    }

    public static class MoveInstr extends Instr {
        private Value src;
        private Value dst;

        public MoveInstr(SymType type, Value src, Value dst, BasicBlock block) {
            super(type, block);
            tag = Tag.move;
            this.src = src;
            this.dst = dst;
        }

        public Value getSrc() {
            return src;
        }

        public Value getDst() {
            return dst;
        }

        @Override
        public String getName() {
            return dst.getName();
        }

        public String toString() {
            return "move " + getType() + " " + src.getName() + " to " + dst.getName();
        }
    }

}
