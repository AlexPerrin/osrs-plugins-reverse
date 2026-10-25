package net.runelite.host;

import android.util.Log;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.eventbus.EventBus;

/**
 * The game's projectile list (Client +0xcb30, Native.projectiles) as Client.getProjectiles, and
 * ProjectileMoved. Desktop posts ProjectileMoved from Projectile.setDestination, which the client
 * calls every cycle from the start cycle on (position = the target's local point, z = target tile
 * height - end height), before the projectile's x/y are first set; the host posts it per client tick
 * for projectiles in [start, end] cycle, with the snapshot taken that tick (x/y 0 on the first).
 * `setprop debug.osrsrl.projlog 1` logs new projectiles.
 */
final class Projectiles implements net.runelite.api.Deque<net.runelite.api.Projectile> {
    private static final int INTS = 26;
    private final java.util.LinkedHashMap<Long, AndroidProjectile> live = new java.util.LinkedHashMap<>();

    void poll(EventBus bus, net.runelite.api.Client client) {
        int[] r = Native.projectiles();
        if (r == null) return;
        boolean log = "1".equals(Native.property("debug.osrsrl.projlog"));
        java.util.LinkedHashMap<Long, AndroidProjectile> next = new java.util.LinkedHashMap<>();
        for (int o = 0; o + INTS <= r.length; o += INTS) {
            long obj = (r[o] & 0xffffffffL) | ((long) r[o + 1] << 32);
            AndroidProjectile p = live.get(obj);
            boolean added = p == null;
            if (added) p = new AndroidProjectile(obj);
            p.update(r, o);
            next.put(obj, p);
            if (added && log) Log.i(Host.TAG, "projectile " + p);
        }
        live.clear();
        live.putAll(next);
        int cycle = client.getGameCycle();
        for (AndroidProjectile p : new java.util.ArrayList<>(live.values())) {
            if (cycle < p.startCycle || cycle > p.endCycle) continue;
            LocalPoint target = p.getTarget();
            if (target == null) continue;
            ProjectileMoved e = new ProjectileMoved();
            e.setProjectile(p);
            e.setPosition(target);
            e.setZ(Perspective.getTileHeight(client, target, p.targetLevel) - p.endHeight);
            if (log) Log.i(Host.TAG, "ProjectileMoved id " + p.id + " cycle " + cycle + " x/y " + p.x + "," + p.y + " z " + p.z
                    + " target " + target + " z " + e.getZ() + " x1/y1 " + p.getX1() + "," + p.getY1()
                    + " src " + p.getSourceActor() + " tgt " + p.getTargetActor() + " remaining " + p.getRemainingCycles());
            bus.post(e);
        }
    }

    void reset() { live.clear(); }

    /** Client.createProjectile: the game spawns it into its list; it is listed here at once. */
    AndroidProjectile create(int[] s) {
        long obj = Native.projectileCreate(s);
        int[] r = obj == 0 ? null : Native.projectiles();
        for (int o = 0; r != null && o + INTS <= r.length; o += INTS) {
            if (((r[o] & 0xffffffffL) | ((long) r[o + 1] << 32)) != obj) continue;
            AndroidProjectile p = new AndroidProjectile(obj);
            p.update(r, o);
            live.put(obj, p);
            return p;
        }
        return null;
    }

    /** createProjectile already put it in the game's list (desktop's deprecated addLast step); others can't be added. */
    @Override public void addLast(net.runelite.api.Projectile p) { }
    @Override public void clear() { }
    @Override public java.util.Iterator<net.runelite.api.Projectile> iterator() {
        return java.util.Collections.<net.runelite.api.Projectile>unmodifiableCollection(new java.util.ArrayList<>(live.values())).iterator();
    }
}
