package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/** Android shim of java.awt.geom.Rectangle2D. */
public abstract class Rectangle2D implements Shape, Cloneable {
    public static class Float extends Rectangle2D {
        public float x, y, width, height;
        public Float() {}
        public Float(float x, float y, float w, float h) { setRect(x, y, w, h); }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public void setRect(double x, double y, double w, double h) {
            this.x = (float) x; this.y = (float) y; width = (float) w; height = (float) h;
        }
    }

    public static class Double extends Rectangle2D {
        public double x, y, width, height;
        public Double() {}
        public Double(double x, double y, double w, double h) { setRect(x, y, w, h); }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public void setRect(double x, double y, double w, double h) { this.x = x; this.y = y; width = w; height = h; }
    }

    protected Rectangle2D() {}
    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public abstract void setRect(double x, double y, double w, double h);
    public double getMinX() { return getX(); }
    public double getMinY() { return getY(); }
    public double getMaxX() { return getX() + getWidth(); }
    public double getMaxY() { return getY() + getHeight(); }
    public double getCenterX() { return getX() + getWidth() / 2; }
    public double getCenterY() { return getY() + getHeight() / 2; }
    public boolean isEmpty() { return getWidth() <= 0 || getHeight() <= 0; }

    @Override public boolean contains(double px, double py) {
        double x = getX(), y = getY();
        return px >= x && py >= y && px < x + getWidth() && py < y + getHeight();
    }
    @Override public boolean intersects(double x, double y, double w, double h) {
        if (isEmpty() || w <= 0 || h <= 0) return false;
        double x0 = getX(), y0 = getY();
        return x + w > x0 && y + h > y0 && x < x0 + getWidth() && y < y0 + getHeight();
    }
    @Override public Rectangle getBounds() {
        int x1 = (int) Math.floor(getMinX()), y1 = (int) Math.floor(getMinY());
        int x2 = (int) Math.ceil(getMaxX()), y2 = (int) Math.ceil(getMaxY());
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }
    @Override public Rectangle2D getBounds2D() { return (Rectangle2D) clone(); }
    @Override public PathIterator getPathIterator(AffineTransform at) {
        double x = getX(), y = getY(), w = getWidth(), h = getHeight();
        GeneralPath p = new GeneralPath();
        p.moveTo(x, y); p.lineTo(x + w, y); p.lineTo(x + w, y + h); p.lineTo(x, y + h); p.closePath();
        return p.getPathIterator(at);
    }
    @Override public Object clone() { try { return super.clone(); } catch (CloneNotSupportedException e) { throw new InternalError(e); } }
    @Override public String toString() {
        return getClass().getName() + "[x=" + getX() + ",y=" + getY() + ",w=" + getWidth() + ",h=" + getHeight() + "]";
    }
}
