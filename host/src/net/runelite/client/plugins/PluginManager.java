package net.runelite.client.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.runelite.client.config.Config;

/** Replaces RuneLite's PluginManager (same name) as a read-only view of the host's plugins. */
public class PluginManager {
    private final HostPluginManager host;

    public PluginManager(HostPluginManager host) {
        this.host = host;
    }

    public Collection<Plugin> getPlugins() { return Collections.unmodifiableList(host.getPlugins()); }
    public boolean isPluginEnabled(Plugin plugin) { return host.getPlugins().contains(plugin); }
    public boolean isPluginActive(Plugin plugin) { return host.getPlugins().contains(plugin); }
    public void setPluginEnabled(Plugin plugin, boolean enabled) {}
    public boolean startPlugin(Plugin plugin) { return false; }
    public boolean stopPlugin(Plugin plugin) { return false; }
    public List<Plugin> conflictsForPlugin(Plugin plugin) { return Collections.emptyList(); }
    public Config getPluginConfigProxy(Plugin plugin) { return null; }
    public List<Config> getPluginConfigProxies(Collection<Plugin> plugins) { return new ArrayList<>(); }
}
