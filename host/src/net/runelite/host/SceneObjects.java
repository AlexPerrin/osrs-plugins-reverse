package net.runelite.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.Tile;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.eventbus.EventBus;

/**
 * Snapshot of the scene's objects and ground items (all planes), re-read at most once per server
 * tick on the client thread. Each refresh diffs against the previous one and posts RuneLite's
 * spawn/despawn events; unchanged objects keep their Java identity.
 */
final class SceneObjects {
    private static final int SIZE = 104;

    /** What one tile holds. */
    static final class Contents {
        AndroidTileObject.Wall wall;
        AndroidTileObject.Decorative decorative;
        AndroidTileObject.Ground ground;
        final List<AndroidTileObject.Game> games = new ArrayList<>(1);
        List<net.runelite.api.TileItem> items;  // null when none (as RuneLite's Tile.getGroundItems)
        AndroidTileObject.Items itemLayer;      // non-null exactly when items is
    }

    private final AndroidScene scene;
    private Map<Long, AndroidTileObject> objects = new HashMap<>();
    private Map<Long, AndroidTileItem> items = new HashMap<>();
    private Contents[][][] tiles = new Contents[4][SIZE][SIZE];
    private int refreshedTick = Integer.MIN_VALUE, refreshedBase = Integer.MIN_VALUE;

    SceneObjects(AndroidScene scene) {
        this.scene = scene;
    }

    /** Contents of a scene tile, refreshing first if the server tick moved on (client thread only). */
    synchronized Contents at(int plane, int x, int y) {
        refresh();
        if (plane < 0 || plane >= 4 || x < 0 || x >= SIZE || y < 0 || y >= SIZE) return null;
        return tiles[plane][x][y];
    }

    /** Where spawn/despawn events go (set once the plugins' bus exists). */
    volatile EventBus bus;

