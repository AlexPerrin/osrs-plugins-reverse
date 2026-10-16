package net.runelite.host;

import net.runelite.api.Friend;
import net.runelite.api.Ignore;
import net.runelite.api.Nameable;

/** Friends and ignore lists read from the game's FriendSystem (Native.friends, read per call). */
final class AndroidNameables {
    private AndroidNameables() {}

    /** Name comparison as the game's: case-insensitive, NBSP/underscore/space equivalent. */
    static String clean(String s) {
        return s == null ? "" : s.replace(' ', ' ').replace('_', ' ').trim().toLowerCase(java.util.Locale.ROOT);
    }

    static class Entry implements Friend, Ignore {
        final String name, prev;
        final int world;
        Entry(String name, String prev, int world) { this.name = name; this.prev = prev; this.world = world; }
        @Override public String getName() { return name; }
        @Override public String getPrevName() { return prev; }
        @Override public int getWorld() { return world; }
        @Override public int compareTo(Nameable o) { return getName().compareTo(o.getName()); }
        @Override public boolean equals(Object o) { return o instanceof Nameable && clean(name).equals(clean(((Nameable) o).getName())); }
        @Override public int hashCode() { return clean(name).hashCode(); }
        @Override public String toString() { return name + (world > 0 ? " @" + world : ""); }
    }

    static Entry[] read(boolean ignores) {
        Object[] r = Native.friends(ignores);
        if (r == null) return new Entry[0];
        String[] names = (String[]) r[0], prevs = (String[]) r[1];
        int[] wr = (int[]) r[2];
        Entry[] out = new Entry[names.length];
        for (int i = 0; i < names.length; i++) out[i] = new Entry(names[i], prevs[i], wr[2 * i]);
        return out;
    }

    static Entry find(Entry[] all, String name) {
        String n = clean(name);
        for (Entry e : all) if (clean(e.name).equals(n) || (e.prev != null && clean(e.prev).equals(n))) return e;
        return null;
    }

    static final class Friends implements net.runelite.api.FriendContainer {
        @Override public int getCount() { return read(false).length; }
        @Override public int getSize() { return 400; }
        @Override public Friend[] getMembers() { Entry[] e = read(false); Friend[] f = new Friend[e.length]; System.arraycopy(e, 0, f, 0, e.length); return f; }
        @Override public Friend findByName(String name) { return find(read(false), name); }
        // Login/logout notices the game hasn't shown yet; not read from the game (always empty).
        private final net.runelite.api.Deque<net.runelite.api.PendingLogin> pending = new net.runelite.api.Deque<net.runelite.api.PendingLogin>() {
            final java.util.ArrayDeque<net.runelite.api.PendingLogin> q = new java.util.ArrayDeque<>();
            @Override public void addLast(net.runelite.api.PendingLogin p) { }
            @Override public void clear() { }
            @Override public java.util.Iterator<net.runelite.api.PendingLogin> iterator() { return q.iterator(); }
        };
        @Override public net.runelite.api.Deque<net.runelite.api.PendingLogin> getPendingLogins() { return pending; }
    }

    static final class Ignores implements net.runelite.api.NameableContainer<Ignore> {
        @Override public int getCount() { return read(true).length; }
        @Override public int getSize() { return 400; }
        @Override public Ignore[] getMembers() { Entry[] e = read(true); Ignore[] f = new Ignore[e.length]; System.arraycopy(e, 0, f, 0, e.length); return f; }
        @Override public Ignore findByName(String name) { return find(read(true), name); }
    }
}
