package java.awt;

import java.awt.geom.Rectangle2D;

/** Android shim of java.awt.Rectangle. */
public class Rectangle extends Rectangle2D {
    public int x, y, width, height;

    public Rectangle() {}
    public Rectangle(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    public Rectangle(int width, int height) { this(0, 0, width, height); }
    public Rectangle(Point p, Dimension d) { this(p.x, p.y, d.width, d.height); }
    public Rectangle(Rectangle r) { this(r.x, r.y, r.width, r.height); }
    public Rectangle(Dimension d) { this(0, 0, d.width, d.height); }
    public Rectangle(Point p) { this(p.x, p.y, 0, 0); }
    public void add(Point p) { add(p.x, p.y); }
    public boolean contains(Rectangle r) { return contains(r.x, r.y, r.width, r.height); }

    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public double getWidth() { return width; }
    @Override public double getHeight() { return height; }
    @Override public void setRect(double x, double y, double w, double h) {
        this.x = (int) Math.floor(x); this.y = (int) Math.floor(y);
        width = (int) Math.ceil(x + w) - this.x; height = (int) Math.ceil(y + h) - this.y;
    }
    public void setBounds(int x, int y, int w, int h) { this.x = x; this.y = y; width = w; height = h; }
    public void setLocation(int x, int y) { this.x = x; this.y = y; }
    public void setLocation(Point p) { setLocation(p.x, p.y); }
    public void setBounds(Rectangle r) { setBounds(r.x, r.y, r.width, r.height); }
    public void setSize(Dimension d) { setSize(d.width, d.height); }
    public void setSize(int w, int h) { width = w; height = h; }
    public void translate(int dx, int dy) { x += dx; y += dy; }
    public void grow(int h, int v) { x -= h; y -= v; width += 2 * h; height += 2 * v; }
    public Point getLocation() { return new Point(x, y); }
    public Dimension getSize() { return new Dimension(width, height); }
    public boolean contains(int px, int py) { return contains((double) px, (double) py); }
    public boolean contains(Point p) { return contains(p.x, p.y); }
    public boolean intersects(Rectangle r) { return intersects(r.x, r.y, r.width, r.height); }
    public Rectangle union(Rectangle r) {
        int x1 = Math.min(x, r.x), y1 = Math.min(y, r.y);
        return new Rectangle(x1, y1, Math.max(x + width, r.x + r.width) - x1, Math.max(y + height, r.y + r.height) - y1);
    }
    public Rectangle intersection(Rectangle r) {
        int x1 = Math.max(x, r.x), y1 = Math.max(y, r.y);
        return new Rectangle(x1, y1, Math.min(x + width, r.x + r.width) - x1, Math.min(y + height, r.y + r.height) - y1);
    }
    public void add(int px, int py) {
        int x1 = Math.min(x, px), y1 = Math.min(y, py);
        width = Math.max(x + width, px) - x1; height = Math.max(y + height, py) - y1; x = x1; y = y1;
    }
    @Override public Rectangle getBounds() { return new Rectangle(this); }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Rectangle)) return false;
        Rectangle r = (Rectangle) o;
        return x == r.x && y == r.y && width == r.width && height == r.height;
    }
    @Override public int hashCode() { return ((x * 31 + y) * 31 + width) * 31 + height; }
    @Override public String toString() { return getClass().getName() + "[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height + "]"; }
}
