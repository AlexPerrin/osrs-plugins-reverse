package java.awt.image;

/** Android shim of java.awt.image.ColorModel (ARGB only). */
public abstract class ColorModel {
    private final int bits;
    protected ColorModel(int bits) { this.bits = bits; }
    public int getPixelSize() { return bits; }
    public int getNumComponents() { return 4; }
    public boolean hasAlpha() { return true; }
    public boolean isAlphaPremultiplied() { return false; }
    public static ColorModel getRGBdefault() { return new DirectColorModel(32, 0xff0000, 0xff00, 0xff, 0xff000000); }
}
