package com.example.tickinfo;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
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

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TickInfoOverlay overlay;

    @Inject
    private ShapesOverlay shapes;

    @Inject
    private MarkerOverlay markers;

    @Inject
    private TickInfoConfig config;

    @Provides
    TickInfoConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TickInfoConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        log.info("config changed {}.{}: {} -> {}", e.getGroup(), e.getKey(), e.getOldValue(), e.getNewValue());
    }

    @Override
    protected void startUp() {
        log.info("Tick Info started; game state {}; config showMarkers={} npcColor={}", client.getGameState(),
                config.showMarkers(), config.npcColor());
        overlayManager.add(overlay);
        overlayManager.add(markers);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        overlayManager.remove(markers);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        log.info("game state -> {}", e.getGameState());
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        Player p = client.getLocalPlayer();
        // Varbit 8119 is the "has set a name" flag the test server sets at login.
        if (client.getTickCount() % 10 != 0) return;
        log.info("tick {} player at {} varbit8119={}", client.getTickCount(), p != null ? p.getWorldLocation() : null,
                client.getVarbitValue(8119));
        log.info("npcs: {}", client.getNpcs());
        log.info("players: {} local={}", client.getPlayers(), client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null);
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        log.info("chat [{}] name='{}' sender='{}': {}", e.getType(), e.getName(), e.getSender(), e.getMessage());
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e) {
        log.info("container changed: {}", e.getItemContainer());
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded e) {
        log.info("widget loaded: group {}", e.getGroupId());
        // chat_left (231): the NPC dialogue text is child 6 on desktop
        if (e.getGroupId() == 231) {
            for (int child = 0; child < 10; child++) {
                Widget w = client.getWidget(231, child);
                if (w != null && !w.getText().isEmpty())
                    log.info("  231:{} '{}' hidden={} bounds={} parent={}", child, w.getText(), w.isHidden(), w.getBounds(), w.getParentId());
            }
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed e) {
        log.info("widget closed: group {}", e.getGroupId());
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
