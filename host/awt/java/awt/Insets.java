package java.awt;

/** Android shim of java.awt.Insets. */
public class Insets implements Cloneable {
    public int top, left, bottom, right;
    public Insets(int top, int left, int bottom, int right) { this.top = top; this.left = left; this.bottom = bottom; this.right = right; }
}
