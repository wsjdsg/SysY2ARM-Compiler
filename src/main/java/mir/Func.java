//函数类，下辖多个基本快
package mir;

import midend.analysis.Loop;
import mir.type.SymType;
import tools.MyList;

import java.util.ArrayList;
import java.util.Objects;

public class Func extends Value implements Cloneable {
    private String name;
    private boolean isExternal = false;
    private SymType retType = null;
    private MyList<BasicBlock> basicBlocks = new MyList<>();
    private BasicBlock funcBody = null;
    private ArrayList<Param> params = new ArrayList<>();    //形式参数

    private ArrayList<Func> callList = new ArrayList<>(); //调用了哪些自定义函数
    private ArrayList<Func> callerList = new ArrayList<>(); //被哪些自定义函数调用

    private boolean useGv = false;
    private ArrayList<Loop> allLoops = new ArrayList<>(); //所有的Loop都在里面
    private ArrayList<Loop> topLoops = new ArrayList<>(); //顶层Loop

    private boolean hasSideEffect = false;

    public Func(String name, boolean isExternal,
                SymType retType, ArrayList<Param> params) {
        this.name = name;
        this.isExternal = isExternal;
        this.retType = retType;
        this.params = params;
        for (Param param : params) {
            param.setBelongFunc(this);
        }
    }

    public static ArrayList<Param> genParamsFromType(SymType... types) {
        ArrayList<Param> params1 = new ArrayList<>();
        for (SymType type1 : types) {
            Param param = new Param(type1);
            params1.add(param);
        }
        return params1;
    }

    public ArrayList<Loop> getAllLoops() {
        return allLoops;
    }

    public boolean isUseGv() {
        return useGv;
    }

    public void setUseGv(boolean useGv) {
        this.useGv = useGv;
    }

    public void addLoop(Loop loop) {
        if (!allLoops.contains(loop)) {
            allLoops.add(loop);
        }
    }

    public void addTopLoop(Loop loop) {
        if (!topLoops.contains(loop)) {
            topLoops.add(loop);
        }
    }

    public ArrayList<Loop> getTopLoops() {
        return topLoops;
    }

    public void setHasSideEffect(boolean hasSideEffect) {
        this.hasSideEffect = hasSideEffect;
    }

    public boolean hasSideEffect() {
        return hasSideEffect;
    }

    public MyList<BasicBlock> getBbs() {
        return basicBlocks;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    } //just shallow copy!!!!

    public boolean isExternal() {
        return isExternal;
    }

    public ArrayList<Func> getCallList() {
        return callList;
    }

    public ArrayList<Func> getCallerList() {
        return callerList;
    }

    public MyList<BasicBlock> getBasicBlocks() {
        return basicBlocks;
    }

    public void setBasicBlocks(MyList<BasicBlock> bbs) {
        basicBlocks = bbs;
    }

    public void addCall(Func f) {
        if (!callList.contains(f)) {
            callList.add(f);
        }
    }

    public void addCaller(Func f) {
        if (!callerList.contains(f)) {
            callerList.add(f);
        }
    }

    public SymType getRetType() {
        return retType;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getRetTypeInStr() {
        return retType.toString();
    }

    public BasicBlock getFuncBody() {
        return funcBody;
    }

    public void setFuncBody(BasicBlock funcBody) {
        this.funcBody = funcBody;
    }

    public ArrayList<Param> getParams() {
        return params;
    }

    public void setParams(ArrayList<Param> arr) {
        params = arr;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        return name.equals(((Func) o).name);
    }

    public int hashCode() {
        return Objects.hash(name);
    }

    public void insertAtTail(BasicBlock block) {
        basicBlocks.insertAtTail(block);
    }

    public void insertAtHead(BasicBlock block) {
        basicBlocks.insertAtHead(block);
    }

    public String getDeclare() {
        StringBuilder paramsStr = new StringBuilder();
        for (Param param : params) {
            paramsStr.append(param.toString()).append(", ");
        }
        String str;
        if (params.isEmpty()) {
            str = "";
        } else {
            str = paramsStr.toString().substring(0, paramsStr.toString().length() - 2);
        }
        if (name.equals("starttime") || name.equals("stoptime")) {
            return "declare " + retType.toString() + " @_sysy_" + name + "(" + str + ")\n";
        } else {
            return "declare " + retType.toString() + " @" + name + "(" + str + ")\n";
        }
    }

    public String outMir() {
        if (this.funcBody == null) {
            return null;
        }

        StringBuilder paramsStr = new StringBuilder();
        for (Param param : params) {
            paramsStr.append(param.toString()).append(", ");
        }
        String str;
        if (params.isEmpty()) {
            str = "";
        } else {
            str = paramsStr.toString().substring(0, paramsStr.toString().length() - 2);
        }
        String dec;
        if (name.equals("starttime") || name.equals("stoptime")) {
            dec = "define dso_local " + retType.toString() + " @_sysy_" + name + "(" + str + ")";
        } else {
            dec = "define dso_local " + retType.toString() + " @" + name + "(" + str + ")";
        }
        StringBuilder str1 = new StringBuilder();
        str1.append(dec).append("{\n");
        for (BasicBlock block : basicBlocks) {
            str1.append(block).append(":\n");
            for (Instr instr : block.getInstrs()) {
                str1.append("    ").append(instr).append("\n");
            }
        }
        str1.append("}\n");
        return str1.toString();
    }

    public static class Param extends Value implements Cloneable {
        private static int paramNum = 0;
        private Func belongFunc;

        public Param(SymType type) {
            this.type = type;
            if (!MyModule.curIsExt) {
                pre = LOCAL_PRE;
                name = FPARAM_NAME_PRE + paramNum;
                paramNum++;
            }
        }

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        public Param getCopyInCertainFunc(Func func) throws CloneNotSupportedException {
            Param param = (Param) this.clone();
            param.belongFunc = func;
            return param;
        }

        public String toString() {
            return this.type + " " + getName();
        }

        public Func getBelongFunc() {
            return belongFunc;
        }

        public void setBelongFunc(Func belongFunc) {
            this.belongFunc = belongFunc;
        }
    }

}
