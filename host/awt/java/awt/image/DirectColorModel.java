package java.awt.image;

/** Android shim of java.awt.image.DirectColorModel. */
public class DirectColorModel extends ColorModel {
    public DirectColorModel(int bits, int rmask, int gmask, int bmask) { super(bits); }
    public DirectColorModel(int bits, int rmask, int gmask, int bmask, int amask) { super(bits); }
}
