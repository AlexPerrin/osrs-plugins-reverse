package net.runelite.host;

/**
 * A health bar type the game shows (desktop HealthBarDefinition as HealthBarConfig), from the head bars of live
 * actors (Native.healthBarTypes, docs/offsets.md "Health bar types"). Desktop posts PostHealthBarConfig as a type
 * is decoded; here it is posted when a type first appears on an actor's head bar (poll, after a hitsplat), and again
 * after resetHealthBarCaches. setPadding writes the game's type, which its draw reads.
 */
final class AndroidHealthBarConfig implements net.runelite.api.HealthBarConfig {
    private static final java.util.Set<Integer> posted = new java.util.HashSet<>();  // type ids
    final int id, front, back, width;

    private AndroidHealthBarConfig(int[] t, int o) {
        id = t[o];
        front = t[o + 1];
        back = t[o + 2];
        width = t[o + 3];
    }

    /** Game thread: posts PostHealthBarConfig for each type on an actor's head bar not posted yet. */
    static void poll(net.runelite.client.eventbus.EventBus bus) {
        int[] t = Native.healthBarTypes();
        if (t == null) return;
        for (int o = 0; o + 5 <= t.length; o += 5) {
            if (!posted.add(t[o])) continue;
            net.runelite.api.events.PostHealthBarConfig e = new net.runelite.api.events.PostHealthBarConfig();
            e.setHealthBarConfig(new AndroidHealthBarConfig(t, o));
            bus.post(e);
        }
    }

    /** resetHealthBarCaches: the game decodes the types again, so they are posted again. */
    static void reset() { posted.clear(); }

    /** The game's current padding of this type, or -1 when no actor shows it (game thread). */
    int padding() {
        int[] t = Native.healthBarTypes();
        for (int o = 0; t != null && o + 5 <= t.length; o += 5) if (t[o] == id) return t[o + 4];
        return -1;
    }

    private static net.runelite.api.SpritePixels sprite(int id) {
        int[] r = id < 0 || !Native.isClientThread() ? null : Native.sprite(id);
        return r == null ? null : new HostSpritePixels(java.util.Arrays.copyOfRange(r, 2, 2 + r[0] * r[1]), r[0], r[1]);
    }

    @Override public net.runelite.api.SpritePixels getHealthBarFrontSprite() { return sprite(front); }
    @Override public net.runelite.api.SpritePixels getHealthBarBackSprite() { return sprite(back); }
    @Override public int getHealthBarFrontSpriteId() { return front; }
    @Override public void setPadding(int padding) { if (Native.isClientThread()) Native.healthBarSetPadding(id, padding); }

    @Override public String toString() {
        return "HealthBarConfig[" + id + " front " + front + " back " + back + " width " + width + "]";
    }
}
