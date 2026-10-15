package net.runelite.host;

import android.util.Log;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import dalvik.system.DexClassLoader;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.HostPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Plugin host entry points, called from native code (native/src/jni_bridge.cpp). */
public final class Host {
    public static final String TAG = "osrsrl-host";

    // Must match client_thread::EventType.
    private static final int CLIENT_TICK = 0, GAME_TICK = 1, GAME_STATE_CHANGED = 2, VARBIT_CHANGED = 3,
            NPC_SPAWNED = 4, NPC_DESPAWNED = 5, CHAT_MESSAGE = 6, WIDGET_LOADED = 7, WIDGET_CLOSED = 8,
            PLAYER_SPAWNED = 9, PLAYER_DESPAWNED = 10, ITEM_CONTAINER_CHANGED = 11, STAT_CHANGED = 12;

    private static final net.runelite.client.callback.ClientThread clientThread = new net.runelite.client.callback.ClientThread();
    private static volatile EventBus eventBus;
    private static Client client;
    private static final NpcCache npcCache = new NpcCache();
    private static final PlayerCache playerCache = new PlayerCache();
    private static final AndroidWorldView worldView = new AndroidWorldView(npcCache, playerCache);
    private static final ClientTick clientTick = new ClientTick();
    private static final net.runelite.client.ui.overlay.OverlayManager overlayManager =
            new net.runelite.client.ui.overlay.OverlayManager();
    private static final OverlayRenderer overlayRenderer = new OverlayRenderer(overlayManager);
    private static final GameTick gameTick = new GameTick();

    private Host() {}

