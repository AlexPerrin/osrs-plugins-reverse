package net.runelite.host;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Map;

/**
 * java.awt.Graphics2D drawing onto an android.graphics.Canvas. Each instance is one
 * Graphics state (colour, font, stroke, transform, clip); create() makes a child that
 * restores the canvas when disposed, matching Swing's create/dispose pattern.
 */
public final class CanvasGraphics extends Graphics2D {
    private static final Font DEFAULT_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);

    private final Canvas canvas;
    private final int saveCount;
    private final Runnable onDispose;
    private final Paint fill = new Paint(), stroke = new Paint(), text = new Paint();
    private Color color = Color.WHITE, background = Color.BLACK;
    private Font font = DEFAULT_FONT;
    private Stroke strokeSpec = new BasicStroke(1f);
    private Composite composite = AlphaComposite.SrcOver;
    private final RenderingHints hints = new RenderingHints(null);
    private AffineTransform transform = new AffineTransform();
    private Shape clip;
    private boolean disposed;

    public CanvasGraphics(Canvas canvas) {
        this(canvas, null);
    }

    private CanvasGraphics(Canvas canvas, Runnable onDispose) {
        this.canvas = canvas;
        this.saveCount = canvas.save();
        this.onDispose = onDispose;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        text.setStyle(Paint.Style.FILL);
        applyColor();
        applyStroke();
        applyFont();
        setAntialias(false);
    }

    /**
     * Graphics for a BufferedImage: draws into the image's shared mutable Bitmap (BufferedImage.liveBitmap).
     * As on desktop, what is drawn is in the image at once, disposed or not: each draw marks the image
     * dirty and its pixel reads copy the Bitmap back first (Quick Prayer Preview never disposes).
     */
    public static CanvasGraphics forImage(BufferedImage img) {
        return new CanvasGraphics(new ImageCanvas(img), img::syncLive);
    }

    /** A Canvas over an image's live Bitmap that reloads it before each draw and marks the image dirty after. */
    private static final class ImageCanvas extends Canvas {
        private final BufferedImage img;

        ImageCanvas(BufferedImage img) {
            super(img.liveBitmap());
            this.img = img;
        }

        private void before() { img.beforeLiveDraw(); }
        private void after() { img.afterLiveDraw(); }

        @Override public void drawPath(Path p, Paint paint) { before(); super.drawPath(p, paint); after(); }
        @Override public void drawLine(float x1, float y1, float x2, float y2, Paint paint) {
            before(); super.drawLine(x1, y1, x2, y2, paint); after();
        }
        @Override public void drawRect(float l, float t, float r, float b, Paint paint) {
            before(); super.drawRect(l, t, r, b, paint); after();
        }
        @Override public void drawRoundRect(RectF r, float rx, float ry, Paint paint) {
            before(); super.drawRoundRect(r, rx, ry, paint); after();
        }
        @Override public void drawOval(RectF r, Paint paint) { before(); super.drawOval(r, paint); after(); }
        @Override public void drawArc(RectF r, float start, float sweep, boolean center, Paint paint) {
            before(); super.drawArc(r, start, sweep, center, paint); after();
        }
        @Override public void drawText(String s, float x, float y, Paint paint) { before(); super.drawText(s, x, y, paint); after(); }
        @Override public void drawBitmap(Bitmap b, float x, float y, Paint paint) { before(); super.drawBitmap(b, x, y, paint); after(); }
        @Override public void drawBitmap(Bitmap b, android.graphics.Rect src, RectF dst, Paint paint) {
            before(); super.drawBitmap(b, src, dst, paint); after();
        }
        @Override public void drawBitmap(Bitmap b, Matrix m, Paint paint) { before(); super.drawBitmap(b, m, paint); after(); }
    }

    private CanvasGraphics copyStateTo(CanvasGraphics g) {
        g.color = color;
        g.background = background;
        g.font = font;
        g.strokeSpec = strokeSpec;
        g.composite = composite;
        g.hints.putAll(hints);
        g.transform = new AffineTransform(transform);
        g.clip = clip;
        g.applyColor();
        g.applyStroke();
        g.applyFont();
        g.setAntialias(VALUE_ON(hints.get(RenderingHints.KEY_ANTIALIASING)));
        return g;
    }

    private static boolean VALUE_ON(Object v) {
        return v == RenderingHints.VALUE_ANTIALIAS_ON;
    }

    // --- state ---

    private int argb() {
        int c = color.getRGB();
        if (composite instanceof AlphaComposite) {
            float a = ((AlphaComposite) composite).getAlpha();
            if (a < 1f) c = (Math.round((c >>> 24) * a) << 24) | (c & 0xffffff);
        }
        return c;
    }

    private void applyColor() {
        int c = argb();
        fill.setColor(c);
        stroke.setColor(c);
        text.setColor(c);
        PorterDuffXfermode mode = null;
        if (composite instanceof AlphaComposite) {
            int rule = ((AlphaComposite) composite).getRule();
            if (rule == AlphaComposite.CLEAR) mode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
            else if (rule == AlphaComposite.SRC) mode = new PorterDuffXfermode(PorterDuff.Mode.SRC);
        }
        fill.setXfermode(mode);
        stroke.setXfermode(mode);
        text.setXfermode(mode);
    }

    private void applyStroke() {
        if (!(strokeSpec instanceof BasicStroke)) return;
        BasicStroke s = (BasicStroke) strokeSpec;
        stroke.setStrokeWidth(s.getLineWidth());
        stroke.setStrokeCap(s.getEndCap() == BasicStroke.CAP_ROUND ? Paint.Cap.ROUND
                : s.getEndCap() == BasicStroke.CAP_BUTT ? Paint.Cap.BUTT : Paint.Cap.SQUARE);
        stroke.setStrokeJoin(s.getLineJoin() == BasicStroke.JOIN_ROUND ? Paint.Join.ROUND
                : s.getLineJoin() == BasicStroke.JOIN_BEVEL ? Paint.Join.BEVEL : Paint.Join.MITER);
        stroke.setStrokeMiter(s.getMiterLimit());
        float[] dash = s.getDashArray();
        stroke.setPathEffect(dash != null && dash.length >= 2 ? new DashPathEffect(dash, s.getDashPhase()) : null);
    }

    private void applyFont() {
        text.setTypeface(font.getTypeface());
        text.setTextSize(font.getSize2D());
    }

    private void setAntialias(boolean on) {
        fill.setAntiAlias(on);
        stroke.setAntiAlias(on);
    }

    private void applyMatrix() {
        canvas.restoreToCount(saveCount);
        canvas.save();
        Matrix m = new Matrix();
        m.setValues(new float[] {
            (float) transform.getScaleX(), (float) transform.getShearX(), (float) transform.getTranslateX(),
            (float) transform.getShearY(), (float) transform.getScaleY(), (float) transform.getTranslateY(),
            0, 0, 1});
        canvas.concat(m);
        if (clip != null) canvas.clipPath(toPath(clip));
    }

    @Override public Graphics create() {
        CanvasGraphics g = copyStateTo(new CanvasGraphics(canvas, null));
        g.applyMatrix();
        return g;
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        canvas.restoreToCount(saveCount);
        if (onDispose != null) onDispose.run();
    }

    @Override public Color getColor() { return color; }
    @Override public void setColor(Color c) { if (c != null) { color = c; applyColor(); } }
    @Override public void setPaint(java.awt.Paint p) { if (p instanceof Color) setColor((Color) p); }
    @Override public java.awt.Paint getPaint() { return color; }
    @Override public Font getFont() { return font; }
    @Override public void setFont(Font f) { if (f != null) { font = f; applyFont(); } }
    @Override public FontMetrics getFontMetrics(Font f) { return FontMetrics.of(f); }
    @Override public void setStroke(Stroke s) { if (s != null) { strokeSpec = s; applyStroke(); } }
    @Override public Stroke getStroke() { return strokeSpec; }
    @Override public void setComposite(Composite c) { if (c != null) { composite = c; applyColor(); } }
    @Override public Composite getComposite() { return composite; }
    @Override public void setBackground(Color c) { background = c; }
    @Override public Color getBackground() { return background; }

    @Override public void setRenderingHint(RenderingHints.Key key, Object value) {
        hints.put(key, value);
        if (key == RenderingHints.KEY_ANTIALIASING) setAntialias(VALUE_ON(value));
        if (key == RenderingHints.KEY_TEXT_ANTIALIASING) text.setAntiAlias(value != RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }
    @Override public Object getRenderingHint(RenderingHints.Key key) { return hints.get(key); }
    @Override public void setRenderingHints(Map<?, ?> m) { hints.clear(); addRenderingHints(m); }
    @Override public void addRenderingHints(Map<?, ?> m) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() instanceof RenderingHints.Key) setRenderingHint((RenderingHints.Key) e.getKey(), e.getValue());
        }
    }
    @Override public RenderingHints getRenderingHints() { return new RenderingHints(null) {{ putAll(hints); }}; }

    // --- transform and clip (clip is kept in user space at the time it was set) ---

    @Override public void translate(int x, int y) { translate((double) x, (double) y); }
    @Override public void translate(double tx, double ty) { transform.translate(tx, ty); applyMatrix(); }
    @Override public void rotate(double theta) { transform.rotate(theta); applyMatrix(); }
    @Override public void rotate(double theta, double x, double y) { transform.rotate(theta, x, y); applyMatrix(); }
    @Override public void scale(double sx, double sy) { transform.scale(sx, sy); applyMatrix(); }
    @Override public void shear(double shx, double shy) {
        transform.concatenate(new AffineTransform(1, shy, shx, 1, 0, 0));
        applyMatrix();
    }
    @Override public void transform(AffineTransform t) { transform.concatenate(t); applyMatrix(); }
    @Override public void setTransform(AffineTransform t) { transform = new AffineTransform(t); applyMatrix(); }
    @Override public AffineTransform getTransform() { return new AffineTransform(transform); }

    @Override public Rectangle getClipBounds() { return clip == null ? null : clip.getBounds(); }
    @Override public Shape getClip() { return clip; }
    @Override public void clipRect(int x, int y, int w, int h) { clip(new Rectangle(x, y, w, h)); }
    @Override public void setClip(int x, int y, int w, int h) { setClip(new Rectangle(x, y, w, h)); }
    @Override public void setClip(Shape s) { clip = s; applyMatrix(); }
    @Override public void clip(Shape s) {
        if (clip == null) {
            clip = s;
        } else {
            Rectangle a = clip.getBounds(), b = s.getBounds();
            clip = a.intersection(b);
        }
        applyMatrix();
    }

    // --- drawing ---

    static Path toPath(Shape s) {
        Path p = new Path();
        PathIterator it = s.getPathIterator(null);
        p.setFillType(it.getWindingRule() == PathIterator.WIND_EVEN_ODD ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
        float[] c = new float[6];
        for (; !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO: p.moveTo(c[0], c[1]); break;
                case PathIterator.SEG_LINETO: p.lineTo(c[0], c[1]); break;
                case PathIterator.SEG_QUADTO: p.quadTo(c[0], c[1], c[2], c[3]); break;
                case PathIterator.SEG_CUBICTO: p.cubicTo(c[0], c[1], c[2], c[3], c[4], c[5]); break;
                case PathIterator.SEG_CLOSE: p.close(); break;
            }
        }
        return p;
    }

    private static Path polygon(int[] xs, int[] ys, int n, boolean close) {
        Path p = new Path();
        for (int i = 0; i < n; i++) {
            if (i == 0) p.moveTo(xs[i], ys[i]); else p.lineTo(xs[i], ys[i]);
        }
        if (close && n > 0) p.close();
        return p;
    }

    // Java2D strokes the pixel centres of integer coordinates; shift by half a pixel.
    private void strokePath(Path p) {
        canvas.save();
        canvas.translate(0.5f, 0.5f);
        canvas.drawPath(p, stroke);
        canvas.restore();
    }

    @Override public void draw(Shape s) { strokePath(toPath(s)); }
    @Override public void fill(Shape s) { canvas.drawPath(toPath(s), fill); }
    @Override public void drawLine(int x1, int y1, int x2, int y2) {
        canvas.drawLine(x1 + 0.5f, y1 + 0.5f, x2 + 0.5f, y2 + 0.5f, stroke);
    }
    @Override public void fillRect(int x, int y, int w, int h) { canvas.drawRect(x, y, x + w, y + h, fill); }
    @Override public void drawRect(int x, int y, int w, int h) {
        canvas.drawRect(x + 0.5f, y + 0.5f, x + w + 0.5f, y + h + 0.5f, stroke);
    }
    @Override public void clearRect(int x, int y, int w, int h) {
        Paint p = new Paint();
        p.setColor(background.getRGB());
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        canvas.drawRect(x, y, x + w, y + h, p);
    }
    @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        canvas.drawRoundRect(new RectF(x + 0.5f, y + 0.5f, x + w + 0.5f, y + h + 0.5f), aw / 2f, ah / 2f, stroke);
    }
    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), aw / 2f, ah / 2f, fill);
    }
    @Override public void drawOval(int x, int y, int w, int h) {
        canvas.drawOval(new RectF(x + 0.5f, y + 0.5f, x + w + 0.5f, y + h + 0.5f), stroke);
    }
    @Override public void fillOval(int x, int y, int w, int h) { canvas.drawOval(new RectF(x, y, x + w, y + h), fill); }
    // Java2D angles are counter-clockwise; Android's are clockwise.
    @Override public void drawArc(int x, int y, int w, int h, int start, int extent) {
        canvas.drawArc(new RectF(x + 0.5f, y + 0.5f, x + w + 0.5f, y + h + 0.5f), -start, -extent, false, stroke);
    }
    @Override public void fillArc(int x, int y, int w, int h, int start, int extent) {
        canvas.drawArc(new RectF(x, y, x + w, y + h), -start, -extent, true, fill);
    }
    @Override public void drawPolyline(int[] xs, int[] ys, int n) { strokePath(polygon(xs, ys, n, false)); }
    @Override public void drawPolygon(int[] xs, int[] ys, int n) { strokePath(polygon(xs, ys, n, true)); }
    @Override public void fillPolygon(int[] xs, int[] ys, int n) {
        Path p = polygon(xs, ys, n, true);
        p.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(p, fill);
    }
    @Override public void drawString(String s, int x, int y) { if (s != null) canvas.drawText(s, x, y, text); }
    @Override public void drawString(String s, float x, float y) { if (s != null) canvas.drawText(s, x, y, text); }

    private static Bitmap bitmapOf(Image img) {
        return img instanceof BufferedImage ? ((BufferedImage) img).toBitmap() : null;
    }

    private Paint imagePaint() {
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (composite instanceof AlphaComposite) p.setAlpha(Math.round(255 * ((AlphaComposite) composite).getAlpha()));
        return p;
    }

    @Override public boolean drawImage(Image img, int x, int y, ImageObserver o) {
        Bitmap b = bitmapOf(img);
        if (b != null) canvas.drawBitmap(b, x, y, imagePaint());
        return true;
    }
    @Override public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver o) {
        Bitmap b = bitmapOf(img);
        if (b != null) canvas.drawBitmap(b, null, new RectF(x, y, x + w, y + h), imagePaint());
        return true;
    }
    @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
                                       ImageObserver o) {
        Bitmap b = bitmapOf(img);
        if (b != null) {
            canvas.drawBitmap(b, new android.graphics.Rect(sx1, sy1, sx2, sy2), new RectF(dx1, dy1, dx2, dy2), imagePaint());
        }
        return true;
    }
    @Override public boolean drawImage(Image img, AffineTransform t, ImageObserver o) {
        Bitmap b = bitmapOf(img);
        if (b == null) return true;
        Matrix m = new Matrix();
        m.setValues(new float[] {
            (float) t.getScaleX(), (float) t.getShearX(), (float) t.getTranslateX(),
            (float) t.getShearY(), (float) t.getScaleY(), (float) t.getTranslateY(), 0, 0, 1});
        canvas.drawBitmap(b, m, imagePaint());
        return true;
    }
}
