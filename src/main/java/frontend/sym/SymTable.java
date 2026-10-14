//符号表类

package frontend.sym;

import java.util.HashMap;

public class SymTable {
    private HashMap<String, Symbol> symbolHashMap = new HashMap<>();
    private SymTable parent;

    public SymTable() {
        parent = null;
    }

    public SymTable(SymTable parent) {
        this.parent = parent;
    }

    public SymTable getParent() {
        return parent;
    }

    public void addSymbol(Symbol symbol) {
        symbolHashMap.put(symbol.getName(), symbol);
    }

    public Symbol getSymbol(String name, boolean ifFindParents) {
        Symbol symbol = symbolHashMap.get(name);
        if (symbol == null && ifFindParents && parent != null) {
            return parent.getSymbol(name, true);
        }
        return symbol;
    }

    public boolean containSymbol(String name, boolean ifFindParent) {
        return getSymbol(name, ifFindParent) != null;
    }

    public HashMap<String, Symbol> getSymbolHashMap() {
        return symbolHashMap;
    }
}
