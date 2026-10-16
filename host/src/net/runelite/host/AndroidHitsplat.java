package net.runelite.host;

import java.util.ArrayList;
import java.util.List;

/** A hitmark slot of an actor (Actor +0x330: {type, amount, end cycle}). */
final class AndroidHitsplat implements net.runelite.api.Hitsplat {
    private final int type, amount, end;

    AndroidHitsplat(int type, int amount, int end) {
        this.type = type;
        this.amount = amount;
        this.end = end;
    }

    @Override public int getHitsplatType() { return type; }
    @Override public int getAmount() { return amount; }
    @Override public int getDisappearsOnGameCycle() { return end; }
    @Override public String toString() { return "Hitsplat(" + type + ", " + amount + ", " + end + ")"; }

    /**
     * Compares the 4 snapshot slots at p[o..o+11] with `slots` (updated in place) and returns the
     * hitmarks that are new since the last snapshot (a slot is reused with a later end cycle).
     */
    static List<net.runelite.api.Hitsplat> diff(int[] p, int o, int[] slots) {
        List<net.runelite.api.Hitsplat> out = null;
        for (int i = 0; i < 12; i += 3) {
            int t = p[o + i], a = p[o + i + 1], e = p[o + i + 2];
            if (t >= 0 && (t != slots[i] || a != slots[i + 1] || e != slots[i + 2])) {
                if (out == null) out = new ArrayList<>(2);
                out.add(new AndroidHitsplat(t, a, e));
            }
            slots[i] = t;
            slots[i + 1] = a;
            slots[i + 2] = e;
        }
        return out;
    }
}
