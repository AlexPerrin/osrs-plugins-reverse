package net.runelite.client.config;

import android.util.Log;
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.client.events.ConfigChanged;

/**
 * Replaces RuneLite's ConfigManager (same name and the plugin-facing API): config interfaces
 * are proxies over a properties file ("group.key=value"), defaulting to the interface's
 * default methods. No profiles or cloud sync on Android.
 */
public class ConfigManager {
    public static final String RSPROFILE_GROUP = "rsprofile";
    public static final String RSPROFILE_DISPLAY_NAME = "displayName";
    public static final String RSPROFILE_TYPE = "type";
    private static final String TAG = "osrsrl-host";
    private static final String RS_PROFILE = "default";

    private final Properties properties = new Properties();
    private final File file;
    private final Consumer<Object> eventSink;

    public ConfigManager(File file, Consumer<Object> eventSink) {
        this.file = file;
        this.eventSink = eventSink;
        load();
    }

    public void load() {
        if (file == null || !file.exists()) return;
        try (InputStream in = new FileInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            Log.e(TAG, "config load failed", e);
        }
    }

    private synchronized void save() {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStream out = new FileOutputStream(file)) {
                properties.store(out, "RuneLite plugin settings (group.key=value)");
            }
        } catch (IOException e) {
            Log.e(TAG, "config save failed", e);
        }
    }

    public static String getWholeKey(String group, String profile, String key) {
        return profile == null ? group + "." + key : group + "." + profile + "." + key;
    }

    // --- raw string access ---

    public String getConfiguration(String group, String key) { return getConfiguration(group, null, key); }
    public String getConfiguration(String group, String profile, String key) {
        String whole = getWholeKey(group, profile, key);
        String v = properties.getProperty(whole);
        return v != null ? v : defaults.get(whole);
    }

    /** Config interface defaults (setDefaultConfiguration), kept in memory, not saved. */
    private final java.util.Map<String, String> defaults = new java.util.concurrent.ConcurrentHashMap<>();
    public String getRSProfileConfiguration(String group, String key) { return getConfiguration(group, rsKey(), key); }

    public <T> T getConfiguration(String group, String key, Type type) { return getConfiguration(group, null, key, type); }
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(String group, String profile, String key, Type type) {
        String v = getConfiguration(group, profile, key);
        return v == null ? null : (T) stringToObject(v, type);
    }
    public <T> T getRSProfileConfiguration(String group, String key, Type type) { return getConfiguration(group, rsKey(), key, type); }

    public void setConfiguration(String group, String key, String value) { setConfiguration(group, null, key, value); }
    public void setConfiguration(String group, String profile, String key, String value) {
        String whole = getWholeKey(group, profile, key);
        Object old = value == null ? properties.remove(whole) : properties.setProperty(whole, value);
        if (old == null ? value == null : old.equals(value)) return;
        save();
        ConfigChanged e = new ConfigChanged();
        e.setGroup(group);
        e.setProfile(profile);
        e.setKey(key);
        e.setOldValue((String) old);
        e.setNewValue(value);
        if (eventSink != null) eventSink.accept(e);
    }
    public <T> void setConfiguration(String group, String key, T value) { setConfiguration(group, null, key, objectToString(value)); }
    public <T> void setConfiguration(String group, String profile, String key, T value) {
        setConfiguration(group, profile, key, objectToString(value));
    }
    public <T> void setRSProfileConfiguration(String group, String key, T value) { setConfiguration(group, rsKey(), key, value); }

    public void unsetConfiguration(String group, String key) { setConfiguration(group, null, key, (String) null); }
    public void unsetConfiguration(String group, String profile, String key) { setConfiguration(group, profile, key, (String) null); }
    public void unsetRSProfileConfiguration(String group, String key) { unsetConfiguration(group, rsKey(), key); }

    public List<String> getConfigurationKeys(String prefix) {
        List<String> out = new ArrayList<>();
        for (String k : properties.stringPropertyNames()) if (k.startsWith(prefix)) out.add(k);
        Collections.sort(out);
        return out;
    }
    public List<String> getRSProfileConfigurationKeys(String group, String profile, String prefix) {
        return getConfigurationKeys(getWholeKey(group, profile, prefix));
    }

    public String getRSProfileKey() { return rsKey(); }
    private static String rsKey() { return RSPROFILE_GROUP + "." + RS_PROFILE; }

    /**
     * As RuneLite (run for each plugin's @Provides config before startUp): the interface's default
     * values become what getConfiguration returns for unset keys (plugins read their own config
     * through the raw API, e.g. Idle Notifier's migration).
     */
    public <T extends Config> void setDefaultConfiguration(T config, boolean override) {
        for (Class<?> iface : config.getClass().getInterfaces()) {
            ConfigGroup group = iface.getAnnotation(ConfigGroup.class);
            if (group == null) continue;
            for (Method m : iface.getMethods()) {
                ConfigItem item = m.getAnnotation(ConfigItem.class);
                if (item == null || m.getParameterCount() != 0 || !m.isDefault()) continue;
                try {
                    Object v = invokeDefault(config, m, null);
                    if (v != null) defaults.put(getWholeKey(group.value(), null, item.keyName()), objectToString(v));
                } catch (Throwable t) {
                    Log.w(TAG, "default for " + group.value() + "." + item.keyName() + " failed: " + t);
                }
            }
        }
    }
    public void sendConfig() {}

    // --- config interfaces ---

    @SuppressWarnings("unchecked")
    public <T extends Config> T getConfig(Class<T> type) {
        ConfigGroup group = type.getAnnotation(ConfigGroup.class);
        if (group == null) throw new IllegalArgumentException("Not a config group: " + type);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString": return "Config[" + group.value() + "]";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                }
            }
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item == null) return invokeDefault(proxy, method, args);
            if (args != null && args.length == 1) {  // setter: void key(value)
                setConfiguration(group.value(), item.keyName(), args[0]);
                return null;
            }
            String stored = getConfiguration(group.value(), item.keyName());
            if (stored != null) {
                try {
                    return stringToObject(stored, method.getGenericReturnType());
                } catch (RuntimeException e) {
                    Log.w(TAG, "bad config value " + group.value() + "." + item.keyName() + "=" + stored);
                }
            }
            return invokeDefault(proxy, method, args);
        });
    }

    // Calls an interface default method on the proxy. Android has no privateLookupIn;
    // a full-privilege Lookup comes from its private constructor.
    private static Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
        if (!method.isDefault()) return primitiveDefault(method.getReturnType());
        Class<?> declaring = method.getDeclaringClass();
        Constructor<MethodHandles.Lookup> c = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        c.setAccessible(true);
        MethodHandles.Lookup lookup = c.newInstance(declaring, -1);
        return lookup.unreflectSpecial(method, declaring).bindTo(proxy).invokeWithArguments(args == null ? new Object[0] : args);
    }

    private static Object primitiveDefault(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == double.class) return 0d;
        if (t == float.class) return 0f;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return (char) 0;
        return null;
    }

    // --- value conversion (the common subset of RuneLite's) ---

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object stringToObject(String s, Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            if (pt.getRawType() == Set.class && pt.getActualTypeArguments()[0] instanceof Class
                    && ((Class<?>) pt.getActualTypeArguments()[0]).isEnum()) {
                Class<? extends Enum> e = (Class<? extends Enum>) pt.getActualTypeArguments()[0];
                EnumSet set = EnumSet.noneOf(e);
                for (String part : s.split(",")) if (!part.isEmpty()) set.add(Enum.valueOf(e, part.trim()));
                return set;
            }
            return s;
        }
        Class<?> c = (Class<?>) type;
        if (c == String.class) return s;
        if (c == int.class || c == Integer.class) return Integer.parseInt(s);
        if (c == long.class || c == Long.class) return Long.parseLong(s);
        if (c == double.class || c == Double.class) return Double.parseDouble(s);
        if (c == float.class || c == Float.class) return Float.parseFloat(s);
        if (c == boolean.class || c == Boolean.class) return Boolean.parseBoolean(s);
        if (c == Notification.class) {
            boolean on = s.equals("true") || s.equals("ON") || s.replace(" ", "").contains("\"enabled\":true");
            return on ? Notification.ON : Notification.OFF;
        }
        if (c == Color.class) return new Color((int) Long.parseLong(s.startsWith("#") ? s.substring(1) : s, 16), true);
        if (c == Duration.class) return Duration.ofMillis(Long.parseLong(s));
        if (c == Instant.class) return Instant.parse(s);
        if (c.isEnum()) return Enum.valueOf((Class<? extends Enum>) c, s);
        return s;
    }

    static String objectToString(Object o) {
        if (o == null) return null;
        if (o instanceof Color) return String.format("%08x", ((Color) o).getRGB());
        // Only on/off is kept (desktop stores the whole object as JSON; sounds/tray don't apply here).
        if (o instanceof Notification) return ((Notification) o).isEnabled() ? "true" : "false";
        if (o instanceof Enum) return ((Enum<?>) o).name();
        if (o instanceof Duration) return Long.toString(((Duration) o).toMillis());
        if (o instanceof Set) {
            StringBuilder sb = new StringBuilder();
            for (Object e : (Set<?>) o) sb.append(sb.length() == 0 ? "" : ",").append(e instanceof Enum ? ((Enum<?>) e).name() : e);
            return sb.toString();
        }
        return o.toString();
    }
}
