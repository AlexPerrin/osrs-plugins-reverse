package java.awt;

import java.awt.image.ImageObserver;

/** Android shim of java.awt.Graphics. Implemented by net.runelite.host.CanvasGraphics. */
public abstract class Graphics {
    protected Graphics() {}

    public abstract Graphics create();
    public Graphics create(int x, int y, int w, int h) {
        Graphics g = create();
        g.translate(x, y);
        g.clipRect(0, 0, w, h);
        return g;
    }
    public abstract void dispose();
    public abstract void translate(int x, int y);
    public abstract Color getColor();
    public abstract void setColor(Color c);
    public abstract Font getFont();
    public abstract void setFont(Font font);
    public FontMetrics getFontMetrics() { return getFontMetrics(getFont()); }
    public abstract FontMetrics getFontMetrics(Font f);
    public void setPaintMode() {}
    public void setXORMode(Color c) {}

    public abstract Rectangle getClipBounds();
    public abstract void clipRect(int x, int y, int w, int h);
    public abstract void setClip(int x, int y, int w, int h);
    public abstract Shape getClip();
    public abstract void setClip(Shape clip);

    public abstract void drawLine(int x1, int y1, int x2, int y2);
    public abstract void fillRect(int x, int y, int w, int h);
    public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) return;
        drawLine(x, y, x + w, y); drawLine(x + w, y, x + w, y + h);
        drawLine(x + w, y + h, x, y + h); drawLine(x, y + h, x, y);
    }
    public abstract void clearRect(int x, int y, int w, int h);
    public abstract void drawRoundRect(int x, int y, int w, int h, int arcW, int arcH);
    public abstract void fillRoundRect(int x, int y, int w, int h, int arcW, int arcH);
    public abstract void drawOval(int x, int y, int w, int h);
    public abstract void fillOval(int x, int y, int w, int h);
    public abstract void drawArc(int x, int y, int w, int h, int start, int extent);
    public abstract void fillArc(int x, int y, int w, int h, int start, int extent);
    public abstract void drawPolyline(int[] xs, int[] ys, int n);
    public abstract void drawPolygon(int[] xs, int[] ys, int n);
    public void drawPolygon(Polygon p) { drawPolygon(p.xpoints, p.ypoints, p.npoints); }
    public abstract void fillPolygon(int[] xs, int[] ys, int n);
    public void fillPolygon(Polygon p) { fillPolygon(p.xpoints, p.ypoints, p.npoints); }
    public abstract void drawString(String s, int x, int y);
    public void drawChars(char[] data, int off, int len, int x, int y) { drawString(new String(data, off, len), x, y); }

    public abstract boolean drawImage(Image img, int x, int y, ImageObserver observer);
    public abstract boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver observer);
    public boolean drawImage(Image img, int x, int y, Color bg, ImageObserver observer) { return drawImage(img, x, y, observer); }
    public abstract boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
                                      ImageObserver observer);
}
