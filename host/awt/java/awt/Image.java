package java.awt;

/** Android shim of java.awt.Image. */
public abstract class Image {
    public static final int SCALE_DEFAULT = 1, SCALE_FAST = 2, SCALE_SMOOTH = 4;
    public abstract int getWidth(java.awt.image.ImageObserver observer);
    public abstract int getHeight(java.awt.image.ImageObserver observer);
    public abstract Graphics getGraphics();
}
