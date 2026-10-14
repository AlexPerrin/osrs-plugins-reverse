package java.awt.image;

import java.awt.Image;

/** Android shim of java.awt.image.PixelGrabber for BufferedImages. */
public class PixelGrabber {
    private final Image img;
    private final int x, y, w, h, off, scan;
    private final int[] pix;

    public PixelGrabber(Image img, int x, int y, int w, int h, int[] pix, int off, int scan) {
        this.img = img; this.x = x; this.y = y; this.w = w; this.h = h; this.pix = pix; this.off = off; this.scan = scan;
    }
    public void setColorModel(ColorModel model) {}
    public boolean grabPixels() {
        if (!(img instanceof BufferedImage)) return false;
        ((BufferedImage) img).getRGB(x, y, w, h, pix, off, scan);
        return true;
    }
    public Object getPixels() { return pix; }
}
