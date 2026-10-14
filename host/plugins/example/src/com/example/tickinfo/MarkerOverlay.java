package com.example.tickinfo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Marks the player and NPCs through RuneLite's Perspective (checks the camera mapping). */
class MarkerOverlay extends Overlay {
    private final Client client;
    private final TickInfoConfig config;

    @Inject
    MarkerOverlay(Client client, TickInfoConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D g) {
        Player p = client.getLocalPlayer();
        if (p == null || !config.showMarkers()) return null;
        // Tile heights aren't mapped yet; the courtyard is flat, so use a fixed ground height.
        int z = -240;
        mark(g, p.getLocalLocation(), z, "you", Color.GREEN);
        for (NPC npc : client.getNpcs()) mark(g, npc.getLocalLocation(), z, npc.getName(), config.npcColor());
        return null;
    }

    private void mark(Graphics2D g, LocalPoint lp, int z, String label, Color color) {
        if (lp == null) return;
        Point pt = Perspective.localToCanvas(client, lp.getX(), lp.getY(), z);
        if (pt == null) return;
        g.setColor(color);
        g.fillOval(pt.getX() - 4, pt.getY() - 4, 8, 8);
        OverlayUtil.renderTextLocation(g, new Point(pt.getX() + 6, pt.getY()), label, color);
    }
}
