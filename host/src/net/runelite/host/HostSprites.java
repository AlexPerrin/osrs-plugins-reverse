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
     * (same size only; the original pixels are kept and written back on remove). The cache can
     * evict and reload a sprite, so overrides are re-applied every 50 cycles (Host CLIENT_TICK).
     */
    static final class Overrides extends HashMap<Integer, SpritePixels> {
        private final Map<Integer, int[]> originals = new HashMap<>();
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
            int w = sp.getWidth(), h = sp.getHeight();
            if (orig[0] != w || orig[1] != h) {
                if (logs++ < 5) Log.w(Host.TAG, "sprite override " + id + ": " + w + "x" + h + " doesn't match the game's " + orig[0] + "x" + orig[1]);
                return;
            }
            int[] src = sp.getPixels(), px = new int[w * h];
            for (int i = 0; i < px.length; i++) px[i] = src[i] == 0 ? 0 : 0xff000000 | src[i];
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
