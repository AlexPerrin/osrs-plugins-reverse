package java.awt;

/** Android shim of java.awt.FontMetrics, measured with an android.graphics.Paint. */
public class FontMetrics {
    protected Font font;
    private final android.graphics.Paint paint = new android.graphics.Paint();
    private final android.graphics.Paint.FontMetricsInt fm;

    protected FontMetrics(Font font) {
        this.font = font;
        paint.setTypeface(font.getTypeface());
        paint.setTextSize(font.getSize2D());
        fm = paint.getFontMetricsInt();
    }

    /** Host extension: metrics for a font without a Graphics. */
    public static FontMetrics of(Font font) { return new FontMetrics(font); }

    public Font getFont() { return font; }
    public int getAscent() { return -fm.ascent; }
    public int getDescent() { return fm.descent; }
    public int getLeading() { return fm.leading; }
    public int getHeight() { return getAscent() + getDescent() + getLeading(); }
    public int getMaxAscent() { return -fm.top; }
    public int getMaxDescent() { return fm.bottom; }
    public int stringWidth(String s) { return s == null ? 0 : (int) Math.ceil(paint.measureText(s)); }
    public int charWidth(char c) { return stringWidth(String.valueOf(c)); }
    public int charsWidth(char[] data, int off, int len) { return stringWidth(new String(data, off, len)); }
    public java.awt.geom.Rectangle2D getStringBounds(String s, Graphics g) {
        return new java.awt.geom.Rectangle2D.Float(0, -getAscent(), stringWidth(s), getHeight());
    }
}
