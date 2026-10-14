package mir;

import mir.type.SymType;

import java.lang.reflect.Type;

public class VirtualValue extends Value {
    private static int virtual_cnt = 0;

    public VirtualValue(SymType type) {
        super(type);
        pre = "virtual";
        name = "virtual_" + valueNum++;
    }
}
