package net.runelite.client.plugins;

import android.util.Log;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import net.runelite.host.Host;
import net.runelite.host.HostEventBus;

/**
 * Starts plugins the way RuneLite's PluginManager does: a child injector per plugin
 * (the plugin is its own Guice module), startUp(), then event-bus registration.
 * Lives in this package to set Plugin.injector, as RuneLite's PluginManager does.
 */
public final class HostPluginManager {
    private final Injector root;
    private final HostEventBus eventBus;
    private final List<Plugin> running = new ArrayList<>();

    public HostPluginManager(Injector root, HostEventBus eventBus) {
        this.root = root;
        this.eventBus = eventBus;
    }

    public void start(Class<? extends Plugin> type) {
        try {
            PluginDescriptor d = type.getAnnotation(PluginDescriptor.class);
            String name = d != null ? d.name() : type.getSimpleName();
            Plugin plugin = type.getDeclaredConstructor().newInstance();
            Injector child = root.createChildInjector(plugin);
            child.injectMembers(plugin);
            plugin.injector = child;
            plugin.startUp();
            eventBus.register(plugin);
            running.add(plugin);
            Log.i(Host.TAG, "started plugin " + name);
        } catch (Throwable t) {
            Log.e(Host.TAG, "plugin " + type.getName() + " failed to start", t);
        }
    }

    public List<Plugin> getPlugins() {
        return running;
    }
}
