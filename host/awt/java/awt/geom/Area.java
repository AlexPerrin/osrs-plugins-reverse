package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android shim of java.awt.geom.Area for rectilinear regions (what plugins build: unions,
 * differences and intersections of Rectangles, as Wilderness Lines, NPC Unaggro Area and the
 * world-map clip areas do).
 *
 * The region is a grid over the distinct x and y edge coordinates of the shapes it was built
 * from, with one filled flag per cell; boolean operations merge the two grids. Its outline
 * ({@link #getPathIterator}) is traced from the cell edges into closed loops (counter-clockwise
 * around filled cells in y-up terms), with collinear edges merged.
 *
 * A non-rectangular shape (polygon, ellipse) is kept exact while the Area holds only that
 * shape; once combined, its cells are classified by the shape's contains() at each cell centre
 * over a grid of its vertex coordinates, so slanted or curved edges become staircases.
 * // unverified: the staircase approximation for non-rectilinear shapes (no plugin seen using it).
 */
public class Area implements Shape, Cloneable {
    private double[] xs = new double[0], ys = new double[0];
    private boolean[] cells = new boolean[0];   // (xs.length-1) * (ys.length-1), row-major by y
    private Shape exact;                        // a lone non-rectangular shape, kept as given

    public Area() {}

    public Area(Shape s) {
        if (s instanceof Area) { set((Area) s); return; }
        if (s instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) s;
            if (r.getWidth() > 0 && r.getHeight() > 0) {
                xs = new double[]{r.getMinX(), r.getMaxX()};
                ys = new double[]{r.getMinY(), r.getMaxY()};
                cells = new boolean[]{true};
            }
            return;
        }
        double[] c = new double[6];
        List<Double> vx = new ArrayList<>(), vy = new ArrayList<>();
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            int n = t == PathIterator.SEG_CLOSE ? 0 : t == PathIterator.SEG_QUADTO ? 4 : t == PathIterator.SEG_CUBICTO ? 6 : 2;
            for (int i = 0; i < n; i += 2) { vx.add(c[i]); vy.add(c[i + 1]); }
        }
        xs = uniq(vx);
        ys = uniq(vy);
        int nx = xs.length - 1, ny = ys.length - 1;
        if (nx <= 0 || ny <= 0) { xs = ys = new double[0]; cells = new boolean[0]; return; }
        cells = new boolean[nx * ny];
        boolean rect = true;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                boolean in = s.contains((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2);
                cells[j * nx + i] = in;
                rect &= in;
            }
        if (!rect || !isRectilinear(s)) exact = s;
    }

    private static boolean isRectilinear(Shape s) {
        double[] c = new double[6];
        double sx = 0, sy = 0, lx = 0, ly = 0;
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            double x, y;
            if (t == PathIterator.SEG_MOVETO) { sx = lx = c[0]; sy = ly = c[1]; continue; }
            if (t == PathIterator.SEG_LINETO) { x = c[0]; y = c[1]; }
            else if (t == PathIterator.SEG_CLOSE) { x = sx; y = sy; }
            else return false;
            if (x != lx && y != ly) return false;
            lx = x; ly = y;
        }
        return true;
    }

    private static double[] uniq(List<Double> v) {
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        return uniq(a, a.length);
    }

    private static double[] uniq(double[] a, int n) {
        a = Arrays.copyOf(a, n);
        Arrays.sort(a);
        int k = 0;
        for (int i = 0; i < n; i++) if (k == 0 || a[i] != a[k - 1]) a[k++] = a[i];
        return Arrays.copyOf(a, k);
    }

    private static double[] merge(double[] a, double[] b) {
        double[] m = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, m, a.length, b.length);
        return uniq(m, m.length);
    }

    private void set(Area o) {
        xs = o.xs.clone(); ys = o.ys.clone(); cells = o.cells.clone(); exact = o.exact;
    }

    /** Cell index along an axis containing v (half-open [a[i], a[i+1])), or -1. */
    private static int find(double[] a, double v) {
        if (a.length < 2 || v < a[0] || v >= a[a.length - 1]) return -1;
        int i = Arrays.binarySearch(a, v);
        return i >= 0 ? i : -i - 2;
    }

    private boolean filledAt(double x, double y) {
        int i = find(xs, x), j = find(ys, y);
        return i >= 0 && j >= 0 && cells[j * (xs.length - 1) + i];
    }

    /** For each cell of the finer axis {@code f}, the index of the cell of {@code c} (a subset of its lines) holding it, or -1. */
    private static int[] cellMap(double[] f, double[] c) {
        int[] m = new int[Math.max(0, f.length - 1)];
        int k = -1;
        for (int i = 0; i < m.length; i++) {
            while (k + 1 < c.length && c[k + 1] <= f[i]) k++;
            m[i] = k >= 0 && k < c.length - 1 ? k : -1;
        }
        return m;
    }

    private static final int OR = 0, MINUS = 1, AND = 2, XOR = 3;

    private void combine(Area o, int op) {
        if (o == this) o = (Area) o.clone();
        double[] nxs = merge(xs, o.xs), nys = merge(ys, o.ys);
        int nx = nxs.length - 1, ny = nys.length - 1;
        boolean[] nc = new boolean[Math.max(0, nx) * Math.max(0, ny)];
        boolean any = false;
        int[] ax = cellMap(nxs, xs), ay = cellMap(nys, ys), bx = cellMap(nxs, o.xs), by = cellMap(nys, o.ys);
        int anx = xs.length - 1, bnx = o.xs.length - 1;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                boolean a = ax[i] >= 0 && ay[j] >= 0 && cells[ay[j] * anx + ax[i]];
                boolean b = bx[i] >= 0 && by[j] >= 0 && o.cells[by[j] * bnx + bx[i]];
                boolean r = op == OR ? a | b : op == MINUS ? a & !b : op == AND ? a & b : a ^ b;
                nc[j * nx + i] = r;
                any |= r;
            }
        }
        exact = null;
        if (!any) { reset(); return; }
        xs = nxs; ys = nys; cells = nc;
        compact();
    }

    /** Drops grid lines that no longer separate cells of different fill. */
    private void compact() {
        int nx = xs.length - 1, ny = ys.length - 1;
        boolean[] keepX = new boolean[xs.length], keepY = new boolean[ys.length];
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j < ny && !keepX[i]; j++) {
                boolean l = i > 0 && cells[j * nx + i - 1], r = i < nx && cells[j * nx + i];
                keepX[i] = l != r;
            }
        }
        for (int j = 0; j <= ny; j++) {
            for (int i = 0; i < nx && !keepY[j]; i++) {
                boolean d = j > 0 && cells[(j - 1) * nx + i], u = j < ny && cells[j * nx + i];
                keepY[j] = d != u;
            }
        }
        int[] mx = new int[xs.length], my = new int[ys.length];
        int kx = 0, ky = 0;
        double[] nxs = new double[xs.length], nys = new double[ys.length];
        for (int i = 0; i <= nx; i++) if (keepX[i]) nxs[kx++] = xs[i];
        for (int j = 0; j <= ny; j++) if (keepY[j]) nys[ky++] = ys[j];
        if (kx == xs.length && ky == ys.length) return;
        double[] fx = Arrays.copyOf(nxs, kx), fy = Arrays.copyOf(nys, ky);
        boolean[] nc = new boolean[Math.max(0, kx - 1) * Math.max(0, ky - 1)];
        for (int j = 0; j < ky - 1; j++)
            for (int i = 0; i < kx - 1; i++)
                nc[j * (kx - 1) + i] = filledAt((fx[i] + fx[i + 1]) / 2, (fy[j] + fy[j + 1]) / 2);
        xs = fx; ys = fy; cells = nc;
    }

    public void add(Area o) {
        if (o.isEmpty()) return;
        if (isEmpty()) { set(o); return; }
        combine(o, OR);
    }
    public void subtract(Area o) { if (!isEmpty() && !o.isEmpty()) combine(o, MINUS); }
    public void intersect(Area o) { if (isEmpty()) return; if (o.isEmpty()) { reset(); return; } combine(o, AND); }
    public void exclusiveOr(Area o) {
        if (o.isEmpty()) return;
        if (isEmpty()) { set(o); return; }
        combine(o, XOR);
    }
    public void reset() { xs = ys = new double[0]; cells = new boolean[0]; exact = null; }

    public boolean isEmpty() {
        if (exact != null) return false;
        for (boolean c : cells) if (c) return false;
        return true;
    }
    public boolean isPolygonal() { return exact == null || isRectilinear(exact); }
    public boolean isRectangular() {
        if (exact != null) return false;
        if (isEmpty()) return true;
        return xs.length == 2 && ys.length == 2;
    }
    public boolean isSingular() {
        PathIterator it = getPathIterator(null);
        double[] c = new double[6];
        int moves = 0;
        for (; !it.isDone(); it.next()) if (it.currentSegment(c) == PathIterator.SEG_MOVETO) moves++;
        return moves <= 1;
    }

    @Override public Rectangle2D getBounds2D() {
        if (exact != null) return exact.getBounds2D();
        int nx = xs.length - 1, ny = ys.length - 1;
        double x1 = java.lang.Double.MAX_VALUE, y1 = x1, x2 = -x1, y2 = -x1;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                if (cells[j * nx + i]) {
                    x1 = Math.min(x1, xs[i]); x2 = Math.max(x2, xs[i + 1]);
                    y1 = Math.min(y1, ys[j]); y2 = Math.max(y2, ys[j + 1]);
                }
        if (x1 > x2) return new Rectangle2D.Double();
        return new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1);
    }
    @Override public Rectangle getBounds() { return getBounds2D().getBounds(); }

    @Override public boolean contains(double x, double y) {
        return exact != null ? exact.contains(x, y) : filledAt(x, y);
    }
    @Override public boolean contains(double x, double y, double w, double h) {
        if (exact != null) return exact.contains(x, y, w, h);
        if (w <= 0 || h <= 0) return false;
        Area r = new Area(new Rectangle2D.Double(x, y, w, h));
        r.subtract(this);
        return r.isEmpty();
    }
    @Override public boolean intersects(double x, double y, double w, double h) {
        if (exact != null) return exact.intersects(x, y, w, h);
        if (w <= 0 || h <= 0) return false;
        Area r = new Area(new Rectangle2D.Double(x, y, w, h));
        r.intersect(this);
        return !r.isEmpty();
    }

    @Override public boolean contains(Point2D p) { return contains(p.getX(), p.getY()); }
    @Override public boolean contains(Rectangle2D r) { return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    @Override public boolean intersects(Rectangle2D r) { return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    @Override public PathIterator getPathIterator(AffineTransform at) {
        if (exact != null) return exact.getPathIterator(at);
        return outline().getPathIterator(at);
    }
    @Override public PathIterator getPathIterator(AffineTransform at, double flatness) { return getPathIterator(at); }

    /** Closed loops around the filled cells, collinear edges merged. */
    private GeneralPath outline() {
        GeneralPath p = new GeneralPath(PathIterator.WIND_NON_ZERO);
        int nx = xs.length - 1, ny = ys.length - 1;
        if (nx <= 0 || ny <= 0) return p;
        int W = xs.length;
        // Directed edges between grid vertices (vertex id = j * W + i), filled cell on the left.
        Map<Integer, List<Integer>> out = new HashMap<>();
        int edges = 0;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!cells[j * nx + i]) continue;
                int v00 = j * W + i, v10 = v00 + 1, v01 = v00 + W, v11 = v01 + 1;
                if (j == 0 || !cells[(j - 1) * nx + i]) { edge(out, v00, v10); edges++; }
                if (i == nx - 1 || !cells[j * nx + i + 1]) { edge(out, v10, v11); edges++; }
                if (j == ny - 1 || !cells[(j + 1) * nx + i]) { edge(out, v11, v01); edges++; }
                if (i == 0 || !cells[j * nx + i - 1]) { edge(out, v01, v00); edges++; }
            }
        while (edges > 0) {
            int start = -1;
            for (Map.Entry<Integer, List<Integer>> e : out.entrySet()) if (!e.getValue().isEmpty()) { start = e.getKey(); break; }
            List<Integer> loop = new ArrayList<>();
            int v = start;
            do {
                loop.add(v);
                List<Integer> next = out.get(v);
                v = next.remove(next.size() - 1);
                edges--;
            } while (v != start);
            // Drop vertices in the middle of straight runs.
            List<Integer> corners = new ArrayList<>();
            int n = loop.size();
            for (int k = 0; k < n; k++) {
                int a = loop.get((k + n - 1) % n), b = loop.get(k), c = loop.get((k + 1) % n);
                boolean sameX = a % W == b % W && b % W == c % W, sameY = a / W == b / W && b / W == c / W;
                if (!sameX && !sameY) corners.add(b);
            }
            for (int k = 0; k < corners.size(); k++) {
                int c = corners.get(k);
                double x = xs[c % W], y = ys[c / W];
                if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.closePath();
        }
        return p;
    }

    private static void edge(Map<Integer, List<Integer>> out, int from, int to) {
        List<Integer> l = out.get(from);
        if (l == null) out.put(from, l = new ArrayList<>(2));
        l.add(to);
    }

    public Area createTransformedArea(AffineTransform t) {
        Area a = (Area) clone();
        a.transform(t);
        return a;
    }

    public void transform(AffineTransform t) {
        if (t == null || t.isIdentity() || isEmpty()) return;
        if (exact == null && t.getShearX() == 0 && t.getShearY() == 0 && t.getScaleX() > 0 && t.getScaleY() > 0) {
            for (int i = 0; i < xs.length; i++) xs[i] = xs[i] * t.getScaleX() + t.getTranslateX();
            for (int j = 0; j < ys.length; j++) ys[j] = ys[j] * t.getScaleY() + t.getTranslateY();
            return;
        }
        Shape s = t.createTransformedShape(exact != null ? exact : outline());
        Area a = new Area(s);
        set(a);
    }

    public boolean equals(Area o) {
        if (o == this) return true;
        if (o == null) return false;
        Area x = (Area) clone();
        x.exclusiveOr(o);
        return x.isEmpty();
    }

    @Override public Object clone() {
        Area a = new Area();
        a.set(this);
        return a;
    }
}
