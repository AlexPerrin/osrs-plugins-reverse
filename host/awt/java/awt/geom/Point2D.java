package java.awt.geom;

/** Android shim of java.awt.geom.Point2D. */
public abstract class Point2D implements Cloneable {
    public static class Float extends Point2D {
        public float x, y;
        public Float() {}
        public Float(float x, float y) { this.x = x; this.y = y; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public void setLocation(double x, double y) { this.x = (float) x; this.y = (float) y; }
    }

    public static class Double extends Point2D {
        public double x, y;
        public Double() {}
        public Double(double x, double y) { this.x = x; this.y = y; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public void setLocation(double x, double y) { this.x = x; this.y = y; }
    }

    protected Point2D() {}
    public abstract double getX();
    public abstract double getY();
    public abstract void setLocation(double x, double y);
    public void setLocation(Point2D p) { setLocation(p.getX(), p.getY()); }
    public double distance(double px, double py) { return Math.hypot(px - getX(), py - getY()); }
    public double distance(Point2D p) { return distance(p.getX(), p.getY()); }
    public double distanceSq(double px, double py) { double dx = px - getX(), dy = py - getY(); return dx * dx + dy * dy; }
    @Override public Object clone() { try { return super.clone(); } catch (CloneNotSupportedException e) { throw new InternalError(e); } }
    @Override public boolean equals(Object o) {
        return o instanceof Point2D && ((Point2D) o).getX() == getX() && ((Point2D) o).getY() == getY();
    }
    @Override public int hashCode() { return java.lang.Double.hashCode(getX()) * 31 + java.lang.Double.hashCode(getY()); }
    @Override public String toString() { return getClass().getName() + "[" + getX() + ", " + getY() + "]"; }
}
