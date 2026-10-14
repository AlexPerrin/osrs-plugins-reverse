package java.awt;

/** Android shim of java.awt.BasicStroke (stored; applied by the host's Canvas renderer). */
public class BasicStroke implements Stroke {
    public static final int CAP_BUTT = 0, CAP_ROUND = 1, CAP_SQUARE = 2;
    public static final int JOIN_MITER = 0, JOIN_ROUND = 1, JOIN_BEVEL = 2;
    private final float width, miterLimit, dashPhase;
    private final int cap, join;
    private final float[] dash;

    public BasicStroke() { this(1f); }
    public BasicStroke(float width) { this(width, CAP_SQUARE, JOIN_MITER, 10f, null, 0f); }
    public BasicStroke(float width, int cap, int join) { this(width, cap, join, 10f, null, 0f); }
    public BasicStroke(float width, int cap, int join, float miterLimit) { this(width, cap, join, miterLimit, null, 0f); }
    public BasicStroke(float width, int cap, int join, float miterLimit, float[] dash, float dashPhase) {
        this.width = width; this.cap = cap; this.join = join; this.miterLimit = miterLimit;
        this.dash = dash == null ? null : dash.clone(); this.dashPhase = dashPhase;
    }
    public float getLineWidth() { return width; }
    public int getEndCap() { return cap; }
    public int getLineJoin() { return join; }
    public float getMiterLimit() { return miterLimit; }
    public float[] getDashArray() { return dash == null ? null : dash.clone(); }
    public float getDashPhase() { return dashPhase; }
}
