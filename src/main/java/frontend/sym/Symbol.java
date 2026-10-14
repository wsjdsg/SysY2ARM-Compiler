//符号类

package frontend.sym;

import mir.Value;
import mir.type.SymType;

public class Symbol {
    private String name;
    private boolean isConst;
    private SymType type;
    private InitSym initValue = null;

    private Value pointer;

    public Symbol(String name, boolean isConst, SymType type, InitSym initValue, Value pointer) {
        this.name = name;
        this.isConst = isConst;
        this.type = type;
        this.initValue = initValue;
        this.pointer = pointer;
    }

    public String getName() {
        return name;
    }

    public boolean getIsConst() {
        return isConst;
    }

    public SymType getType() {
        return type;
    }

    public InitSym getInitValue() {
        return initValue;
    }

    public Value getPointer() {
        return pointer;
    }
}
