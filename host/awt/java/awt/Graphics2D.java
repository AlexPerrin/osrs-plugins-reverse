package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;
import java.util.Map;

/** Android shim of java.awt.Graphics2D. Implemented by net.runelite.host.CanvasGraphics. */
public abstract class Graphics2D extends Graphics {
    protected Graphics2D() {}

    public abstract void draw(Shape s);
    public abstract void fill(Shape s);
    public abstract void drawString(String s, float x, float y);
    public abstract boolean drawImage(Image img, AffineTransform xform, ImageObserver obs);

    public abstract void setStroke(Stroke s);
    public abstract Stroke getStroke();
    public abstract void setPaint(Paint p);
    public abstract Paint getPaint();
    public abstract void setComposite(Composite c);
    public abstract Composite getComposite();
    public abstract void setBackground(Color c);
    public abstract Color getBackground();

    public abstract void setRenderingHint(RenderingHints.Key key, Object value);
    public abstract Object getRenderingHint(RenderingHints.Key key);
    public abstract void setRenderingHints(Map<?, ?> hints);
    public abstract void addRenderingHints(Map<?, ?> hints);
    public abstract RenderingHints getRenderingHints();

    public abstract void translate(double tx, double ty);
    public abstract void rotate(double theta);
    public abstract void rotate(double theta, double x, double y);
    public abstract void scale(double sx, double sy);
    public abstract void shear(double shx, double shy);
    public abstract void transform(AffineTransform t);
    public abstract void setTransform(AffineTransform t);
    public abstract AffineTransform getTransform();
    public abstract void clip(Shape s);
}
