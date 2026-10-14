package com.example.tickinfo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Exercises the Graphics2D shim: fills, strokes, alpha, polygons, text. */
class ShapesOverlay extends Overlay {
    @Inject
    ShapesOverlay() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Polygon tile = new Polygon(new int[] {600, 680, 600, 520}, new int[] {420, 460, 500, 460}, 4);
        OverlayUtil.renderPolygon(g, tile, Color.CYAN);
        g.setColor(new Color(255, 0, 0, 120));
        g.fill(new Ellipse2D.Double(560, 300, 80, 80));
        g.setStroke(new BasicStroke(3));
        g.setColor(Color.YELLOW);
        g.drawRect(500, 280, 200, 240);
        OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(560, 270), "Graphics2D on Android", Color.WHITE);
        return null;
    }
}
