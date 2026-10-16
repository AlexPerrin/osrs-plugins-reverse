package net.runelite.host;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws registered overlays into a Bitmap on the game thread and hands the pixels to
 * libosrsrl, which composites them over the game in its eglSwapBuffers hook.
 *
 * Plugins see the game's UI canvas (canvasSize()); the bitmap is that size and the GPU scales
 * it up to the screen.
 */
final class OverlayRenderer {
    private static final float FALLBACK_HEIGHT = 503f;
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

    /**
     * Canvas size plugins draw on: the game's UI canvas (size of the root interface, 894x503 on a
     * 1920x1080 screen), so widget coordinates, overlays and Perspective share one space as on
     * desktop. Falls back to a 503-unit-high canvas before the root interface exists.
     */
    static Dimension canvasSize() {
        int root = Native.rootInterface();
        if (root >= 0) {
            int[] g = Native.widgetGeometry(root << 16);
            if (g != null && g[2] > 0 && g[3] > 0) return new Dimension(g[2], g[3]);
        }
        int[] s = Native.screenSize();
        if (s[0] <= 0 || s[1] <= 0) return new Dimension(0, 0);
        float scale = s[1] / FALLBACK_HEIGHT;
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
        List<Rect> under = uiExclusions();
        // The game draws its long-press menu natively on top of everything; no overlay may cover it.
        java.awt.Rectangle menu = AndroidMenu.bounds();
        Rect menuRect = menu == null ? null : new Rect(menu.x, menu.y, menu.x + menu.width, menu.y + menu.height);
        try {
            for (OverlayLayer layer : LAYERS) {
                // Scene layers go under the game's opaque UI panels, as RuneLite draws them before
                // widgets. The layer's root Graphics is created inside the clip: CanvasGraphics
                // restores the canvas to its own save count, which would otherwise drop the clip.
                boolean scene = layer == OverlayLayer.UNDER_WIDGETS || layer == OverlayLayer.ABOVE_SCENE;
                int save = canvas.save();
                if (scene) for (Rect r : under) canvas.clipOutRect(r);
                if (menuRect != null) canvas.clipOutRect(menuRect);
                CanvasGraphics root = new CanvasGraphics(canvas);
                root.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                net.runelite.client.ui.overlay.outline.ModelOutlineRenderer.target = root;
                try {
                    renderLayer(root, layer, size);
                } finally {
                    root.dispose();
                    canvas.restoreToCount(save);
                }
            }
        } finally {
            net.runelite.client.ui.overlay.outline.ModelOutlineRenderer.target = null;
        }

        ByteBuffer buf = Native.overlayBuffer(size.width, size.height);
        if (buf == null) return;
        bitmap.copyPixelsToBuffer(buf);
        Native.overlayPublish(size.width, size.height);
        published = true;
    }

    /** Android root (601) children that are opaque panels: side tab stones, minimap block, side panel. */
    private static final int[] OPAQUE_ROOT_CHILDREN = {50, 36, 111};  // + open side panel (inventory etc.)
    private int exclusionsCycle = Integer.MIN_VALUE;
    private List<Rect> exclusions = new ArrayList<>();

    /** UI-canvas rectangles scene overlays must not cover; refreshed every 25 cycles. */
    private List<Rect> uiExclusions() {
        int cycle = Native.gameCycle();
        if (cycle - exclusionsCycle < 25 && cycle >= exclusionsCycle) return exclusions;
        exclusionsCycle = cycle;
        List<Rect> out = new ArrayList<>();
        int root = Native.rootInterface();
        if (root == 601) {
            List<Integer> ids = new ArrayList<>();
            for (int i : OPAQUE_ROOT_CHILDREN) ids.add(i);
            for (String t : Native.property("debug.osrsrl.clipui").split(",")) {  // experiments
                try { ids.add(Integer.parseInt(t.trim())); } catch (NumberFormatException ignored) { }
            }
            for (int child : ids) {
                AndroidWidget w = new AndroidWidget(root << 16 | child);
                if (w.rawGeo() == null || w.isHidden() || w.getWidth() <= 0) continue;
                java.awt.Rectangle b = w.getBounds();
                out.add(new Rect(b.x, b.y, b.x + b.width, b.y + b.height));
            }
        }
        exclusions = out;
        return out;
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
