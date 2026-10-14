package net.runelite.host;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws registered overlays into a Bitmap on the game thread and hands the pixels to
 * libosrsrl, which composites them over the game in its eglSwapBuffers hook.
 *
 * Plugins see a canvas of (screen / SCALE) pixels, close to the desktop client's
 * resizable mode; the bitmap is that size and the GPU scales it up.
 */
final class OverlayRenderer {
    private static final float TARGET_HEIGHT = 720f;  // logical canvas height plugins draw on
    private static final int BORDER = 5, PADDING = 2;
    private static final OverlayLayer[] LAYERS = {
        OverlayLayer.UNDER_WIDGETS, OverlayLayer.ABOVE_SCENE, OverlayLayer.ABOVE_WIDGETS, OverlayLayer.ALWAYS_ON_TOP};

    private final OverlayManager overlays;
    private Bitmap bitmap;
    private Canvas canvas;
    private boolean published;
    private int frame;

    OverlayRenderer(OverlayManager overlays) {
        this.overlays = overlays;
    }

    /** Logical canvas size plugins draw on. */
    static Dimension canvasSize() {
        int[] s = Native.screenSize();
        if (s[0] <= 0 || s[1] <= 0) return new Dimension(0, 0);
        float scale = Math.max(1f, s[1] / TARGET_HEIGHT);
        return new Dimension(Math.round(s[0] / scale), Math.round(s[1] / scale));
    }

    void render() {
        // 25 Hz is plenty for overlays and halves the copy cost.
        if ((frame++ & 1) != 0) return;
        if (overlays.isEmpty()) {
            if (published) Native.overlayPublish(0, 0);
            published = false;
            return;
        }
        Dimension size = canvasSize();
        if (size.width <= 0) return;
        if (bitmap == null || bitmap.getWidth() != size.width || bitmap.getHeight() != size.height) {
            bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }
        bitmap.eraseColor(0);
        CanvasGraphics root = new CanvasGraphics(canvas);
        root.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (OverlayLayer layer : LAYERS) renderLayer(root, layer, size);
        root.dispose();

        ByteBuffer buf = Native.overlayBuffer(size.width, size.height);
        if (buf == null) return;
        bitmap.copyPixelsToBuffer(buf);
        Native.overlayPublish(size.width, size.height);
        published = true;
    }

    private void renderLayer(CanvasGraphics root, OverlayLayer layer, Dimension size) {
        Map<OverlayPosition, Point> cursor = new EnumMap<>(OverlayPosition.class);
        for (Overlay overlay : overlays.getLayer(layer)) {
            OverlayPosition pos = overlay.getPreferredPosition() != null ? overlay.getPreferredPosition() : overlay.getPosition();
            Rectangle bounds = overlay.getBounds();
            Point at;
            switch (pos) {
                case DYNAMIC:
                case TOOLTIP:
                    at = new Point(0, 0);
                    break;
                case DETACHED:
                    at = overlay.getPreferredLocation() != null ? overlay.getPreferredLocation() : new Point(BORDER, BORDER);
                    break;
                default:
                    at = snap(cursor, pos, bounds, size);
                    break;
            }
            CanvasGraphics g = (CanvasGraphics) root.create();
            try {
                g.translate(at.x, at.y);
                g.setFont(net.runelite.client.ui.FontManager.getRunescapeFont());
                Dimension d = overlay.render(g);
                if (d != null) {
                    overlay.setBounds(new Rectangle(at.x, at.y, d.width, d.height));
                    advance(cursor, pos, d);
                }
            } catch (Throwable t) {
                Log.e(Host.TAG, "overlay " + overlay.getName() + " threw", t);
            } finally {
                g.dispose();
            }
        }
    }

    // Snap corners stack overlays away from the corner. Right/bottom anchored positions use
    // last frame's size, as RuneLite does.
    private static Point snap(Map<OverlayPosition, Point> cursor, OverlayPosition pos, Rectangle last, Dimension size) {
        Point c = cursor.get(pos);
        if (c == null) {
            switch (pos) {
                case TOP_RIGHT:
                case CANVAS_TOP_RIGHT: c = new Point(size.width - BORDER, BORDER); break;
                case TOP_CENTER: c = new Point(size.width / 2, BORDER); break;
                case BOTTOM_LEFT: c = new Point(BORDER, size.height - BORDER); break;
                case BOTTOM_RIGHT:
                case ABOVE_CHATBOX_RIGHT: c = new Point(size.width - BORDER, size.height - BORDER); break;
                default: c = new Point(BORDER, BORDER); break;
            }
            cursor.put(pos, c);
        }
        int w = last != null ? last.width : 0, h = last != null ? last.height : 0;
        switch (pos) {
            case TOP_RIGHT:
            case CANVAS_TOP_RIGHT: return new Point(c.x - w, c.y);
            case TOP_CENTER: return new Point(c.x - w / 2, c.y);
            case BOTTOM_LEFT: return new Point(c.x, c.y - h);
            case BOTTOM_RIGHT:
            case ABOVE_CHATBOX_RIGHT: return new Point(c.x - w, c.y - h);
            default: return new Point(c.x, c.y);
        }
    }

    private static void advance(Map<OverlayPosition, Point> cursor, OverlayPosition pos, Dimension d) {
        Point c = cursor.get(pos);
        if (c == null) return;
        switch (pos) {
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
            case ABOVE_CHATBOX_RIGHT: c.y -= d.height + PADDING; break;
            default: c.y += d.height + PADDING; break;
        }
    }
}
