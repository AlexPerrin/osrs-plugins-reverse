package java.awt.geom;

/** Android shim of java.awt.geom.AffineTransform: [m00 m01 m02; m10 m11 m12]. */
public class AffineTransform implements Cloneable {
    double m00 = 1, m10, m01, m11 = 1, m02, m12;

    public AffineTransform() {}
    public AffineTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        setTransform(m00, m10, m01, m11, m02, m12);
    }
    public AffineTransform(AffineTransform t) { setTransform(t.m00, t.m10, t.m01, t.m11, t.m02, t.m12); }

    public static AffineTransform getTranslateInstance(double tx, double ty) { return new AffineTransform(1, 0, 0, 1, tx, ty); }
    public static AffineTransform getScaleInstance(double sx, double sy) { return new AffineTransform(sx, 0, 0, sy, 0, 0); }
    public static AffineTransform getRotateInstance(double theta) {
        double c = Math.cos(theta), s = Math.sin(theta);
        return new AffineTransform(c, s, -s, c, 0, 0);
    }

    public void setTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        this.m00 = m00; this.m10 = m10; this.m01 = m01; this.m11 = m11; this.m02 = m02; this.m12 = m12;
    }
    public void setToIdentity() { setTransform(1, 0, 0, 1, 0, 0); }
    public boolean isIdentity() { return m00 == 1 && m10 == 0 && m01 == 0 && m11 == 1 && m02 == 0 && m12 == 0; }
    public double getScaleX() { return m00; }
    public double getScaleY() { return m11; }
    public double getShearX() { return m01; }
    public double getShearY() { return m10; }
    public double getTranslateX() { return m02; }
    public double getTranslateY() { return m12; }

    /** this = this * t */
    public void concatenate(AffineTransform t) {
        setTransform(m00 * t.m00 + m01 * t.m10, m10 * t.m00 + m11 * t.m10,
                     m00 * t.m01 + m01 * t.m11, m10 * t.m01 + m11 * t.m11,
                     m00 * t.m02 + m01 * t.m12 + m02, m10 * t.m02 + m11 * t.m12 + m12);
    }
    public void translate(double tx, double ty) { concatenate(getTranslateInstance(tx, ty)); }
    public void scale(double sx, double sy) { concatenate(getScaleInstance(sx, sy)); }
    public void rotate(double theta) { concatenate(getRotateInstance(theta)); }
    public void rotate(double vecx, double vecy) { rotate(Math.atan2(vecy, vecx)); }
    public void rotate(double theta, double x, double y) { translate(x, y); rotate(theta); translate(-x, -y); }

    public Point2D transform(Point2D src, Point2D dst) {
        if (dst == null) dst = new Point2D.Double();
        double x = src.getX(), y = src.getY();
        dst.setLocation(m00 * x + m01 * y + m02, m10 * x + m11 * y + m12);
        return dst;
    }
    public void transform(double[] src, int so, double[] dst, int d0, int n) {
        for (int i = 0; i < n; i++) {
            double x = src[so + 2 * i], y = src[so + 2 * i + 1];
            dst[d0 + 2 * i] = m00 * x + m01 * y + m02;
            dst[d0 + 2 * i + 1] = m10 * x + m11 * y + m12;
        }
    }
    public void transform(float[] src, int so, float[] dst, int d0, int n) {
        for (int i = 0; i < n; i++) {
            float x = src[so + 2 * i], y = src[so + 2 * i + 1];
            dst[d0 + 2 * i] = (float) (m00 * x + m01 * y + m02);
            dst[d0 + 2 * i + 1] = (float) (m10 * x + m11 * y + m12);
        }
    }
    public java.awt.Shape createTransformedShape(java.awt.Shape s) {
        GeneralPath p = new GeneralPath();
        p.append(s.getPathIterator(this), false);
        return p;
    }
    @Override public Object clone() { return new AffineTransform(this); }
}
