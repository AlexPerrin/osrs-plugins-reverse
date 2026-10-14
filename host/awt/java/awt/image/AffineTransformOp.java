package java.awt.image;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/** Android shim of java.awt.image.AffineTransformOp, drawn through the Canvas renderer. */
public class AffineTransformOp {
    public static final int TYPE_NEAREST_NEIGHBOR = 1, TYPE_BILINEAR = 2, TYPE_BICUBIC = 3;
    private final AffineTransform xform;

    public AffineTransformOp(AffineTransform xform, int type) { this.xform = new AffineTransform(xform); }

    public BufferedImage filter(BufferedImage src, BufferedImage dst) {
        if (dst == null) {
            java.awt.Rectangle b = xform.createTransformedShape(new java.awt.Rectangle(0, 0, src.getWidth(), src.getHeight())).getBounds();
            dst = new BufferedImage(Math.max(1, b.x + b.width), Math.max(1, b.y + b.height), BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, xform, null);
        g.dispose();
        return dst;
    }
}
