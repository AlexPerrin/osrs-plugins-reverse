package java.awt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Android shim of java.awt.Font, backed by an android.graphics.Typeface. */
public class Font {
    public static final int PLAIN = 0, BOLD = 1, ITALIC = 2;
    public static final int TRUETYPE_FONT = 0, TYPE1_FONT = 1;
    public static final String DIALOG = "Dialog", DIALOG_INPUT = "DialogInput", SANS_SERIF = "SansSerif",
            SERIF = "Serif", MONOSPACED = "Monospaced";

    protected String name;
    protected int style;
    protected float size;
    private final android.graphics.Typeface typeface;

    public Font(String name, int style, int size) { this(name, style, (float) size, null); }

    private Font(String name, int style, float size, android.graphics.Typeface base) {
        this.name = name == null ? DIALOG : name;
        this.style = style;
        this.size = size;
        android.graphics.Typeface family = base != null ? base : GraphicsEnvironment.typefaceFor(this.name);
        int s = ((style & BOLD) != 0 ? android.graphics.Typeface.BOLD : 0) | ((style & ITALIC) != 0 ? android.graphics.Typeface.ITALIC : 0);
        this.typeface = base != null && s == 0 ? base : android.graphics.Typeface.create(family, s);
    }

    public static Font createFont(int format, InputStream in) throws FontFormatException, IOException {
        File tmp = File.createTempFile("font", ".ttf");
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        try {
            android.graphics.Typeface tf = android.graphics.Typeface.createFromFile(tmp);
            String fname = tmp.getName();
            return new Font(fname, PLAIN, 1f, tf);
        } catch (RuntimeException e) {
            throw new FontFormatException(e.toString());
        } finally {
            tmp.delete();
        }
    }

    public Font deriveFont(float size) { return new Font(name, style, size, typeface); }
    public Font deriveFont(int style) { return new Font(name, style, size, typeface); }
    public Font deriveFont(int style, float size) { return new Font(name, style, size, typeface); }

    public String getName() { return name; }
    public String getFontName() { return name; }
    public String getFamily() { return name; }
    public int getStyle() { return style; }
    public int getSize() { return (int) (size + 0.5f); }
    public float getSize2D() { return size; }
    public boolean isBold() { return (style & BOLD) != 0; }
    public boolean isItalic() { return (style & ITALIC) != 0; }
    public boolean isPlain() { return style == PLAIN; }
    public boolean canDisplay(char c) { return true; }
    /** Host extension. */
    public android.graphics.Typeface getTypeface() { return typeface; }

    void rename(String name) { this.name = name; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Font)) return false;
        Font f = (Font) o;
        return f.name.equals(name) && f.style == style && f.size == size;
    }
    @Override public int hashCode() { return name.hashCode() ^ style ^ Float.floatToIntBits(size); }
    @Override public String toString() { return getClass().getName() + "[name=" + name + ",style=" + style + ",size=" + size + "]"; }
}
