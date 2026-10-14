package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/** Android shim of java.awt.geom.Line2D. */
public abstract class Line2D implements Shape {
    public static class Float extends Line2D {
        public float x1, y1, x2, y2;
        public Float() {}
        public Float(float x1, float y1, float x2, float y2) { this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; }
        @Override public double getX1() { return x1; }
        @Override public double getY1() { return y1; }
        @Override public double getX2() { return x2; }
        @Override public double getY2() { return y2; }
    }
    public static class Double extends Line2D {
        public double x1, y1, x2, y2;
        public Double() {}
        public Double(double x1, double y1, double x2, double y2) { this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; }
        @Override public double getX1() { return x1; }
        @Override public double getY1() { return y1; }
        @Override public double getX2() { return x2; }
        @Override public double getY2() { return y2; }
    }
    public abstract double getX1();
    public abstract double getY1();
    public abstract double getX2();
    public abstract double getY2();
    @Override public boolean contains(double x, double y) { return false; }
    @Override public Rectangle2D getBounds2D() {
        double x = Math.min(getX1(), getX2()), y = Math.min(getY1(), getY2());
        return new Rectangle2D.Double(x, y, Math.abs(getX2() - getX1()), Math.abs(getY2() - getY1()));
    }
    @Override public Rectangle getBounds() { return getBounds2D().getBounds(); }
    @Override public PathIterator getPathIterator(AffineTransform at) {
        GeneralPath p = new GeneralPath();
        p.moveTo(getX1(), getY1());
        p.lineTo(getX2(), getY2());
        return p.getPathIterator(at);
    }
}
