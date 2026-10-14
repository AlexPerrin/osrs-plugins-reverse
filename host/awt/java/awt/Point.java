package java.awt;

import java.awt.geom.Point2D;

/** Android shim of java.awt.Point. */
public class Point extends Point2D {
    public int x, y;
    public Point() {}
    public Point(int x, int y) { this.x = x; this.y = y; }
    public Point(Point p) { this(p.x, p.y); }
    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public void setLocation(double x, double y) { this.x = (int) Math.floor(x + 0.5); this.y = (int) Math.floor(y + 0.5); }
    public void setLocation(int x, int y) { this.x = x; this.y = y; }
    public void translate(int dx, int dy) { x += dx; y += dy; }
    public Point getLocation() { return new Point(x, y); }
    @Override public String toString() { return getClass().getName() + "[x=" + x + ",y=" + y + "]"; }
}
