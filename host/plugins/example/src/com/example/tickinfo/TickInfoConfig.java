package com.example.tickinfo;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("tickinfo")
public interface TickInfoConfig extends Config {
    @ConfigItem(keyName = "showMarkers", name = "Show markers", description = "Mark NPCs and the player")
    default boolean showMarkers() { return true; }

    @ConfigItem(keyName = "npcColor", name = "NPC colour", description = "Marker colour for NPCs")
    default Color npcColor() { return Color.YELLOW; }
}
