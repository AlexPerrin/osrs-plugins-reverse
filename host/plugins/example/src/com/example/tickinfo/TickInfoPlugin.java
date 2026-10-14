package com.example.tickinfo;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A desktop-style RuneLite plugin, compiled only against runelite-api and the client jar.
 * Used to check that the Android host runs unmodified plugin code.
 */
@PluginDescriptor(name = "Tick Info", description = "Logs game ticks, position and varp changes")
public class TickInfoPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(TickInfoPlugin.class);

    @Inject
    private Client client;

    @Override
    protected void startUp() {
        log.info("Tick Info started; game state {}", client.getGameState());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        log.info("game state -> {}", e.getGameState());
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        Player p = client.getLocalPlayer();
        // Varbit 8119 is the "has set a name" flag the test server sets at login.
        log.info("tick {} player at {} varbit8119={}", client.getTickCount(), p != null ? p.getWorldLocation() : null,
                client.getVarbitValue(8119));
        if (client.getTickCount() % 10 == 0) log.info("npcs: {}", client.getNpcs());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned e) {
        log.info("npc spawned {} {} (id {}) at {}", e.getNpc().getIndex(), e.getNpc().getName(), e.getNpc().getId(),
                e.getNpc().getWorldLocation());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned e) {
        log.info("npc despawned {} {}", e.getNpc().getIndex(), e.getNpc().getName());
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged e) {
        log.info("varp {} = {}", e.getVarpId(), e.getValue());
    }
}
