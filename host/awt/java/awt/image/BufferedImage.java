package java.awt.image;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;

/** Android shim of java.awt.image.BufferedImage: an ARGB int buffer. Drawing into it comes with the Graphics2D shim. */
public class BufferedImage extends Image {
    public static final int TYPE_CUSTOM = 0, TYPE_INT_RGB = 1, TYPE_INT_ARGB = 2, TYPE_INT_ARGB_PRE = 3, TYPE_INT_BGR = 4,
            TYPE_3BYTE_BGR = 5, TYPE_4BYTE_ABGR = 6, TYPE_BYTE_GRAY = 10, TYPE_BYTE_BINARY = 12, TYPE_BYTE_INDEXED = 13;

    private final int width, height, type;
    private final int[] pixels;

    public BufferedImage(int width, int height, int type) {
        this.width = width;
        this.height = height;
        this.type = type;
        this.pixels = new int[width * height];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getType() { return type; }
    @Override public int getWidth(ImageObserver observer) { return width; }
    @Override public int getHeight(ImageObserver observer) { return height; }
    public int getRGB(int x, int y) { return type == TYPE_INT_RGB ? pixels[y * width + x] | 0xff000000 : pixels[y * width + x]; }
    public void setRGB(int x, int y, int argb) { pixels[y * width + x] = argb; }
    public int[] getRGB(int x, int y, int w, int h, int[] out, int off, int scan) {
        if (out == null) out = new int[off + h * scan];
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) out[off + j * scan + i] = getRGB(x + i, y + j);
        return out;
    }
    public void setRGB(int x, int y, int w, int h, int[] in, int off, int scan) {
        for (int j = 0; j < h; j++) System.arraycopy(in, off + j * scan, pixels, (y + j) * width + x, w);
    }
    /** Host extension: a cached android.graphics.Bitmap copy for drawing. */
    public android.graphics.Bitmap toBitmap() {
        return android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888);
    }

    /** Host extension: the backing ARGB pixels (row-major). */
    public int[] getPixels() { return pixels; }
    @Override public Graphics getGraphics() { return createGraphics(); }
    /**
     * Draws into an android.graphics.Bitmap mirror of the pixels; the result is copied back
     * when the Graphics is disposed (net.runelite.host.CanvasGraphics).
     */
    public Graphics2D createGraphics() {
        return net.runelite.host.CanvasGraphics.forImage(this);
    }
}
