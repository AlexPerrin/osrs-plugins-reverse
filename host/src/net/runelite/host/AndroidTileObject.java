package net.runelite.host;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;

import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * A scene object read from the game's scene (game::scene_objects). Positions are kept in world
 * coordinates so an object survives scene base changes; scene/local positions are derived from
 * the current base. Hulls and click boxes are boxes over the footprint (models aren't read).
 */
abstract class AndroidTileObject implements net.runelite.api.TileObject {
    final int worldX, worldY, plane, id, sizeX, sizeY;
    final long tag;

    AndroidTileObject(int worldX, int worldY, int plane, int id, long tag, int sizeX, int sizeY) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.plane = plane;
        this.id = id;
        this.tag = tag;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
    }

    /** Box height in local units used for the click box / hull. */
    abstract int boxHeight();

    int sceneX() { return worldX - Host.worldView().getBaseX(); }
    int sceneY() { return worldY - Host.worldView().getBaseY(); }

    @Override public long getHash() { return tag; }
    @Override public int getX() { return sceneX() * 128 + sizeX * 64; }
    @Override public int getY() { return sceneY() * 128 + sizeY * 64; }
    @Override public int getZ() {
        return Host.worldView().getTileHeight(getX(), getY(), plane);
    }
    @Override public int getPlane() { return plane; }
    @Override public WorldView getWorldView() { return Host.worldView(); }
    @Override public int getId() { return id; }
    @Override public WorldPoint getWorldLocation() { return new WorldPoint(worldX, worldY, plane); }
    @Override public LocalPoint getLocalLocation() { return new LocalPoint(getX(), getY(), WorldView.TOPLEVEL); }

    @Override public Point getCanvasLocation() { return getCanvasLocation(0); }
    @Override public Point getCanvasLocation(int zOffset) {
        return Host.client() == null ? null : Perspective.localToCanvas(Host.client(), getLocalLocation(), plane, zOffset);
    }
    @Override public Polygon getCanvasTilePoly() {
        return Host.client() == null ? null
                : Perspective.getCanvasTileAreaPoly(Host.client(), getLocalLocation(), sizeX, sizeY, plane, 0);
    }
    @Override public Point getCanvasTextLocation(Graphics2D g, String text, int zOffset) {
        return Host.client() == null ? null : Perspective.getCanvasTextLocation(Host.client(), g, getLocalLocation(), text, zOffset);
    }
    /** Minimap geometry isn't mapped yet. */
    @Override public Point getMinimapLocation() { return null; }
    @Override public Shape getClickbox() {
        int h = boxHeight();
        if (h <= 0) return getCanvasTilePoly();
        return Hulls.box(getX(), getY(), sizeX * 64 - 8, sizeY * 64 - 8, plane, h);
    }
    @Override public String getOpOverride(int op) { return null; }
    @Override public boolean isOpShown(int op) { return true; }

    public Shape getConvexHull() { return getClickbox(); }
    public Shape getConvexHull2() { return null; }
    public int getConfig() { return (int) (tag >>> 14 & 0x3f); }

    /** Same object (by world position, plane, id and layer). */
    long key() {
        return (long) plane << 62 | (long) layer() << 60 | (long) worldX << 44 | (long) worldY << 28 | (id & 0xfffffff);
    }

    abstract int layer();

    @Override public String toString() {
        return getClass().getSimpleName() + "[" + id + " at " + worldX + "," + worldY + "," + plane + "]";
    }

    static AndroidTileObject create(int layer, int worldX, int worldY, int plane, int id, long tag, int sx, int sy) {
        switch (layer) {
            case 0: return new Wall(worldX, worldY, plane, id, tag);
            case 1: return new Decorative(worldX, worldY, plane, id, tag);
            case 2: return new Game(worldX, worldY, plane, id, tag, sx, sy);
            case 3: return new Ground(worldX, worldY, plane, id, tag);
            default: return null;
        }
    }

    static final class Game extends AndroidTileObject implements net.runelite.api.GameObject {
        Game(int wx, int wy, int plane, int id, long tag, int sx, int sy) { super(wx, wy, plane, id, tag, sx, sy); }
        @Override int layer() { return 2; }
        @Override int boxHeight() { return 160; }
        @Override public int sizeX() { return sizeX; }
        @Override public int sizeY() { return sizeY; }
        @Override public Point getSceneMinLocation() { return new Point(sceneX(), sceneY()); }
        @Override public Point getSceneMaxLocation() { return new Point(sceneX() + sizeX - 1, sceneY() + sizeY - 1); }
        @Override public int getOrientation() { return 0; }
        @Override public Renderable getRenderable() { return null; }
        @Override public int getModelOrientation() { return 0; }
    }

    static final class Wall extends AndroidTileObject implements net.runelite.api.WallObject {
        Wall(int wx, int wy, int plane, int id, long tag) { super(wx, wy, plane, id, tag, 1, 1); }
        @Override int layer() { return 0; }
        @Override int boxHeight() { return 240; }
        @Override public int getOrientationA() { return 0; }
        @Override public int getOrientationB() { return 0; }
        @Override public Renderable getRenderable1() { return null; }
        @Override public Renderable getRenderable2() { return null; }
    }

    static final class Decorative extends AndroidTileObject implements net.runelite.api.DecorativeObject {
        Decorative(int wx, int wy, int plane, int id, long tag) { super(wx, wy, plane, id, tag, 1, 1); }
        @Override int layer() { return 1; }
        @Override int boxHeight() { return 120; }
        @Override public Renderable getRenderable() { return null; }
        @Override public Renderable getRenderable2() { return null; }
        @Override public int getXOffset() { return 0; }
        @Override public int getYOffset() { return 0; }
        @Override public int getXOffset2() { return 0; }
        @Override public int getYOffset2() { return 0; }
    }

    /** The item pile on a tile (one per tile with ground items); renderables aren't read. */
    static final class Items extends AndroidTileObject implements net.runelite.api.ItemLayer {
        Items(int wx, int wy, int plane) { super(wx, wy, plane, -1, 0, 1, 1); }
        @Override int layer() { return 4; }
        @Override int boxHeight() { return 0; }
        @Override public int getHeight() { return 0; }
        @Override public Renderable getBottom() { return null; }
        @Override public Renderable getMiddle() { return null; }
        @Override public Renderable getTop() { return null; }
    }

    static final class Ground extends AndroidTileObject implements net.runelite.api.GroundObject {
        Ground(int wx, int wy, int plane, int id, long tag) { super(wx, wy, plane, id, tag, 1, 1); }
        @Override int layer() { return 3; }
        @Override int boxHeight() { return 0; }
        @Override public Renderable getRenderable() { return null; }
    }
}
