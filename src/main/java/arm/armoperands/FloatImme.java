package arm.armoperands;

import java.text.DecimalFormat;

public class FloatImme extends ArmOperand {
    // 单纯一个Imme是指的普通立即数
    private float imme;

    public FloatImme(float imme) {
        super(OpType.floatImme);
        this.imme = imme;
    }

    @Override
    public String toString() {
        String s = new DecimalFormat("0.0#############E0").
                format(imme).replace("E", "e");
        return "#" + s;
    }

    public boolean isPreColored() {
        return false;
    }

    public float getImme() {
        return imme;
    }
}
