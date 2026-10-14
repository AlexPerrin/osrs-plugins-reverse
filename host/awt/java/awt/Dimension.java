package java.awt;

/** Android shim of java.awt.Dimension. */
public class Dimension implements Cloneable {
    public int width, height;
    public Dimension() {}
    public Dimension(int width, int height) { this.width = width; this.height = height; }
    public Dimension(Dimension d) { this(d.width, d.height); }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public void setSize(int w, int h) { width = w; height = h; }
    public Dimension getSize() { return new Dimension(this); }
    @Override public boolean equals(Object o) { return o instanceof Dimension && ((Dimension) o).width == width && ((Dimension) o).height == height; }
    @Override public int hashCode() { return width * 31 + height; }
    @Override public String toString() { return getClass().getName() + "[width=" + width + ",height=" + height + "]"; }
}
