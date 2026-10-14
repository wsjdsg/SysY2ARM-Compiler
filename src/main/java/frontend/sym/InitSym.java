//变量初值类

package frontend.sym;

import frontend.visitor.ForDefInit;
import mir.Value;
import mir.type.SymType;

import java.util.ArrayList;

import static mir.Constant.IntConst.CONST_INT_0;

public abstract class InitSym {
    protected SymType type;

    public InitSym(SymType type) {
        this.type = type;
    }

    public abstract ForDefInit.ForArrayFlatten flatten();

    public SymType getType() {
        return type;
    }

    //0初始
    public static class InitZero extends InitSym {
        public InitZero(SymType type) {
            super(type);
        }

        @Override
        public ForDefInit.ForArrayFlatten flatten() {
            int size = 0;
            if (getType().isBasicType()) {
                size = 1;
            } else if (getType().isArrayType()) {
                size = ((SymType.ArrayType) getType()).getSumSize();
            }
            ForDefInit.ForArrayFlatten forArrayFlatten = new ForDefInit.ForArrayFlatten();
            forArrayFlatten.insertAtTail(new ForDefInit.ForArrayFlatten.Elements(CONST_INT_0, size));
            return forArrayFlatten;
        }

        public String toString() {
            return getType() + " zeroinitializer";
        }
    }

    //具体值初始化
    public static class InitValue extends InitSym {
        private Value value;

        public InitValue(SymType type, Value value) {
            super(type);
            this.value = value;
        }

        public ForDefInit.ForArrayFlatten flatten() {
            ForDefInit.ForArrayFlatten forArrayFlatten = new ForDefInit.ForArrayFlatten();
            forArrayFlatten.insertAtTail(new ForDefInit.ForArrayFlatten.Elements(value, 1));
            return forArrayFlatten;
        }

        public Value getValue() {
            return value;
        }

        public String toString() {
            return getType() + " " + value;
        }
    }

    //数组初始值
    public static class InitArray extends InitSym {
        private ArrayList<InitSym> elements = new ArrayList<>();

        public InitArray(SymType type) {
            super(type);
        }

        @Override
        public ForDefInit.ForArrayFlatten flatten() {
            ForDefInit.ForArrayFlatten forArrayFlatten = new ForDefInit.ForArrayFlatten();
            for (InitSym initSym : elements) {
                ForDefInit.ForArrayFlatten forArrayFlatten1 = initSym.flatten();
                forArrayFlatten.concat(forArrayFlatten1);
                forArrayFlatten.mergeAll();
            }
            return forArrayFlatten;
        }

        public void addEle(InitSym initValue) {
            this.elements.add(initValue);
        }

        public InitSym getEle(int index) {
            return elements.get(index);
        }

        public int getLen() {
            return elements.size();
        }

        public String toString() {
            StringBuilder ret = new StringBuilder();
            for (InitSym ele : elements) {
                ret.append(ele.toString()).append(", ");
            }
            String r;
            if (elements.isEmpty()) {
                r = "";
            } else {
                r = ret.toString();
                r = r.substring(0, r.length() - 2);
            }
            return getType() + " [" + r + "]";
        }

    }

    //静态分析（前端）期不可求值的初始化值
    public static class InitExp extends InitSym {
        private Value exp;

        public InitExp(SymType type, Value exp) {
            super(type);
            this.exp = exp;
        }

        public ForDefInit.ForArrayFlatten flatten() {
            ForDefInit.ForArrayFlatten forArrayFlatten = new ForDefInit.ForArrayFlatten();
            forArrayFlatten.insertAtTail(new ForDefInit.ForArrayFlatten.Elements(exp, 1));
            return forArrayFlatten;
        }

        public Value getExp() {
            return exp;
        }
    }

}
