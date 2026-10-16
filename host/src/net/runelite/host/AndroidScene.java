package net.runelite.host;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * The top-level scene: tiles with locations and ground heights. Scene objects (game/wall/ground
 * objects, ground items) aren't read from the game yet, so tiles report none.
 */
final class AndroidScene extends SceneStub {
    private static final int SIZE = 104;
    private final AndroidWorldView wv;
    private Tile[][][] tiles;
    private int tilesBase = Integer.MIN_VALUE;

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
    @Override public byte[][][] getExtendedTileSettings() { return wv.getTileSettings(); }
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
        @Override public List<net.runelite.api.TileItem> getGroundItems() { return null; }
        @Override public net.runelite.api.GameObject[] getGameObjects() { return new net.runelite.api.GameObject[0]; }
        @Override public net.runelite.api.WallObject getWallObject() { return null; }
        @Override public net.runelite.api.GroundObject getGroundObject() { return null; }
        @Override public net.runelite.api.DecorativeObject getDecorativeObject() { return null; }
        @Override public net.runelite.api.ItemLayer getItemLayer() { return null; }
        @Override public net.runelite.api.Tile getBridge() { return null; }
    }
}
