package java.awt.image;

/** Android shim of java.awt.image.ImageObserver. */
public interface ImageObserver {
    int WIDTH = 1, HEIGHT = 2, PROPERTIES = 4, SOMEBITS = 8, FRAMEBITS = 16, ALLBITS = 32, ERROR = 64, ABORT = 128;
    boolean imageUpdate(java.awt.Image img, int infoflags, int x, int y, int width, int height);
}
