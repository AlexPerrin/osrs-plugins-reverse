package java.awt;

/** Android shim of java.awt.Image. */
public abstract class Image {
    public static final int SCALE_DEFAULT = 1, SCALE_FAST = 2, SCALE_SMOOTH = 4;
    public abstract int getWidth(Object observer);
    public abstract int getHeight(Object observer);
    public abstract Graphics getGraphics();
}
