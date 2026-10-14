//常量类

package mir;

import mir.type.SymType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class Constant extends Value {
    private static int constNum = 0;
    private int hashId = constNum++;

    public Constant(SymType type) {
        this.type = type;
    }

    public Object getConstValue() {
        return null;
    }

    public int hashCode() {
        return Objects.hash(hashId);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        return ((Constant) o).hashId == this.hashId;
    }

    public static class IntConst extends Constant {
        public static HashMap<Integer, IntConst> intConstHashMap = new HashMap<>();
        public static IntConst CONST_INT_0;
        public static IntConst CONST_INT_1;

        static {
            CONST_INT_0 = new IntConst(0);
            CONST_INT_1 = new IntConst(1);
            intConstHashMap.put(0, CONST_INT_0);
            intConstHashMap.put(1, CONST_INT_1);
        }

        public int intVal;

        public IntConst(int val) {
            super(SymType.BasicType.getBasic_INT());
            this.intVal = val;
        }

        public static IntConst getIntConstByNum(int num) {
            IntConst r = intConstHashMap.get(num);
            if (r == null) {
                r = new IntConst(num);
                intConstHashMap.put(num, r);
            }
            return r;
        }

        public Object getConstValue() {
            return this.intVal;
        }

        @Override
        public String toString() {
            return String.valueOf(this.intVal);
        }

        @Override
        public String getName() {
            return String.valueOf(this.intVal);
        }

        public int hashCode() {
            return intVal;
        }

        public int getIntVal() {
            return intVal;
        }

        public boolean equals(Object o) {
            if (!(o instanceof IntConst)) {
                return false;
            }
            return ((IntConst) o).getIntVal() == intVal;
        }
    }

    public static class FloatConst extends Constant {
        public static HashMap<Float, FloatConst> floatConstHashMap = new HashMap<>();
        public static FloatConst CONST_FLOAT_0;
        public static FloatConst CONST_FLOAT_1;

        static {
            CONST_FLOAT_0 = new FloatConst(0.0F);
            CONST_FLOAT_1 = new FloatConst(1.0F);
            floatConstHashMap.put(0.0F, CONST_FLOAT_0);
            floatConstHashMap.put(1.0F, CONST_FLOAT_1);
        }

        public float floatVal;

        public FloatConst(float val) {
            super(SymType.BasicType.getBasic_FLOAT());
            this.floatVal = val;
        }

        public Object getConstValue() {
            return floatVal;
        }

        public String toString() {
            return String.format("0x%x", Double.doubleToRawLongBits((this.floatVal)));
        }

        @Override
        public String getName() {
            return String.format("0x%x", Double.doubleToRawLongBits((this.floatVal)));
        }

        public int hashCode() {
            return ((int) floatVal) << 1;
        }

        public float getFloatVal() {
            return floatVal;
        }

        public boolean equals(Object o) {
            if (!(o instanceof FloatConst)) {
                return false;
            }
            return Math.abs(((FloatConst) o).getFloatVal() - floatVal) < 1e-6;
        }
    }

    public static class BoolConst extends Constant {
        private int boolVal;

        public BoolConst(int val) {
            super(SymType.BasicType.getBasic_BOOL());
            boolVal = val;
        }

        @Override
        public Object getConstValue() {
            return this.boolVal;
        }

        public String toString() {
            return String.valueOf(this.boolVal);
        }

        public String getName() {
            return String.valueOf(boolVal);
        }

        public int getBoolVal() {
            return boolVal;
        }

        public boolean equals(Object o) {
            if (!(o instanceof BoolConst)) {
                return false;
            } else {
                return ((BoolConst) o).getBoolVal() == boolVal;
            }
        }
    }

    public static class ArrayConst extends Constant {
        private ArrayList<Constant> elements;
        private SymType eleType;

        public ArrayConst(SymType type, SymType eleType, ArrayList<Constant> elements) {
            super(type);
            this.elements = elements;
            this.eleType = eleType;
        }

        public SymType getEleType() {
            return eleType;
        }

        public ArrayList<Constant> getElements() {
            return elements;
        }
    }
}
