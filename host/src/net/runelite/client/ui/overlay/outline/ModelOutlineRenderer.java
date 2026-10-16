package net.runelite.client.ui.overlay.outline;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Actor;
import net.runelite.api.GraphicsObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.Perspective;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;

/**
 * Same-name replacement for RuneLite's model outline renderer. Models aren't read on Android, so
 * the outline is the object's convex hull (for NPCs: a box over the footprint, see AndroidNPC),
 * stroked on the overlay canvas of the layer being rendered (set by the host's OverlayRenderer).
 */
@Singleton
public class ModelOutlineRenderer {
    /** The root overlay graphics of the frame being rendered; null outside overlay rendering. */
    public static volatile Graphics2D target;

    @Inject
    public ModelOutlineRenderer() {}

    private static void stroke(Shape shape, int width, Color color) {
        Graphics2D g = target;
        if (shape == null || g == null || color == null) return;
        Graphics2D c = (Graphics2D) g.create();
        try {
            c.setColor(color);
            c.setStroke(new BasicStroke(Math.max(1, width)));
            c.draw(shape);
        } finally {
            c.dispose();
        }
    }

    public void drawOutline(Actor actor, int outlineWidth, Color color, int feather) {
        if (actor != null) stroke(actor.getConvexHull(), outlineWidth, color);
    }

    public void drawOutline(TileObject object, int outlineWidth, Color color, int feather) {
        if (object == null) return;
        Shape s = object.getClickbox();
        if (s == null) s = object.getCanvasTilePoly();
        stroke(s, outlineWidth, color);
    }

    public void drawOutline(ItemLayer itemLayer, TileItem item, int outlineWidth, Color color, int feather) {
        if (itemLayer == null || net.runelite.host.Host.client() == null) return;
        Polygon p = Perspective.getCanvasTilePoly(net.runelite.host.Host.client(), itemLayer.getLocalLocation());
        stroke(p, outlineWidth, color);
    }

    public void drawOutline(GraphicsObject graphicsObject, int outlineWidth, Color color, int feather) {}

    public void drawOutline(RuneLiteObject runeLiteObject, int outlineWidth, Color color, int feather) {}
}
