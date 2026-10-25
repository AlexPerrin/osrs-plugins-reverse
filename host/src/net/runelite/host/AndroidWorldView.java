package net.runelite.host;

import java.util.Iterator;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;

/** The top-level world view (the main scene). */
public class AndroidWorldView extends WorldViewStub {
    private final NpcCache npcs;
    private final PlayerCache players;

    AndroidWorldView(NpcCache npcs, PlayerCache players) {
        this.npcs = npcs;
        this.players = players;
    }

    @Override
    public IndexedObjectSet<? extends net.runelite.api.Player> players() {
        return new IndexedObjectSet<net.runelite.api.Player>() {
            @Override
            public net.runelite.api.Player byIndex(int index) {
                return players.get(index);
            }

            @Override
            public Iterator<net.runelite.api.Player> iterator() {
                return java.util.Collections.<net.runelite.api.Player>unmodifiableList(players.all()).iterator();
            }
        };
    }

    @Override public int getId() { return net.runelite.api.WorldView.TOPLEVEL; }  // 0 in API 1.13
    @Override public boolean isTopLevel() { return true; }
    /** The main camera; the top level has no entity transform, so its canvas projection is the same. */
    @Override public net.runelite.api.Projection getMainWorldProjection() { return AndroidRender.PROJECTION; }
    /**
     * The menu action a plain ground tap (desktop's yellow click) runs in this world view: WALK, or
     * SET_HEADING on a sailing boat's view. The host has only the top-level view, where a tap walks.
     * shortcut: no boat world views (the test server has no sailing); read the game's mode once one exists.
     */
    @Override public int getYellowClickAction() { return net.runelite.api.MenuAction.WALK.getId(); }
    @Override public net.runelite.api.Projection getCanvasProjection() { return AndroidRender.PROJECTION; }
    // REBUILD_REGION templates (Native.instanceTemplates), read once per cycle.
    private static int instStamp = -1;
    private static int[] instRaw;

    static int[] instanceRaw() {
        if (instStamp != Host.cycleStamp || instRaw == null) {
            int[] r = Native.instanceTemplates();
            instRaw = r != null && r.length == 1 + 4 * 13 * 13 ? r : new int[1 + 4 * 13 * 13];
            instStamp = Host.cycleStamp;
        }
        return instRaw;
    }

    static boolean instanced() { return instanceRaw()[0] == 1; }

    /** [level][x][z] packed as desktop (level << 24 | zoneX << 14 | zoneZ << 3 | rotation << 1), -1 = none. */
    static int[][][] templateChunks() {
        int[] r = instanceRaw();
        int[][][] out = new int[4][13][13];
        int i = 1;
        for (int l = 0; l < 4; l++)
            for (int x = 0; x < 13; x++)
                for (int z = 0; z < 13; z++) out[l][x][z] = r[i++];
        return out;
    }

    /** The distinct mapsquares an instance's templates come from (desktop's region list for REBUILD_REGION). */
    static int[] instanceRegions() {
        int[] r = instanceRaw();
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        for (int i = 1; i < r.length; i++) {
            int v = r[i];
            if (v == -1) continue;
            int zx = v >> 14 & 1023, zz = v >> 3 & 2047;
            ids.add((zx >> 3) << 8 | (zz >> 3));
        }
        int[] out = new int[ids.size()];
        int k = 0;
        for (int id : ids) out[k++] = id;
        return out;
    }

    @Override public boolean isInstance() { return instanced(); }
    @Override public int[][][] getInstanceTemplateChunks() { return templateChunks(); }
    @Override public int getSizeX() { return 104; }
    @Override public int getSizeY() { return 104; }

    @Override
    public boolean contains(net.runelite.api.coords.WorldPoint p) {
        int x = p.getX() - getBaseX(), y = p.getY() - getBaseY();
        return p.getPlane() == getPlane() && x >= 0 && y >= 0 && x < 104 && y < 104;
    }

    @Override
    public boolean contains(net.runelite.api.coords.LocalPoint p) {
        return p != null && p.getX() >= 0 && p.getY() >= 0 && p.getX() < 104 * 128 && p.getY() < 104 * 128;
    }

