package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Arrays;

/** Android shim of java.awt.geom.GeneralPath (Path2D.Float in the JDK). Curves are kept as segments. */
public class GeneralPath implements Shape, Cloneable {
    private byte[] types = new byte[16];
    private double[] coords = new double[32];
    private int ntypes, ncoords;
    private int windingRule = PathIterator.WIND_NON_ZERO;

    public GeneralPath() {}
    public GeneralPath(int rule) { windingRule = rule; }
    public GeneralPath(Shape s) { append(s.getPathIterator(null), false); }

    private void add(int type, double... c) {
        if (ntypes == types.length) types = Arrays.copyOf(types, ntypes * 2);
        if (ncoords + c.length > coords.length) coords = Arrays.copyOf(coords, Math.max(coords.length * 2, ncoords + c.length));
        types[ntypes++] = (byte) type;
        System.arraycopy(c, 0, coords, ncoords, c.length);
        ncoords += c.length;
    }

    public void moveTo(double x, double y) { add(PathIterator.SEG_MOVETO, x, y); }
    public void lineTo(double x, double y) { add(PathIterator.SEG_LINETO, x, y); }
    public void quadTo(double x1, double y1, double x2, double y2) { add(PathIterator.SEG_QUADTO, x1, y1, x2, y2); }
    public void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        add(PathIterator.SEG_CUBICTO, x1, y1, x2, y2, x3, y3);
    }
    public void moveTo(float x, float y) { moveTo((double) x, (double) y); }
    public void lineTo(float x, float y) { lineTo((double) x, (double) y); }
    public void closePath() { add(PathIterator.SEG_CLOSE); }
    public void reset() { ntypes = ncoords = 0; }
    public int getWindingRule() { return windingRule; }
    public void setWindingRule(int rule) { windingRule = rule; }

    public void append(Shape s, boolean connect) { append(s.getPathIterator(null), connect); }
    public void append(PathIterator it, boolean connect) {
        double[] c = new double[6];
        for (; !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            int n = t == PathIterator.SEG_CLOSE ? 0 : t == PathIterator.SEG_QUADTO ? 4 : t == PathIterator.SEG_CUBICTO ? 6 : 2;
            if (t == PathIterator.SEG_MOVETO && connect && ntypes > 0) t = PathIterator.SEG_LINETO;
            connect = false;
            add(t, Arrays.copyOf(c, n));
        }
    }

    @Override public Rectangle2D getBounds2D() {
        if (ncoords == 0) return new Rectangle2D.Double();
        double x1 = coords[0], y1 = coords[1], x2 = x1, y2 = y1;
        for (int i = 2; i < ncoords; i += 2) {
            x1 = Math.min(x1, coords[i]); x2 = Math.max(x2, coords[i]);
            y1 = Math.min(y1, coords[i + 1]); y2 = Math.max(y2, coords[i + 1]);
        }
        return new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1);
    }
    @Override public Rectangle getBounds() { return getBounds2D().getBounds(); }

    /** Even-odd / non-zero test on the polygon formed by segment end points. */
    @Override public boolean contains(double px, double py) {
        int winding = 0;
        double sx = 0, sy = 0, lx = 0, ly = 0;
        int ci = 0;
        for (int i = 0; i < ntypes; i++) {
            int t = types[i];
            double x = lx, y = ly;
            switch (t) {
                case PathIterator.SEG_MOVETO:
                    winding += cross(lx, ly, sx, sy, px, py);
                    sx = lx = coords[ci]; sy = ly = coords[ci + 1]; ci += 2; continue;
                case PathIterator.SEG_LINETO: x = coords[ci]; y = coords[ci + 1]; ci += 2; break;
                case PathIterator.SEG_QUADTO: x = coords[ci + 2]; y = coords[ci + 3]; ci += 4; break;
                case PathIterator.SEG_CUBICTO: x = coords[ci + 4]; y = coords[ci + 5]; ci += 6; break;
                case PathIterator.SEG_CLOSE: x = sx; y = sy; break;
            }
            winding += cross(lx, ly, x, y, px, py);
            lx = x; ly = y;
        }
        winding += cross(lx, ly, sx, sy, px, py);
        return windingRule == PathIterator.WIND_EVEN_ODD ? (winding & 1) != 0 : winding != 0;
    }

    private static int cross(double x0, double y0, double x1, double y1, double px, double py) {
        if (y0 <= py) {
            if (y1 > py && (x1 - x0) * (py - y0) - (px - x0) * (y1 - y0) > 0) return 1;
        } else if (y1 <= py && (x1 - x0) * (py - y0) - (px - x0) * (y1 - y0) < 0) {
            return -1;
        }
        return 0;
    }

    @Override public PathIterator getPathIterator(AffineTransform at) {
        return new PathIterator() {
            int ti, ci;
            @Override public int getWindingRule() { return windingRule; }
            @Override public boolean isDone() { return ti >= ntypes; }
            @Override public void next() {
                int t = types[ti++];
                ci += t == SEG_CLOSE ? 0 : t == SEG_QUADTO ? 4 : t == SEG_CUBICTO ? 6 : 2;
            }
            @Override public int currentSegment(double[] out) {
                int t = types[ti];
                int n = t == SEG_CLOSE ? 0 : t == SEG_QUADTO ? 4 : t == SEG_CUBICTO ? 6 : 2;
                if (at != null) at.transform(coords, ci, out, 0, n / 2);
                else System.arraycopy(coords, ci, out, 0, n);
                return t;
            }
            @Override public int currentSegment(float[] out) {
                double[] d = new double[6];
                int t = currentSegment(d);
                for (int i = 0; i < 6; i++) out[i] = (float) d[i];
                return t;
            }
        };
    }
    @Override public Object clone() { GeneralPath p = new GeneralPath(windingRule); p.append(this, false); return p; }
}
