//基本数据类型

package mir.type;

public enum DataType {
    I32("i32"),
    FLOAT("float"),
    CHAR("i8"),
    BOOL("i1");

    private final String symTypeName;

    DataType(String symTypeName) {
        this.symTypeName = symTypeName;
    }

    public String getTypeName() {
        return this.symTypeName;
    }

    @Override
    public String toString() {
        return symTypeName;
    }
}
