package net.runelite.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.runelite.api.AmbientSoundEffect;
import net.runelite.api.events.AmbientSoundEffectCreated;
import net.runelite.client.eventbus.EventBus;

/**
 * Client.getAmbientSoundEffects over the game's live ambient (loc) sound list (game::ambient_sounds,
 * docs/offsets.md "Ambient sounds"). clear() is the game's own clear (the sounds stop next cycle);
 * addLast takes back an effect cleared in the same cycle, which is how plugins drop a few of them
 * (Annoyance Mute, Music: clear, then re-add the ones to keep). AmbientSoundEffectCreated is posted
 * for each entry the list gains (polled every client tick and before LOGGED_IN).
 */
final class AmbientSounds implements net.runelite.api.Deque<AmbientSoundEffect> {
    static final class Effect implements AmbientSoundEffect {
        final long obj;
        final int id, plane;
        final int[] background;

        Effect(long obj, int id, int plane, int[] background) {
            this.obj = obj;
            this.id = id;
            this.plane = plane;
            this.background = background;
        }

        long key() { return obj ^ ((long) id << 48) ^ ((long) plane << 40); }
        @Override public int getSoundEffectId() { return id; }
        @Override public int[] getBackgroundSoundEffectIds() { return background; }
        @Override public int getPlane() { return plane; }
        @Override public String toString() { return "AmbientSound[" + id + " plane " + plane + "]"; }
    }

    private Map<Long, Effect> known = new HashMap<>();

    /** The live list (identities kept across reads); empty off the client thread. */
    private synchronized List<Effect> read() {
        List<Effect> out = new ArrayList<>();
        int[] p;
        try {
            p = Native.ambientSounds();
        } catch (RuntimeException e) {
            return out;
        }
        if (p == null) return out;
        for (int i = 0; i + 4 < p.length; ) {
            long obj = (p[i] & 0xffffffffL) | (long) p[i + 1] << 32;
            int id = p[i + 2], plane = p[i + 3], n = p[i + 4];
            i += 5;
            if (n < 0 || i + n > p.length) break;
            int[] bg = null;
            if (n > 0) {
                bg = new int[n];
                System.arraycopy(p, i, bg, 0, n);
            }
            i += n;
            Effect e = new Effect(obj, id, plane, bg);
            Effect old = known.get(e.key());
            if (old != null) e = old;
            out.add(e);
        }
        return out;
    }

    /** Posts AmbientSoundEffectCreated for entries not seen before (client thread, logged in). */
    synchronized void poll(EventBus bus) {
        Map<Long, Effect> before = known;
        List<Effect> now = read();
        Map<Long, Effect> next = new HashMap<>();
        List<Effect> created = new ArrayList<>();
        for (Effect e : now) {
            next.put(e.key(), e);
            if (!before.containsKey(e.key())) created.add(e);
        }
        known = next;
        if (!created.isEmpty() || before.size() != next.size())
            android.util.Log.i(Host.TAG, "ambient sounds: " + next.size() + " (" + created.size() + " new"
                    + (created.isEmpty() ? "" : ", first " + created.get(0)) + ")");
        for (Effect e : created) bus.post(new AmbientSoundEffectCreated(e));
    }

    synchronized void reset() { known = new HashMap<>(); }

    @Override public void addLast(AmbientSoundEffect e) {
        if (e instanceof Effect && !Native.ambientRestore(((Effect) e).obj))
            android.util.Log.d(Host.TAG, "ambient sound " + e + " not restored (not cleared this cycle)");
    }

    @Override public void clear() { Native.ambientClear(); }

    @Override public Iterator<AmbientSoundEffect> iterator() {
        return new ArrayList<AmbientSoundEffect>(read()).iterator();
    }
}
