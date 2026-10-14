//扩展数据类型

package mir.type;

import java.util.ArrayList;
import java.util.Objects;

public class SymType {

    public boolean isVoidType() {
        return this instanceof VoidType;
    }

    public boolean isBasicType() {
        return this instanceof BasicType;
    }

    public boolean isIntType() {
        return this == BasicType.Basic_INT;
    }

    public boolean isFloatType() {
        return this == BasicType.Basic_FLOAT;
    }

    public boolean isBoolType() {
        return this == BasicType.Basic_BOOL;
    }

    public boolean isBasicBlockType() {
        return this instanceof BasicBlockType;
    }

    public boolean isArrayType() {
        return this instanceof ArrayType;
    }

    public boolean isPointerType() {
        return this instanceof PointerType;
    }

    public static class VoidType extends SymType {
        private static VoidType voidType = new VoidType();

        public static VoidType getVoidType() {
            return voidType;
        }

        public String toString() {
            return "void";
        }

        public boolean equals(Object o) {
            return o == voidType;
        }
    }

    public static class BasicType extends SymType {
        public DataType dataType;
        public static BasicType Basic_INT = new BasicType(DataType.I32);
        public static BasicType Basic_FLOAT = new BasicType(DataType.FLOAT);
        public static BasicType Basic_Char = new BasicType(DataType.CHAR);
        public static BasicType Basic_BOOL = new BasicType(DataType.BOOL);

        public BasicType(DataType dataType) {
            this.dataType = dataType;
        }

        public String toString() {
            return dataType.toString();
        }

        public boolean equals(Object o) {
            return this == o;
        }

        public static BasicType getBasic_INT() {
            return Basic_INT;
        }

        public static BasicType getBasic_FLOAT() {
            return Basic_FLOAT;
        }

        public static BasicType getBasic_BOOL() {
            return Basic_BOOL;
        }

        public static BasicType getBasic_Char() {
            return Basic_Char;
        }
    }

    public static class BasicBlockType extends SymType{
        private static final BasicBlockType basicBlockType = new BasicBlockType();

        public String toString() {
            return "b ";
        }

        public boolean equals(Object o) {
            return o == basicBlockType;
        }

        public BasicBlockType getBasicBlockType() {
            return basicBlockType;
        }
    }

    public static class ArrayType extends SymType {
        private int size;
        private SymType baseType;
        private BasicType eleType = null;
        //表示数组的基本元素类型，即数组中元素的基本类型。如果baseType是BasicType类的实例，
        // 则eleType等于baseType；如果baseType是ArrayType类的实例，则需要递归获取最内层的基本元素类型。
        private ArrayList<Integer> dims = new ArrayList<>();    //各个维度的长度

        public ArrayType(int size, SymType baseType) {
            this.size = size;
            this.baseType = baseType;
            dims.add(size);
            if (baseType.isArrayType()) {
                dims.addAll(((ArrayType) baseType).dims);
            }
        }

        public String toString() {
            return "[" + size + " x " + baseType + "]";
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ArrayType o1 = (ArrayType) o;
            return o1.size == this.size && this.baseType.equals(o1.baseType);
        }

        public int hashCode() {         //重写 hashCode 方法，保证数组类型的身份标识通过大小和类型区分
            return Objects.hash(size, baseType);
        }

        public int getSize() {
            return size;
        }

        public int getSumSize() {
            if (baseType instanceof BasicType) {
                return size;
            } else {
                return size * ((ArrayType) baseType).getSumSize();
            }
        }

        public SymType getBaseType() {
            return baseType;
        }

        public BasicType getEleType() {
            if (eleType != null) {
                return eleType;
            }
            if (baseType instanceof BasicType) {
                return (BasicType) baseType;
            }
            return ((ArrayType) baseType).getEleType();
        }

        public ArrayList<Integer> getDims() {
            return dims;
        }

        public int getDimSize() {
            return dims.size();
        }
    }

    public static class PointerType extends SymType {
        private SymType pointToType;

        public PointerType(SymType pointToType) {
            this.pointToType = pointToType;
        }

        public String toString() {
            return pointToType + "*";
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PointerType o1 = (PointerType) o;
            return o1.pointToType.equals(pointToType);
        }

        public int hashCode() {
            return Objects.hash(pointToType);
        }

        public SymType getPointToType() {
            return pointToType;
        }
    }

}
