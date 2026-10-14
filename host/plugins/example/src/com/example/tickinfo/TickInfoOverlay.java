package com.example.tickinfo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** A standard RuneLite info panel: tick, position, NPC count. */
class TickInfoOverlay extends OverlayPanel {
    private final Client client;

    @Inject
    TickInfoOverlay(TickInfoPlugin plugin, Client client) {
        super(plugin);
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D g) {
        Player p = client.getLocalPlayer();
        panelComponent.getChildren().add(TitleComponent.builder().text("Tick Info").color(Color.GREEN).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Tick").right(String.valueOf(client.getTickCount())).build());
        if (p != null && p.getWorldLocation() != null) {
            panelComponent.getChildren().add(LineComponent.builder().left("Position")
                    .right(p.getWorldLocation().getX() + ", " + p.getWorldLocation().getY() + ", " + p.getWorldLocation().getPlane())
                    .build());
        }
        panelComponent.getChildren().add(LineComponent.builder().left("NPCs").right(String.valueOf(client.getNpcs().size())).build());
        return super.render(g);
    }
}