    /** Called once on a background thread. `pluginDir` holds plugin jars (classes.dex inside). */
    public static void start(String pluginDir, String cacheDir) {
        Log.i(TAG, "host starting; plugins from " + pluginDir);
        client = new AndroidClient(worldView);
        EventBus bus = new EventBus();
        final HostPluginManager[] pluginManager = new HostPluginManager[1];
        final net.runelite.client.config.ConfigManager configManager = new net.runelite.client.config.ConfigManager(
                new File(new File(pluginDir).getParentFile(), "settings.properties"), bus::post);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(client);
                bind(EventBus.class).toInstance(bus);
                // Named constants RuneLiteModule provides on desktop.
                for (String flag : new String[] {"developerMode", "safeMode", "insecureWriteCredentials", "noupdate", "disableTelemetry"}) {
                    bindConstant().annotatedWith(com.google.inject.name.Names.named(flag)).to(false);
                }
                bind(File.class).annotatedWith(com.google.inject.name.Names.named("runeLiteDir")).toInstance(new File(pluginDir).getParentFile());
                bind(File.class).annotatedWith(com.google.inject.name.Names.named("sessionfile"))
                        .toInstance(new File(new File(pluginDir).getParentFile(), "session"));
                bind(String.class).annotatedWith(com.google.inject.name.Names.named("profile")).toInstance("default");
                bind(EventBus.class).annotatedWith(com.google.inject.name.Names.named("Deferred EventBus")).toInstance(bus);
                bind(net.runelite.client.callback.ClientThread.class).toInstance(clientThread);
                bind(net.runelite.client.ui.overlay.OverlayManager.class).toInstance(overlayManager);
                bind(net.runelite.client.config.ConfigManager.class).toInstance(configManager);
                bind(net.runelite.client.config.RuneLiteConfig.class)
                        .toInstance(configManager.getConfig(net.runelite.client.config.RuneLiteConfig.class));
                bind(net.runelite.client.config.ChatColorConfig.class)
                        .toInstance(configManager.getConfig(net.runelite.client.config.ChatColorConfig.class));
                bind(java.util.concurrent.ScheduledExecutorService.class)
                        .toInstance(java.util.concurrent.Executors.newSingleThreadScheduledExecutor());
                bind(net.runelite.client.callback.Hooks.class).toInstance(new net.runelite.client.callback.Hooks());
                bind(net.runelite.client.ui.ClientToolbar.class).toInstance(new net.runelite.client.ui.ClientToolbar());
                bind(net.runelite.client.game.SpriteManager.class).toInstance(new net.runelite.client.game.SpriteManager());
                bind(net.runelite.client.chat.ChatMessageManager.class).toInstance(new net.runelite.client.chat.ChatMessageManager());
                bind(net.runelite.client.game.chatbox.ChatboxPanelManager.class)
                        .toInstance(new net.runelite.client.game.chatbox.ChatboxPanelManager());
                bind(net.runelite.client.plugins.PluginManager.class).toProvider(
                        () -> new net.runelite.client.plugins.PluginManager(pluginManager[0]));
            }
        });
        // Some plugins call RuneLite.getInjector(); desktop main() sets this static.
        try {
            net.runelite.client.RuneLite.setInjector(injector);
        } catch (Throwable t) {
            Log.e(TAG, "could not set RuneLite.injector", t);
        }
        pluginManager[0] = new HostPluginManager(injector, bus);
        HostPluginManager plugins = pluginManager[0];
        File[] jars = new File(pluginDir).listFiles((d, n) -> n.endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) loadJar(plugins, jar, cacheDir);
        }
        eventBus = bus;
        Log.i(TAG, "host ready; " + plugins.getPlugins().size() + " plugin(s) running");
    }

    private static void loadJar(HostPluginManager plugins, File jar, String cacheDir) {
        try {
            // Android 14+ refuses writable dex files; harmless before that.
            File copy = new File(cacheDir, jar.getName());
            java.nio.file.Files.copy(jar.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            copy.setReadOnly();
            ClassLoader loader = new DexClassLoader(copy.getPath(), cacheDir, null, Host.class.getClassLoader());
            // Plugin classes are found by scanning the jar's class list (kept beside the dex
            // by tools/dex-plugin.sh) for @PluginDescriptor.
            try (ZipFile zip = new ZipFile(copy)) {
                ZipEntry list = zip.getEntry("plugin-classes.txt");
                if (list == null) {
                    Log.e(TAG, jar + ": no plugin-classes.txt");
                    return;
                }
                String[] names = new String(readAll(zip.getInputStream(list)), "UTF-8").split("\n");
                for (String name : names) {
                    name = name.trim();
                    if (name.isEmpty()) continue;
                    Class<?> c = loader.loadClass(name);
                    if (Plugin.class.isAssignableFrom(c) && c.isAnnotationPresent(PluginDescriptor.class)) {
                        plugins.start(c.asSubclass(Plugin.class));
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "failed to load " + jar, t);
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** Called on the game thread after every client cycle and for each derived event. */
    public static void onEvent(int type, int a, int b) {
        EventBus bus = eventBus;
        if (bus == null) return;
        switch (type) {
            case CLIENT_TICK:
                bus.post(clientTick);
                runQueued();
                try {
                    overlayRenderer.render();
                } catch (Throwable t) {
                    Log.e(TAG, "overlay rendering failed", t);
                }
                break;
            case GAME_TICK:
                bus.post(gameTick);
                clientThread.runTickEnd();
                break;
            case GAME_STATE_CHANGED: {
                GameStateChanged e = new GameStateChanged();
                GameState s = GameState.of(a);
                e.setGameState(s != null ? s : GameState.UNKNOWN);
                bus.post(e);
                break;
            }
            case NPC_SPAWNED: {
                AndroidNPC npc = npcCache.get(a);
                if (npc != null) bus.post(new NpcSpawned(npc));
                break;
            }
            case NPC_DESPAWNED: {
                AndroidNPC npc = npcCache.cached(a);
                if (npc != null) bus.post(new NpcDespawned(npc));
                break;
            }
            case CHAT_MESSAGE: {
                String[] m = Native.message(a);
                if (m == null) break;
                ChatMessageType chatType = ChatMessageType.of(b);
                int now = (int) (System.currentTimeMillis() / 1000);
                AndroidMessageNode node = new AndroidMessageNode(a, chatType, m[0], m[1], m[2], now);
                bus.post(new ChatMessage(node, chatType, m[0], m[2], m[1], now));
                break;
            }
            case PLAYER_SPAWNED: {
                AndroidPlayer p = playerCache.get(a);
                if (p != null) bus.post(new net.runelite.api.events.PlayerSpawned(p));
                break;
            }
            case PLAYER_DESPAWNED: {
                AndroidPlayer p = playerCache.cached(a);
                if (p != null) bus.post(new net.runelite.api.events.PlayerDespawned(p));
                break;
            }
            case ITEM_CONTAINER_CHANGED: {
                AndroidItemContainer c = AndroidItemContainer.of(a);
                if (c != null) bus.post(new net.runelite.api.events.ItemContainerChanged(a, c));
                break;
            }
            case STAT_CHANGED: {
                net.runelite.api.Skill[] all = net.runelite.api.Skill.values();
                if (a < 0 || a >= all.length) break;
                int[] sk = Native.skills();
                bus.post(new net.runelite.api.events.StatChanged(all[a], sk[50 + a], sk[25 + a], sk[a]));
                break;
            }
            case WIDGET_LOADED: {
                net.runelite.api.events.WidgetLoaded e = new net.runelite.api.events.WidgetLoaded();
                e.setGroupId(a);
                bus.post(e);
                break;
            }
            case WIDGET_CLOSED:
                bus.post(new net.runelite.api.events.WidgetClosed(a, 0, true));
                break;
            case VARBIT_CHANGED: {
                VarbitChanged e = new VarbitChanged();
                e.setVarpId(a);
                e.setVarbitId(-1);
                e.setValue(b);
                bus.post(e);
                break;
            }
        }
    }

    static AndroidPlayer localPlayer() {
        return playerCache.local();
    }

    static AndroidWorldView worldView() {
        return worldView;
    }

    /** RuneLite's ClientThread.invoke equivalent. */
    public static void invoke(Runnable r) {
        clientThread.invoke(r);
    }

    static void runQueued() {
        clientThread.runQueued();
    }
}
