package java.awt.geom;

/** Android shim of java.awt.geom.PathIterator. */
public interface PathIterator {
    int WIND_EVEN_ODD = 0, WIND_NON_ZERO = 1;
    int SEG_MOVETO = 0, SEG_LINETO = 1, SEG_QUADTO = 2, SEG_CUBICTO = 3, SEG_CLOSE = 4;
    int getWindingRule();
    boolean isDone();
    void next();
    int currentSegment(float[] coords);
    int currentSegment(double[] coords);
}
