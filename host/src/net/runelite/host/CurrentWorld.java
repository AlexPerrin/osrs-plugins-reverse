package net.runelite.host;

import android.util.Log;
import net.runelite.api.World;
import net.runelite.api.WorldType;

/**
 * The current world as the client knows it, for WorldService's world list (the api list has Jagex's
 * worlds only, so a private world such as the test server's 255 is missing from it). Read from the
 * game's own world list (slr.ws, which the game loaded from its configured server), else from the
 * client's world fields (id, flags, host). Nothing is fetched.
 */
public final class CurrentWorld {
    private CurrentWorld() {}
    private static String logged;

    /** The world {@code id}, or null when the client has no entry for it. */
    public static World entry(int id) {
        if (id <= 0) return null;
        Object[] e = Native.worldEntry(id);
        if (e == null) return null;
        int[] info = (int[]) e[2];
        AndroidWorld w = new AndroidWorld();
        w.setId(info[0]);
        w.setTypes(WorldType.fromMask(info[1]));
        w.setPlayerCount(info[2]);
        w.setLocation(info[3]);
        w.setAddress((String) e[0]);
        w.setActivity((String) e[1]);
        String desc = "world " + id + " " + (info[4] != 0 ? "from the world list" : "from the client fields")
                + ": host " + e[0] + ", activity \"" + e[1] + "\", flags 0x" + Integer.toHexString(info[1])
                + ", players " + info[2] + ", location " + info[3];
        if (!desc.equals(logged)) {
            logged = desc;
            Log.i(Host.TAG, "current " + desc);
        }
        return w;
    }
}
