package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/** Android shim of java.awt.Shape (subset). */
public interface Shape {
    Rectangle getBounds();
    Rectangle2D getBounds2D();
    boolean contains(double x, double y);
    default boolean contains(Point2D p) { return contains(p.getX(), p.getY()); }
    default boolean intersects(double x, double y, double w, double h) { return getBounds2D().intersects(x, y, w, h); }
    default boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }
    PathIterator getPathIterator(AffineTransform at);
    default PathIterator getPathIterator(AffineTransform at, double flatness) { return getPathIterator(at); }
}