    @Override
    public int[] getMapRegions() {
        if (instanced()) return instanceRegions();
        // The 104x104 scene spans up to 4x4 64-tile regions around its base.
        int bx = getBaseX(), by = getBaseY();
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        for (int x = bx; x < bx + 104; x += 8)
            for (int y = by; y < by + 104; y += 8) ids.add((x >> 6) << 8 | (y >> 6));
        int[] out = new int[ids.size()];
        int i = 0;
        for (int id : ids) out[i++] = id;
        return out;
    }
    @Override
    public int getPlane() {
        int[] p = pos();
        return p == null ? 0 : p[7];
    }

    @Override
    public int getBaseX() {
        int[] p = pos();
        return p == null ? 0 : p[0] - p[2];
    }

    @Override
    public int getBaseY() {
        int[] p = pos();
        return p == null ? 0 : p[1] - p[3];
    }

    // --- ground heights: copied from the game's scene when the scene base changes ---

    private int[][][] tileHeights;
    private byte[][][] tileSettings;
    private int heightsBase = Integer.MIN_VALUE, heightsCycle;

    // The player position is read once per client cycle (Perspective asks per vertex).
    private int posStamp = -1, heightsStamp = -1;
    private int[] posCache;
    private synchronized int[] pos() {
        int stamp = Host.cycleStamp;
        if (stamp != posStamp) {
            posCache = Native.playerPosition();
            posStamp = stamp;
        }
        return posCache;
    }

    private synchronized void refreshHeights() {
        if (tileHeights != null && heightsStamp == Host.cycleStamp) return;
        heightsStamp = Host.cycleStamp;
        int base = getBaseX() << 16 | getBaseY();
        int cycle = Native.gameCycle();
        if (tileHeights != null && base == heightsBase && cycle - heightsCycle < 250) return;
        if (!Native.isClientThread()) return;  // keep the last copy
        int[] r = Native.sceneHeights();
        if (r == null) return;
        int planes = r[0], sx = r[1], sy = r[2], o = 3;
        int[][][] h = new int[4][sx + 1][sy + 1];
        byte[][][] st = new byte[4][sx][sy];
        for (int p = 0; p < planes; p++)
            for (int x = 0; x <= sx; x++) {
                System.arraycopy(r, o, h[p][x], 0, sy + 1);
                o += sy + 1;
            }
        for (int p = 0; p < planes; p++)
            for (int x = 0; x < sx; x++)
                for (int y = 0; y < sy; y++) st[p][x][y] = (byte) r[o++];
        tileHeights = h;
        tileSettings = st;
        heightsBase = base;
        heightsCycle = cycle;
    }

    /** Interpolated ground height at a local point (Perspective's formula over getTileHeights). */
    @Override
    public int getTileHeight(int localX, int localY, int plane) {
        net.runelite.api.Client c = Host.client();
        if (c == null) return 0;
        return net.runelite.api.Perspective.getTileHeight(c, new net.runelite.api.coords.LocalPoint(localX, localY, net.runelite.api.WorldView.TOPLEVEL), plane);
    }

    @Override
    public int[][][] getTileHeights() {
        refreshHeights();
        return tileHeights != null ? tileHeights : new int[4][105][105];
    }

    @Override
    public byte[][][] getTileSettings() {
        refreshHeights();
        return tileSettings != null ? tileSettings : new byte[4][104][104];
    }

    private final AndroidScene scene = new AndroidScene(this);

    private net.runelite.api.CollisionData[] collision;
    private int collisionTick = Integer.MIN_VALUE, collisionBase = Integer.MIN_VALUE;

    /**
     * The game's collision flags per plane (docs/offsets.md "Collision maps"; same flag bits as
     * desktop), re-read at most once per server tick or scene change. Off the client thread the last
     * read is returned; null before the first one (as desktop before a scene loads).
     */
    @Override
    public synchronized net.runelite.api.CollisionData[] getCollisionMaps() {
        int tick = Native.serverTick(), base = getBaseX() << 16 | getBaseY();
        if ((collision == null || tick != collisionTick || base != collisionBase)
                && Host.client() != null && Host.client().isClientThread()) {
            net.runelite.api.CollisionData[] maps = new net.runelite.api.CollisionData[4];
            boolean any = false;
            for (int p = 0; p < 4; p++) {
                int[] f = Native.collisionFlags(p, 104);
                final int[][] flags = new int[104][104];
                if (f != null) {
                    any = true;
                    for (int x = 0; x < 104; x++) System.arraycopy(f, x * 104, flags[x], 0, 104);
                }
                maps[p] = () -> flags;
            }
            if (any) {
                collision = maps;
                collisionTick = tick;
                collisionBase = base;
            }
        }
        return collision;
    }

