package java.awt.image;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;

/** Android shim of java.awt.image.BufferedImage: an ARGB int buffer. Drawing into it comes with the Graphics2D shim. */
public class BufferedImage extends Image implements RenderedImage {
    public static final int TYPE_CUSTOM = 0, TYPE_INT_RGB = 1, TYPE_INT_ARGB = 2, TYPE_INT_ARGB_PRE = 3, TYPE_INT_BGR = 4,
            TYPE_3BYTE_BGR = 5, TYPE_4BYTE_ABGR = 6, TYPE_BYTE_GRAY = 10, TYPE_BYTE_BINARY = 12, TYPE_BYTE_INDEXED = 13;

    private final int width, height, type;
    private final int[] pixels;

    public BufferedImage(int width, int height, int type) {
        this.width = width;
        this.height = height;
        this.type = type;
        this.pixels = new int[width * height];
        if (type == TYPE_INT_RGB) java.util.Arrays.fill(pixels, 0xff000000);  // desktop: no alpha, starts black
    }

    /** Over a raster from getRaster/copyData (shares its pixels, as on desktop); other rasters give 1x1. */
    public BufferedImage(ColorModel cm, WritableRaster raster, boolean premultiplied, java.util.Hashtable<?, ?> properties) {
        HostRaster r = raster instanceof HostRaster ? (HostRaster) raster : new HostRaster(1, 1, new int[1]);
        this.width = r.width;
        this.height = r.height;
        this.type = TYPE_INT_ARGB;
        this.pixels = r.pixels;
    }

    public WritableRaster getRaster() { return new HostRaster(width, height, pixels); }

    /** Copies the pixels into a new raster (the `out` argument is ignored unless it is null). */
    public WritableRaster copyData(WritableRaster out) {
        if (out instanceof HostRaster && ((HostRaster) out).pixels.length == pixels.length) {
            System.arraycopy(pixels, 0, ((HostRaster) out).pixels, 0, pixels.length);
            return out;
        }
        return new HostRaster(width, height, pixels.clone());
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getType() { return type; }
    @Override public int getWidth(ImageObserver observer) { return width; }
    @Override public int getHeight(ImageObserver observer) { return height; }
    public int getRGB(int x, int y) { return type == TYPE_INT_RGB ? pixels[y * width + x] | 0xff000000 : pixels[y * width + x]; }
    /** TYPE_INT_RGB has no alpha on desktop: what is stored reads back (and draws) opaque. */
    public void setRGB(int x, int y, int argb) { pixels[y * width + x] = type == TYPE_INT_RGB ? argb | 0xff000000 : argb; }
    public int[] getRGB(int x, int y, int w, int h, int[] out, int off, int scan) {
        if (out == null) out = new int[off + h * scan];
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) out[off + j * scan + i] = getRGB(x + i, y + j);
        return out;
    }
    public void setRGB(int x, int y, int w, int h, int[] in, int off, int scan) {
        for (int j = 0; j < h; j++) System.arraycopy(in, off + j * scan, pixels, (y + j) * width + x, w);
        if (type == TYPE_INT_RGB)
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) pixels[(y + j) * width + x + i] |= 0xff000000;
    }
    public BufferedImage getSubimage(int x, int y, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, type);
        for (int j = 0; j < h; j++) System.arraycopy(pixels, (y + j) * width + x, out.pixels, j * w, w);
        return out;
    }

    public ColorModel getColorModel() { return new DirectColorModel(32, 0xff0000, 0xff00, 0xff, 0xff000000); }

    public Image getScaledInstance(int w, int h, int hints) {
        if (w < 0) w = h * width / Math.max(1, height);
        if (h < 0) h = w * height / Math.max(1, width);
        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(toBitmap(), Math.max(1, w), Math.max(1, h),
                (hints & (SCALE_SMOOTH | SCALE_DEFAULT)) != 0);
        return fromBitmap(scaled);
    }

    /** Host extension: copies an android.graphics.Bitmap into a new TYPE_INT_ARGB image. */
    public static BufferedImage fromBitmap(android.graphics.Bitmap b) {
        BufferedImage img = new BufferedImage(b.getWidth(), b.getHeight(), TYPE_INT_ARGB);
        b.getPixels(img.pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return img;
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
