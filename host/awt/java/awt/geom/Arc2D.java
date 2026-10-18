package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/** Android shim of java.awt.geom.Arc2D (angles in degrees, counter-clockwise from 3 o'clock). */
public abstract class Arc2D implements Shape {
    public static final int OPEN = 0, CHORD = 1, PIE = 2;
    private int type;

    public static class Float extends Arc2D {
        public float x, y, width, height, start, extent;
        public Float() { super(OPEN); }
        public Float(int type) { super(type); }
        public Float(float x, float y, float w, float h, float start, float extent, int type) {
            super(type); this.x = x; this.y = y; width = w; height = h; this.start = start; this.extent = extent;
        }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getAngleStart() { return start; }
        @Override public double getAngleExtent() { return extent; }
        @Override public void setArc(double x, double y, double w, double h, double start, double extent, int type) {
            this.x = (float) x; this.y = (float) y; width = (float) w; height = (float) h;
            this.start = (float) start; this.extent = (float) extent; setArcType(type);
        }
        @Override public void setAngleStart(double a) { start = (float) a; }
        @Override public void setAngleExtent(double a) { extent = (float) a; }
    }

    public static class Double extends Arc2D {
        public double x, y, width, height, start, extent;
        public Double() { super(OPEN); }
        public Double(int type) { super(type); }
        public Double(double x, double y, double w, double h, double start, double extent, int type) {
            super(type); this.x = x; this.y = y; width = w; height = h; this.start = start; this.extent = extent;
        }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getAngleStart() { return start; }
        @Override public double getAngleExtent() { return extent; }
        @Override public void setArc(double x, double y, double w, double h, double start, double extent, int type) {
            this.x = x; this.y = y; width = w; height = h; this.start = start; this.extent = extent; setArcType(type);
        }
        @Override public void setAngleStart(double a) { start = a; }
        @Override public void setAngleExtent(double a) { extent = a; }
    }

    protected Arc2D(int type) { this.type = type; }
    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public abstract double getAngleStart();
    public abstract double getAngleExtent();
    public abstract void setArc(double x, double y, double w, double h, double start, double extent, int type);
    public abstract void setAngleStart(double a);
    public abstract void setAngleExtent(double a);
    public void setFrame(double x, double y, double w, double h) {
        setArc(x, y, w, h, getAngleStart(), getAngleExtent(), type);
    }
    /** RectangularShape.setFrame(Rectangle2D) on desktop (Consumable Cooldowns' overlay). */
    public void setFrame(Rectangle2D r) { setFrame(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    public int getArcType() { return type; }
    public void setArcType(int type) { this.type = type; }

    @Override public PathIterator getPathIterator(AffineTransform at) {
        GeneralPath p = new GeneralPath();
        double cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2, rx = getWidth() / 2, ry = getHeight() / 2;
        double a0 = Math.toRadians(getAngleStart()), ext = Math.toRadians(getAngleExtent());
        int n = Math.max(2, (int) Math.ceil(Math.abs(getAngleExtent()) / 10));
        if (type == PIE) p.moveTo(cx, cy);
        for (int i = 0; i <= n; i++) {
            double t = a0 + ext * i / n;
            double px = cx + rx * Math.cos(t), py = cy - ry * Math.sin(t);
            if (i == 0 && type != PIE) p.moveTo(px, py); else p.lineTo(px, py);
        }
        if (type != OPEN) p.closePath();
        return p.getPathIterator(at);
    }
    @Override public boolean contains(double x, double y) { return new GeneralPath(this).contains(x, y); }
    @Override public Rectangle2D getBounds2D() { return new GeneralPath(this).getBounds2D(); }
    @Override public Rectangle getBounds() { return getBounds2D().getBounds(); }
}
