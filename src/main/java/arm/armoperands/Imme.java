package arm.armoperands;

public class Imme extends ArmOperand {
    private int imme; // 立即数的值

    public Imme(int imme) {
        super(OpType.imme);
        this.imme = imme;
    }

    public boolean isPreColored() {
        return false;
    }

    public String toString() {
        return "#" + imme;
    }

    public int getImme() {
        return this.imme;
    }
}
