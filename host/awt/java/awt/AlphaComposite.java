package java.awt;

/** Android shim of java.awt.AlphaComposite: rule + extra alpha. */
public final class AlphaComposite implements Composite {
    public static final int CLEAR = 1, SRC = 2, SRC_OVER = 3, DST_OVER = 4, SRC_IN = 5, DST_IN = 6, SRC_OUT = 7,
            DST_OUT = 8, DST = 9, SRC_ATOP = 10, DST_ATOP = 11, XOR = 12;
    public static final AlphaComposite Clear = new AlphaComposite(CLEAR, 1f), Src = new AlphaComposite(SRC, 1f),
            SrcOver = new AlphaComposite(SRC_OVER, 1f), SrcIn = new AlphaComposite(SRC_IN, 1f),
            SrcAtop = new AlphaComposite(SRC_ATOP, 1f), DstOver = new AlphaComposite(DST_OVER, 1f),
            DstIn = new AlphaComposite(DST_IN, 1f), DstOut = new AlphaComposite(DST_OUT, 1f);
    private final int rule;
    private final float alpha;

    private AlphaComposite(int rule, float alpha) { this.rule = rule; this.alpha = alpha; }
    public static AlphaComposite getInstance(int rule) { return new AlphaComposite(rule, 1f); }
    public static AlphaComposite getInstance(int rule, float alpha) { return new AlphaComposite(rule, alpha); }
    public AlphaComposite derive(float alpha) { return new AlphaComposite(rule, alpha); }
    public AlphaComposite derive(int rule) { return new AlphaComposite(rule, alpha); }
    public int getRule() { return rule; }
    public float getAlpha() { return alpha; }
}