    /** Re-reads the scene if the tick or base changed and posts the differences as events. */
    synchronized void refresh() {
        int tick = Native.serverTick();
        AndroidWorldView wv = Host.worldView();
        int base = wv.getBaseX() << 16 | wv.getBaseY();
        if (tick == refreshedTick && base == refreshedBase) return;
        if (Host.client() == null || !Host.client().isClientThread()
                || Host.client().getGameState() != net.runelite.api.GameState.LOGGED_IN) return;
        int[] packed, packedItems;
        try {
            packed = Native.sceneObjects();
            packedItems = Native.groundItems();
        } catch (RuntimeException e) {
            return;
        }
        if (packed == null || packedItems == null) return;
        refreshedTick = tick;
        refreshedBase = base;
        int baseX = base >> 16, baseY = base & 0xffff;

        Contents[][][] t = new Contents[4][SIZE][SIZE];
        Map<Long, AndroidTileObject> nextObjects = new HashMap<>(packed.length / 5 * 2);
        List<AndroidTileObject> spawned = new ArrayList<>();
        for (int i = 0; i + 4 < packed.length; i += 5) {
            int pos = packed[i];
            int plane = pos >>> 24 & 0xff, layer = pos >>> 16 & 0xff, x = pos >>> 8 & 0xff, y = pos & 0xff;
            long tag = (packed[i + 2] & 0xffffffffL) | (long) packed[i + 3] << 32;
            int sx = packed[i + 4] >>> 8 & 0xff, sy = packed[i + 4] & 0xff;
            AndroidTileObject o = AndroidTileObject.create(layer, baseX + x, baseY + y, plane, packed[i + 1], tag, sx, sy);
            if (o == null || plane >= 4 || x >= SIZE || y >= SIZE) continue;
            AndroidTileObject old = objects.get(o.key());
            if (old != null) o = old;
            else spawned.add(o);
            nextObjects.put(o.key(), o);
            if (o instanceof AndroidTileObject.Game) {
                for (int dx = 0; dx < o.sizeX && x + dx < SIZE; dx++)
                    for (int dy = 0; dy < o.sizeY && y + dy < SIZE; dy++)
                        contents(t, plane, x + dx, y + dy).games.add((AndroidTileObject.Game) o);
            } else {
                Contents c = contents(t, plane, x, y);
                if (o instanceof AndroidTileObject.Wall) c.wall = (AndroidTileObject.Wall) o;
                else if (o instanceof AndroidTileObject.Decorative) c.decorative = (AndroidTileObject.Decorative) o;
                else c.ground = (AndroidTileObject.Ground) o;
            }
        }

        Map<Long, AndroidTileItem> nextItems = new HashMap<>();
        List<long[]> itemSpawns = new ArrayList<>();
        Map<Long, Integer> seen = new HashMap<>();
        for (int i = 0; i + 6 < packedItems.length; i += 7) {
            int pos = packedItems[i];
            int plane = pos >>> 16 & 0xff, x = pos >>> 8 & 0xff, y = pos & 0xff;
            if (plane >= 4 || x >= SIZE || y >= SIZE) continue;
            int id = packedItems[i + 1], qty = packedItems[i + 2];
            long base64 = (long) plane << 62 | (long) (baseX + x) << 44 | (long) (baseY + y) << 28 | (id & 0xfffffL) << 8;
            int n = seen.containsKey(base64) ? seen.get(base64) + 1 : 0;
            seen.put(base64, n);
            long key = base64 | (n & 0xff);
            AndroidTileItem item = items.get(key);
            if (item == null || item.quantity != qty) {
                item = new AndroidTileItem(id, qty);
                item.visibleTime = packedItems[i + 3];
                item.despawnTime = packedItems[i + 4];
                item.ownership = packedItems[i + 5];
                item.isPrivate = packedItems[i + 6] != 0;
                itemSpawns.add(new long[] {key, plane, x, y});
            }
            nextItems.put(key, item);
            Contents c = contents(t, plane, x, y);
            if (c.items == null) {
                c.items = new ArrayList<>(2);
                c.itemLayer = new AndroidTileObject.Items(baseX + x, baseY + y, plane);
            }
            c.items.add(item);
        }

        Map<Long, AndroidTileObject> oldObjects = objects;
        Map<Long, AndroidTileItem> oldItems = items;
        objects = nextObjects;
        items = nextItems;
        tiles = t;
        if (!spawned.isEmpty() || !itemSpawns.isEmpty() || oldObjects.size() + oldItems.size() != nextObjects.size() + nextItems.size())
            android.util.Log.i(Host.TAG, "scene: " + nextObjects.size() + " objects, " + nextItems.size() + " ground items ("
                    + spawned.size() + " new objects, " + itemSpawns.size() + " new items)");
        EventBus bus = this.bus;
        if (bus == null) return;

        for (Map.Entry<Long, AndroidTileObject> e : oldObjects.entrySet())
            if (!nextObjects.containsKey(e.getKey())) postDespawn(bus, e.getValue(), baseX, baseY);
        for (Map.Entry<Long, AndroidTileItem> e : oldItems.entrySet())
            if (nextItems.get(e.getKey()) != e.getValue()) {
                long k = e.getKey();
                int plane = (int) (k >>> 62), wx = (int) (k >>> 44 & 0x3fff), wy = (int) (k >>> 28 & 0xffff);
                Tile tile = tile(plane, wx - baseX, wy - baseY);
                if (tile != null) bus.post(new ItemDespawned(tile, e.getValue()));
            }
        for (AndroidTileObject o : spawned) postSpawn(bus, o, baseX, baseY);
        for (long[] s : itemSpawns) {
            Tile tile = tile((int) s[1], (int) s[2], (int) s[3]);
            if (tile != null) bus.post(new ItemSpawned(tile, nextItems.get(s[0])));
        }
    }

    /**
     * A scene load the host saw as LOADING (login, setGameState(LOADING) reloads): desktop's scene is
     * rebuilt and every object spawns again with no despawns (plugins clear their lists on LOADING),
     * so the next refresh posts spawns for all objects (Ground Object Hider re-hides after a reload).
     */
    synchronized void forgetObjects() {
        objects = new HashMap<>();
        refreshedTick = Integer.MIN_VALUE;
    }

