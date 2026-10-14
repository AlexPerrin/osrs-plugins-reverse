package java.awt.image;

import java.awt.RenderingHints;

/** Android shim of java.awt.image.RescaleOp for ARGB images: c' = c * scale + offset per channel. */
public class RescaleOp {
    private final float[] scale, offset;

    public RescaleOp(float[] scale, float[] offset, RenderingHints hints) { this.scale = scale.clone(); this.offset = offset.clone(); }
    public RescaleOp(float scale, float offset, RenderingHints hints) { this(new float[] {scale}, new float[] {offset}, hints); }

    public BufferedImage filter(BufferedImage src, BufferedImage dst) {
        if (dst == null) dst = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        int[] in = src.getPixels(), out = dst.getPixels();
        // One factor applies to the colour channels only; four cover R, G, B, A (Java's order).
        boolean all = scale.length >= 4;
        for (int i = 0; i < in.length; i++) {
            int p = in[i];
            int a = p >>> 24, r = p >> 16 & 0xff, g = p >> 8 & 0xff, b = p & 0xff;
            r = clamp(r * s(0) + o(0)); g = clamp(g * s(1) + o(1)); b = clamp(b * s(2) + o(2));
            if (all) a = clamp(a * s(3) + o(3));
            out[i] = a << 24 | r << 16 | g << 8 | b;
        }
        return dst;
    }

    private float s(int i) { return scale[Math.min(i, scale.length - 1)]; }
    private float o(int i) { return offset[Math.min(i, offset.length - 1)]; }
    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }
}
