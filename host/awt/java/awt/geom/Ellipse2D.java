package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/** Android shim of java.awt.geom.Ellipse2D. */
public abstract class Ellipse2D implements Shape {
    public static class Float extends Ellipse2D {
        public float x, y, width, height;
        public Float() {}
        public Float(float x, float y, float w, float h) { this.x = x; this.y = y; width = w; height = h; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
    }
    public static class Double extends Ellipse2D {
        public double x, y, width, height;
        public Double() {}
        public Double(double x, double y, double w, double h) { this.x = x; this.y = y; width = w; height = h; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
    }
    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    @Override public boolean contains(double px, double py) {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return false;
        double nx = (px - getX()) / w - 0.5, ny = (py - getY()) / h - 0.5;
        return nx * nx + ny * ny < 0.25;
    }
    @Override public Rectangle getBounds() { return getBounds2D().getBounds(); }
    @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight()); }
    @Override public PathIterator getPathIterator(AffineTransform at) {
        GeneralPath p = new GeneralPath();
        double cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2, rx = getWidth() / 2, ry = getHeight() / 2;
        for (int i = 0; i <= 32; i++) {
            double t = Math.PI * 2 * i / 32;
            if (i == 0) p.moveTo(cx + rx, cy); else p.lineTo(cx + rx * Math.cos(t), cy + ry * Math.sin(t));
        }
        p.closePath();
        return p.getPathIterator(at);
    }
}
