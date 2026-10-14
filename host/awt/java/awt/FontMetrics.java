package java.awt;

/** Android shim of java.awt.FontMetrics. */
public abstract class FontMetrics {
    protected FontMetrics() {}
    public abstract int getHeight();
    public abstract int getAscent();
    public abstract int getDescent();
    public abstract int stringWidth(String s);
}
