package arm.armoperands;

import java.util.HashMap;
import java.util.Objects;

public class PhysicalReg extends Reg {
    private static HashMap<Integer, String> numToNameMap = new HashMap<>();
    private static HashMap<String, Integer> nameToNumMap = new HashMap<>();

    static { // key只能出现一次, 所以13号不能映射到两个寄存器名r13和sp
        numToNameMap.put(0, "r0");
        numToNameMap.put(1, "r1");
        numToNameMap.put(2, "r2");
        numToNameMap.put(3, "r3");
        numToNameMap.put(4, "r4");
        numToNameMap.put(5, "r5");
        numToNameMap.put(6, "r6");
        numToNameMap.put(7, "r7");
        numToNameMap.put(8, "r8");
        numToNameMap.put(9, "r9");
        numToNameMap.put(10, "r10");
        numToNameMap.put(11, "r11");
        numToNameMap.put(12, "r12");
        numToNameMap.put(13, "sp");
        numToNameMap.put(14, "lr");
        numToNameMap.put(15, "pc");
        numToNameMap.put(16, "cspr");
        // -------------------------
        // -------------------------
        nameToNumMap.put("r0", 0);
        nameToNumMap.put("r1", 1);
        nameToNumMap.put("r2", 2);
        nameToNumMap.put("r3", 3);
        nameToNumMap.put("r4", 4);
        nameToNumMap.put("r5", 5);
        nameToNumMap.put("r6", 6);
        nameToNumMap.put("r7", 7);
        nameToNumMap.put("r8", 8);
        nameToNumMap.put("r9", 9);
        nameToNumMap.put("r10", 10);
        nameToNumMap.put("r11", 11);
        nameToNumMap.put("r12", 12);
        nameToNumMap.put("r13", 13);
        nameToNumMap.put("r14", 14);
        nameToNumMap.put("r15", 15);
        // 下面这些寄存器是对应物理寄存器的别名
        nameToNumMap.put("fp", 11);
        nameToNumMap.put("ip", 12);
        nameToNumMap.put("sp", 13);
        nameToNumMap.put("lr", 14);
        nameToNumMap.put("pc", 15);
    }

    private String name;
    private int num; // 寄存器编号

    public PhysicalReg(int num) {
        super(OpType.phy);
        this.num = num;
        this.name = numToNameMap.get(num);
        this.setNeedColor(true);
    }

    public PhysicalReg(int num, boolean isAllocated) {
        super(OpType.phy);
        this.num = num;
        this.name = numToNameMap.get(num);
        this.setNeedColor(true);
        this.setAllocated(isAllocated);
    }

    public PhysicalReg(String regName) {
        super(OpType.phy);
        this.name = regName;
        this.setNeedColor(true);
        this.num = nameToNumMap.get(regName);
    }

    public boolean needColor() {
        return isPreColored(); // 对于物理来说, 预着色, 就是needColor
    }

    @Override
    public boolean isPreColored() {
        return !super.isAllocated();
    }

    public String getName() {
        return name;
    }

    public int getNum() {
        return num;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(num);
    }


//    @Override
//    public boolean equals(Object obj) {
//        if (!(obj instanceof PhysicalReg)) {
//            return false;
//        }
//        return ((PhysicalReg) obj).getName().equals(this.getName());
//    }
}
