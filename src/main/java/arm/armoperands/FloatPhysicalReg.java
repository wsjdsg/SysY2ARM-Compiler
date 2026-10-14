package arm.armoperands;

import java.util.HashMap;
import java.util.Objects;

public class FloatPhysicalReg extends Reg {
    private static HashMap<Integer, String> numToNameMap = new HashMap<>(); // <0, s0>
    private static HashMap<String, Integer> nameToNumMap = new HashMap<>(); // <s0, 0>

    static {
        for (int i = 0; i < 32; i++) {
            // 建立这两个映射
            numToNameMap.put(i, "s" + i);
            nameToNumMap.put("s" + i, i);
        }
    }
    // physical是常规物理寄存器, 这个是浮点物理寄存器

    private String name;
    private int num;

    public FloatPhysicalReg(int num) {
        super(OpType.floatPhy);
        this.name = numToNameMap.get(num);
        this.num = num;
        this.setNeedColor(true);
    }

    public FloatPhysicalReg(String name) {
        super(OpType.floatPhy);
        this.setNeedColor(true);
        this.name = name;
        this.num = nameToNumMap.get(name);
    }

    @Override
    public String toString() {
        return name;
    }

    public boolean isPreColored() {
        return !super.isAllocated();
    }

    public int getNum() {
        return num;
    }

    @Override
    public int hashCode() {
        return Objects.hash(num);
    }

    public boolean needColor() {
        return isPreColored(); // 对于物理来说, 预着色, 就是needColor
    }
}
