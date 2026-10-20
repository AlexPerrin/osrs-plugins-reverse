package net.runelite.host;

import android.util.Log;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.SpritePixels;

/** Sprites from the game's sprite cache (Native.sprite, docs/offsets.md): SpriteManager and overrides. */
public final class HostSprites {
    private HostSprites() {}

    public static boolean onClientThread() { return Native.isClientThread(); }

    /** Frame 0 of graphic `id` as an ARGB image; null while not loaded. Client thread only. */
    public static BufferedImage load(int id) {
        int[] r = Native.sprite(id);
        if (r == null) return null;
        int w = r[0], h = r[1];
        return new HostSpritePixels(java.util.Arrays.copyOfRange(r, 2, 2 + w * h), w, h).toBufferedImage();
    }

    /**
     * Client.getSpriteOverrides: a put writes the sprite into the game's cached copy of that id
     * (the original pixels are kept and written back on remove). The cache can evict and reload a
     * sprite, so overrides are re-applied every 50 cycles (Host CLIENT_TICK).
     * The game keeps a sprite trimmed (width x height at an offset in its full canvas) and the
     * pixels can only be rewritten in place. Desktop draws an override as the whole canvas, so an
     * override of the canvas size is cropped to the trimmed block at the sprite's offset (Poison's
     * 26x26 hearts on the 15x14 orb heart); one of the trimmed size is written as is. Other sizes are
     * refused (logged).
     */
    static final class Overrides extends HashMap<Integer, SpritePixels> {
        private final Map<Integer, int[]> originals = new HashMap<>();
        private final Map<Integer, int[]> infos = new HashMap<>();
        private int logs;

        @Override public SpritePixels put(Integer id, SpritePixels sp) {
            SpritePixels old = super.put(id, sp);
            run(() -> apply(id, sp));
            return old;
        }

        @Override public SpritePixels remove(Object key) {
            SpritePixels old = super.remove(key);
            if (key instanceof Integer) run(() -> restore((Integer) key));
            return old;
        }

        private static void run(Runnable r) {
            if (Native.isClientThread()) r.run();
            else Host.invoke(r);
        }

        private void apply(int id, SpritePixels sp) {
            if (sp == null || !containsKey(id)) return;
            if (!originals.containsKey(id)) {
                int[] r = Native.sprite(id);
                if (r == null) return;  // not loaded yet: the periodic pass retries
                originals.put(id, r);
            }
            int[] orig = originals.get(id);
            int w = orig[0], h = orig[1], sw = sp.getWidth(), sh = sp.getHeight(), ox = 0, oy = 0;
            if (sw != w || sh != h) {
                int[] info = infos.get(id);
                boolean fresh = info == null;
                if (fresh && (info = Native.spriteInfo(id)) != null) infos.put(id, info);
                if (info == null || info[4] != sw || info[5] != sh || info[2] < 0 || info[3] < 0
                        || info[2] + w > sw || info[3] + h > sh) {
                    if (logs++ < 5) Log.w(Host.TAG, "sprite override " + id + ": " + sw + "x" + sh + " matches neither the game's "
                            + w + "x" + h + " nor its canvas" + (info == null ? "" : " " + info[4] + "x" + info[5]));
                    return;
                }
                ox = info[2];
                oy = info[3];
                if (fresh) Log.i(Host.TAG, "sprite override " + id + ": " + sw + "x" + sh + " canvas cropped to the game's "
                        + w + "x" + h + " at " + ox + "," + oy);
            }
            int[] src = sp.getPixels(), px = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = src[(y + oy) * sw + x + ox];
                    px[y * w + x] = c == 0 ? 0 : 0xff000000 | c;
                }
            Native.spriteWrite(id, px, w, h);
        }

        private void restore(int id) {
            int[] orig = originals.remove(id);
            if (orig != null) Native.spriteWrite(id, java.util.Arrays.copyOfRange(orig, 2, orig.length), orig[0], orig[1]);
        }

        /** Client thread, every 50 cycles: re-applies after cache evictions. */
        void reapply() {
            if (isEmpty()) return;
            for (Map.Entry<Integer, SpritePixels> e : new HashMap<>(this).entrySet()) apply(e.getKey(), e.getValue());
        }
    }

    static void onCycle(net.runelite.api.Client client) {
        if (Host.cycleStamp % 50 != 0) return;
        Map<Integer, SpritePixels> m = client.getSpriteOverrides();
        if (m instanceof Overrides) ((Overrides) m).reapply();
    }
}