    /** No world entities (boats) yet. */
    private static <T> IndexedObjectSet<T> emptySet() {
        return new IndexedObjectSet<T>() {
            @Override public T byIndex(int index) { return null; }
            @Override public Iterator<T> iterator() { return java.util.Collections.<T>emptyList().iterator(); }
        };
    }

    @Override public IndexedObjectSet<? extends net.runelite.api.WorldView> worldViews() { return emptySet(); }
    @Override public IndexedObjectSet<? extends net.runelite.api.WorldEntity> worldEntities() { return emptySet(); }

    @Override public net.runelite.api.Scene getScene() { return scene; }

    /**
     * The tile the open long-press menu is for (desktop: the tile under the mouse): the tile of the
     * menu's "Walk here" entry, else the tile whose polygon contains the long-press point.
     */
    private net.runelite.api.Tile lastSelected;
    private long lastSelectedAt;

    /**
     * The tile the long-press menu is or was just for. While the menu is open it is computed
     * (selectedTile, also cached when a menu opens); up to 10 s after that the last one is kept, as plugin-strip entries
     * (Shortest Path's "Set Target") run after the game's menu has gone.
     */
    @Override
    public net.runelite.api.Tile getSelectedSceneTile() {
        if (!Native.menuOpen()) {
            return System.currentTimeMillis() - lastSelectedAt < 10_000 ? lastSelected : null;
        }
        net.runelite.api.Tile t = selectedTile();
        if (t != null) {
            lastSelected = t;
            lastSelectedAt = System.currentTimeMillis();
        }
        return t;
    }

    private net.runelite.api.Tile selectedTile() {
        int plane = getPlane();
        net.runelite.api.Tile[][][] tiles = scene.getTiles();
        if (tiles == null || plane < 0 || plane >= tiles.length) return null;
        for (AndroidMenuEntry e : AndroidMenuEntry.fromNative(Native.menuEntries())) {
            if (e.getType() != net.runelite.api.MenuAction.WALK || e.worldX() == -1) continue;
            int x = e.getParam0(), y = e.getParam1();
            if (x >= 0 && y >= 0 && x < tiles[plane].length && y < tiles[plane][x].length) return tiles[plane][x][y];
        }
        int[] p = Native.menuPoint();
        net.runelite.api.Player me = Host.localPlayer();
        if (p == null || me == null) return null;
        net.runelite.api.coords.LocalPoint lp = me.getLocalLocation();
        if (lp == null) return null;
        int cx = lp.getSceneX(), cy = lp.getSceneY();
        for (int r = 0; r <= 25; r++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int y = cy - r; y <= cy + r; y++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(y - cy)) != r) continue;
                    if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length) continue;
                    java.awt.Polygon poly = net.runelite.api.Perspective.getCanvasTilePoly(Host.client(),
                            net.runelite.api.coords.LocalPoint.fromScene(x, y, this));
                    if (poly != null && poly.contains(p[0], p[1])) return tiles[plane][x][y];
                }
            }
        }
        return null;
    }
    AndroidScene androidScene() { return scene; }

    @Override
    public IndexedObjectSet<? extends NPC> npcs() {
        return new IndexedObjectSet<NPC>() {
            @Override
            public NPC byIndex(int index) {
                return npcs.get(index);
            }

            @Override
            public Iterator<NPC> iterator() {
                return java.util.Collections.<NPC>unmodifiableList(npcs.all()).iterator();
            }
        };
    }

    /** The top-level view's map spotanims (Client.getGraphicsObjects). */
    @Override public net.runelite.api.Deque<net.runelite.api.GraphicsObject> getGraphicsObjects() {
        return Host.client().getGraphicsObjects();
    }
}
