package net.runelite.host;

import android.util.Log;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;

/**
 * Graphic widgets showing a plugin-provided sprite id (RuneLite's custom sprites are negative ids
 * registered through Client.getSpriteOverrides, e.g. Bank Tags' tab backgrounds and arrows -201..).
 * The game's sprite cache has no entry for them, so it draws nothing; the host draws them on the
 * overlay canvas at the widget's bounds instead, right after the game's widgets (before plugin
 * overlays). Item icons of later siblings that the drawn sprite overlaps are redrawn on top, since
 * the game drew them under it (Bank Tags puts each tab's item icon above its background).
 * // unverified: on the device.
 */
final class CustomSpriteWidgets {
    private CustomSpriteWidgets() {}

    private static final String TAG = "osrsrl-host";
    // Widget (static or dynamic child) -> sprite id it was given.
    private static final Map<AndroidWidget, Integer> tracked = new LinkedHashMap<>();
    private static final Map<Integer, BufferedImage> images = new HashMap<>();
    private static int logs;

    static synchronized void note(AndroidWidget w, int spriteId) {
        if (spriteId < 0 && spriteId != -1) tracked.put(w, spriteId);
        else tracked.remove(w);
    }

    static synchronized boolean isEmpty() { return tracked.isEmpty(); }

    /** Game thread, from OverlayRenderer before the ABOVE_WIDGETS overlays. */
    static void draw(Graphics2D g) {
        List<Map.Entry<AndroidWidget, Integer>> list;
        synchronized (CustomSpriteWidgets.class) {
            if (tracked.isEmpty()) return;
            list = new ArrayList<>(tracked.entrySet());
        }
        net.runelite.api.Client client = Host.client();
        if (client == null) return;
        Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
        for (Map.Entry<AndroidWidget, Integer> e : list) {
            AndroidWidget w = e.getKey();
            int sprite = e.getValue();
            try {
                if (w.rawGeo() == null || w.getSpriteId() != sprite) {  // deleted or re-sprited
                    synchronized (CustomSpriteWidgets.class) { tracked.remove(w); }
                    continue;
                }
                if (w.isHidden()) continue;
                BufferedImage img = images.get(sprite);
                if (img == null) {
                    SpritePixels sp = overrides.get(sprite);
                    if (sp == null) continue;
                    img = sp.toBufferedImage();
                    images.put(sprite, img);
                }
                Rectangle b = w.getBounds();
                if (b.width <= 0 || b.height <= 0) continue;
                Graphics2D c = (Graphics2D) g.create();
                try {
                    c.clipRect(b.x, b.y, b.width, b.height);
                    c.drawImage(img, b.x, b.y, null);
                } finally {
                    c.dispose();
                }
                redrawItemsOver(g, w, b);
            } catch (Throwable t) {
                if (logs++ < 5) Log.w(TAG, "custom sprite widget " + w + " failed: " + t);
            }
        }
    }

    /** Later dynamic siblings with an item that overlap `b`: their icon goes back on top. */
    private static void redrawItemsOver(Graphics2D g, AndroidWidget w, Rectangle b) {
        if (!(w instanceof AndroidWidget.Dynamic)) return;
        Widget parent = w.getParent();
        Widget[] sib = parent != null ? parent.getDynamicChildren() : null;
        if (sib == null) return;
        net.runelite.client.game.ItemManager items = net.runelite.client.RuneLite.getInjector().getInstance(net.runelite.client.game.ItemManager.class);
        for (int i = w.getIndex() + 1; i < sib.length; i++) {
            Widget s = sib[i];
            if (s == null || s.getItemId() < 0 || s.isHidden()) continue;
            Rectangle sb = s.getBounds();
            if (!sb.intersects(b)) continue;
            BufferedImage icon = items.getImage(s.getItemId(), Math.max(1, s.getItemQuantity()), false);
            g.drawImage(icon, sb.x + (sb.width - icon.getWidth()) / 2, sb.y + (sb.height - icon.getHeight()) / 2, null);
        }
    }
}
