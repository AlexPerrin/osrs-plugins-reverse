package net.runelite.client.plugins;

import android.util.Log;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import net.runelite.host.Host;
import net.runelite.client.eventbus.EventBus;

/**
 * Starts plugins the way RuneLite's PluginManager does: a child injector per plugin
 * (the plugin is its own Guice module), startUp(), then event-bus registration.
 * Lives in this package to set Plugin.injector, as RuneLite's PluginManager does.
 */
public final class HostPluginManager {
    private final Injector root;
    private final EventBus eventBus;
    private final List<Plugin> running = new ArrayList<>();

    public HostPluginManager(Injector root, EventBus eventBus) {
        this.root = root;
        this.eventBus = eventBus;
    }

    /** Starts `type` (and first its @PluginDependency plugins, as RuneLite does); null on failure. */
    public Plugin start(Class<? extends Plugin> type) {
        for (Plugin p : running) if (p.getClass() == type) return p;
        // As RuneLite's PluginManager.instantiate: each dependency is started first and its
        // getPublicModule() (the services it exposes, e.g. XpTrackerService) is installed in the
        // dependent plugin's injector, a child of the root injector.
        // (getAnnotationsByType came back empty on ART for FishingPlugin's single @PluginDependency.)
        List<PluginDependency> depList = new ArrayList<>();
        PluginDependency one = type.getAnnotation(PluginDependency.class);
        if (one != null) depList.add(one);
        PluginDependencies many = type.getAnnotation(PluginDependencies.class);
        if (many != null) for (PluginDependency d : many.value()) depList.add(d);
        if (depList.isEmpty()) for (PluginDependency d : type.getAnnotationsByType(PluginDependency.class)) depList.add(d);
        List<com.google.inject.Module> modules = new ArrayList<>();
        for (PluginDependency dep : depList) {
            Plugin p = start(dep.value());
            com.google.inject.Module m = p != null ? p.getPublicModule() : null;
            if (m == null) {
                Log.e(Host.TAG, "plugin " + type.getName() + ": dependency " + dep.value().getName() + " failed or exposes no services");
                return null;
            }
            modules.add(m);
        }
        try {
            PluginDescriptor d = type.getAnnotation(PluginDescriptor.class);
            String name = d != null ? d.name() : type.getSimpleName();
            Plugin plugin = type.getDeclaredConstructor().newInstance();
            // As RuneLite: the plugin is its own module, and its class is bound to this
            // instance so its overlays etc. can inject it. toInstance() injects its members.
            @SuppressWarnings("unchecked")
            Class<Plugin> cls = (Class<Plugin>) type;
            modules.add(plugin);
            modules.add(binder -> binder.bind(cls).toInstance(plugin));
            Injector child = root.createChildInjector(modules);
            plugin.injector = child;
            // As RuneLite's PluginManager: register the defaults of the plugin's config interfaces.
            net.runelite.client.config.ConfigManager configManager = root.getInstance(net.runelite.client.config.ConfigManager.class);
            for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
                if (m.getAnnotation(com.google.inject.Provides.class) == null) continue;
                if (!net.runelite.client.config.Config.class.isAssignableFrom(m.getReturnType())) continue;
                try {
                    configManager.setDefaultConfiguration((net.runelite.client.config.Config) child.getInstance(m.getReturnType()), false);
                } catch (Throwable t) {
                    Log.w(Host.TAG, name + ": config defaults failed: " + t);
                }
            }
            net.runelite.host.Host.runAsEdtAtStartup(() -> { plugin.startUp(); return null; });  // desktop: SwingUtil.syncExec
            eventBus.register(plugin);
            running.add(plugin);
            Log.i(Host.TAG, "started plugin " + name);
            return plugin;
        } catch (Throwable t) {
            Log.e(Host.TAG, "plugin " + type.getName() + " failed to start", t);
            return null;
        }
    }

    public List<Plugin> getPlugins() {
        return running;
    }
}