    /**
     * Tile.setGroundObject(null): removes the game's ground decoration on the tile and posts
     * GroundObjectDespawned at once (desktop's tile hook does the same); the next refresh doesn't
     * see it again. The game re-adds it only when the scene is rebuilt (setGameState(LOADING)).
     */
    private int groundRemovals;

    synchronized void removeGround(int plane, int x, int y) {
        if (plane < 0 || plane >= 4 || x < 0 || x >= SIZE || y < 0 || y >= SIZE) return;
        Contents c = tiles[plane][x][y];
        AndroidTileObject.Ground g = c != null ? c.ground : null;
        boolean removed = Native.removeGroundObject(plane, x, y);
        if (++groundRemovals % 500 == 1)
            android.util.Log.i(Host.TAG, "setGroundObject(null) #" + groundRemovals + ": " + g + " removed " + removed);
        if (g == null) return;
        c.ground = null;
        objects.remove(g.key());
        EventBus bus = this.bus;
        AndroidWorldView wv = Host.worldView();
        if (bus != null) postDespawn(bus, g, wv.getBaseX(), wv.getBaseY());
    }

    private static Contents contents(Contents[][][] t, int plane, int x, int y) {
        Contents c = t[plane][x][y];
        if (c == null) t[plane][x][y] = c = new Contents();
        return c;
    }

    private Tile tile(int plane, int x, int y) {
        if (plane < 0 || plane >= 4 || x < 0 || x >= SIZE || y < 0 || y >= SIZE) return null;
        return scene.getTiles()[plane][x][y];
    }

    private void postSpawn(EventBus bus, AndroidTileObject o, int baseX, int baseY) {
        Tile tile = tile(o.plane, o.worldX - baseX, o.worldY - baseY);
        if (tile == null) return;
        if (o instanceof AndroidTileObject.Game) {
            GameObjectSpawned e = new GameObjectSpawned();
            e.setTile(tile);
            e.setGameObject((AndroidTileObject.Game) o);
            bus.post(e);
        } else if (o instanceof AndroidTileObject.Wall) {
            WallObjectSpawned e = new WallObjectSpawned();
            e.setTile(tile);
            e.setWallObject((AndroidTileObject.Wall) o);
            bus.post(e);
        } else if (o instanceof AndroidTileObject.Decorative) {
            DecorativeObjectSpawned e = new DecorativeObjectSpawned();
            e.setTile(tile);
            e.setDecorativeObject((AndroidTileObject.Decorative) o);
            bus.post(e);
        } else {
            GroundObjectSpawned e = new GroundObjectSpawned();
            e.setTile(tile);
            e.setGroundObject((AndroidTileObject.Ground) o);
            bus.post(e);
        }
    }

    private void postDespawn(EventBus bus, AndroidTileObject o, int baseX, int baseY) {
        Tile tile = tile(o.plane, o.worldX - baseX, o.worldY - baseY);
        if (tile == null) return;
        if (o instanceof AndroidTileObject.Game) {
            GameObjectDespawned e = new GameObjectDespawned();
            e.setTile(tile);
            e.setGameObject((AndroidTileObject.Game) o);
            bus.post(e);
        } else if (o instanceof AndroidTileObject.Wall) {
            WallObjectDespawned e = new WallObjectDespawned();
            e.setTile(tile);
            e.setWallObject((AndroidTileObject.Wall) o);
            bus.post(e);
        } else if (o instanceof AndroidTileObject.Decorative) {
            DecorativeObjectDespawned e = new DecorativeObjectDespawned();
            e.setTile(tile);
            e.setDecorativeObject((AndroidTileObject.Decorative) o);
            bus.post(e);
        } else {
            GroundObjectDespawned e = new GroundObjectDespawned();
            e.setTile(tile);
            e.setGroundObject((AndroidTileObject.Ground) o);
            bus.post(e);
        }
    }

    static <T> List<T> listOrEmpty(List<T> l) { return l != null ? l : Collections.<T>emptyList(); }
}
