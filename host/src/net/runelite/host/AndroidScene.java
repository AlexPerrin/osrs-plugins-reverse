package net.runelite.host;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * The top-level scene: tiles with locations, ground heights, scene objects and ground items
 * (SceneObjects, re-read once per server tick).
 */
final class AndroidScene extends SceneStub {
    private static final int SIZE = 104;
    private final AndroidWorldView wv;
    private Tile[][][] tiles;
    private int tilesBase = Integer.MIN_VALUE;

    final SceneObjects objects = new SceneObjects(this);

    AndroidScene(AndroidWorldView wv) {
        this.wv = wv;
    }

    @Override
    public synchronized net.runelite.api.Tile[][][] getTiles() {
        int base = wv.getBaseX() << 16 | wv.getBaseY();
        if (tiles == null || base != tilesBase) {
            Tile[][][] t = new Tile[4][SIZE][SIZE];
            for (int z = 0; z < 4; z++)
                for (int x = 0; x < SIZE; x++)
                    for (int y = 0; y < SIZE; y++) t[z][x][y] = new Tile(x, y, z);
            tiles = t;
            tilesBase = base;
        }
        return tiles;
    }

    @Override public net.runelite.api.Tile[][][] getExtendedTiles() { return getTiles(); }
    @Override public int[][][] getTileHeights() { return wv.getTileHeights(); }
    private byte[][][] extSettings, extSource;

    /**
     * RuneLite's extended scene: 184 x 184 with the 104-tile scene at offset 40 (Perspective indexes
     * it with that margin for the top-level view). Only the inner scene is known; the margin is 0.
     */
    @Override public synchronized byte[][][] getExtendedTileSettings() {
        byte[][][] src = wv.getTileSettings();
        if (src != extSource || extSettings == null) {
            int ext = net.runelite.api.Constants.EXTENDED_SCENE_SIZE, off = (ext - SIZE) / 2;
            byte[][][] e = new byte[src.length][ext][ext];
            for (int z = 0; z < src.length; z++)
                for (int x = 0; x < src[z].length && x < SIZE; x++)
                    System.arraycopy(src[z][x], 0, e[z][x + off], off, Math.min(SIZE, src[z][x].length));
            extSettings = e;
            extSource = src;
        }
        return extSettings;
    }
    @Override public int getBaseX() { return wv.getBaseX(); }
    @Override public int getBaseY() { return wv.getBaseY(); }
    @Override public int getWorldViewId() { return -1; }
    @Override public boolean isInstance() { return false; }

    final class Tile extends TileStub {
        private final int x, y, z;

        Tile(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override public WorldPoint getWorldLocation() { return new WorldPoint(wv.getBaseX() + x, wv.getBaseY() + y, z); }
        @Override public net.runelite.api.Point getSceneLocation() { return new net.runelite.api.Point(x, y); }
        @Override public LocalPoint getLocalLocation() { return LocalPoint.fromScene(x, y, wv); }
        @Override public int getPlane() { return z; }
        @Override public int getRenderLevel() { return z; }
        private SceneObjects.Contents contents() { return objects.at(z, x, y); }
        @Override public List<net.runelite.api.TileItem> getGroundItems() {
            SceneObjects.Contents c = contents();
            return c != null && c.items != null ? Collections.unmodifiableList(c.items) : null;
        }
        @Override public net.runelite.api.GameObject[] getGameObjects() {
            SceneObjects.Contents c = contents();
            return c == null ? new net.runelite.api.GameObject[0] : c.games.toArray(new net.runelite.api.GameObject[0]);
        }
        @Override public net.runelite.api.WallObject getWallObject() {
            SceneObjects.Contents c = contents();
            return c != null ? c.wall : null;
        }
        @Override public net.runelite.api.GroundObject getGroundObject() {
            SceneObjects.Contents c = contents();
            return c != null ? c.ground : null;
        }
        @Override public net.runelite.api.DecorativeObject getDecorativeObject() {
            SceneObjects.Contents c = contents();
            return c != null ? c.decorative : null;
        }
        @Override public net.runelite.api.ItemLayer getItemLayer() {
            SceneObjects.Contents c = contents();
            return c != null ? c.itemLayer : null;
        }
        @Override public net.runelite.api.Tile getBridge() { return null; }
    }
}
