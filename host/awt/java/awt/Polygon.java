package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

/** Android shim of java.awt.Polygon. */
public class Polygon implements Shape {
    public int npoints;
    public int[] xpoints = new int[4], ypoints = new int[4];

    public Polygon() {}
    public Polygon(int[] xs, int[] ys, int n) {
        npoints = n;
        xpoints = Arrays.copyOf(xs, n);
        ypoints = Arrays.copyOf(ys, n);
    }

    public void addPoint(int x, int y) {
        if (npoints == xpoints.length) {
            xpoints = Arrays.copyOf(xpoints, Math.max(4, npoints * 2));
            ypoints = Arrays.copyOf(ypoints, Math.max(4, npoints * 2));
        }
        xpoints[npoints] = x;
        ypoints[npoints++] = y;
    }
    public void reset() { npoints = 0; }
    public void translate(int dx, int dy) { for (int i = 0; i < npoints; i++) { xpoints[i] += dx; ypoints[i] += dy; } }

    @Override public Rectangle getBounds() {
        if (npoints == 0) return new Rectangle();
        int x1 = xpoints[0], y1 = ypoints[0], x2 = x1, y2 = y1;
        for (int i = 1; i < npoints; i++) {
            x1 = Math.min(x1, xpoints[i]); x2 = Math.max(x2, xpoints[i]);
            y1 = Math.min(y1, ypoints[i]); y2 = Math.max(y2, ypoints[i]);
        }
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }
    @Override public Rectangle2D getBounds2D() { return getBounds(); }
    /** Even-odd rule, as java.awt.Polygon. */
    @Override public boolean contains(double x, double y) {
        boolean in = false;
        for (int i = 0, j = npoints - 1; i < npoints; j = i++) {
            if ((ypoints[i] > y) != (ypoints[j] > y)
                    && x < (double) (xpoints[j] - xpoints[i]) * (y - ypoints[i]) / (ypoints[j] - ypoints[i]) + xpoints[i]) {
                in = !in;
            }
        }
        return in;
    }
    public boolean contains(int x, int y) { return contains((double) x, (double) y); }
    public boolean contains(Point p) { return contains(p.x, p.y); }
    @Override public PathIterator getPathIterator(AffineTransform at) {
        GeneralPath p = new GeneralPath(PathIterator.WIND_EVEN_ODD);
        for (int i = 0; i < npoints; i++) {
            if (i == 0) p.moveTo((double) xpoints[i], (double) ypoints[i]); else p.lineTo((double) xpoints[i], (double) ypoints[i]);
        }
        if (npoints > 0) p.closePath();
        return p.getPathIterator(at);
    }
}
