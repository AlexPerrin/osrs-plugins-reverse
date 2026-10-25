package com.osrsrl.apiprobe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Named;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Rasterizer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls every abstract method of every net.runelite.api interface on a live instance, by reflection,
 * and writes one row per method to <runeLiteDir>/api-probe.tsv (tools/api-probe.sh). Results:
 * pass (a value of the declared type, no exception; setters: set, read back, restored),
 * throw (exception), inert (resolves to a generated *Stub/*Bag or an Inert path), mismatch (a setter
 * read back something else), unchecked (a native-hook action whose effect the API cannot read),
 * unreached (no live instance) and skipped (destructive or not restorable; the reason is the evidence).
 * Runs on the game thread, ROWS_PER_TICK rows per GameTick, once per load.
 */
@PluginDescriptor(name = "API Probe", description = "Probes every net.runelite.api method (tools/api-probe.sh)", enabledByDefault = true)
public class ApiProbePlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(ApiProbePlugin.class);
    private static final int ROWS_PER_TICK = 40;
    private static final String API = "net.runelite.api.";

    /** Interfaces the client calls rather than implements: plugins or RuneLite provide them. */
    static final Map<String, String> EXCLUDED = new LinkedHashMap<>();
    static {
        EXCLUDED.put(API + "hooks.Callbacks", "RuneLite's callbacks: the game calls them, plugins never do");
        EXCLUDED.put(API + "hooks.DrawCallbacks", "a GPU plugin implements it and the client calls it");
        EXCLUDED.put(API + "widgets.JavaScriptCallback", "a plugin's widget listener: the client calls it");
        EXCLUDED.put(API + "TileFunction", "a plugin's minimap tile drawer: the client calls it");
    }

    /** Destructive or session-ending: never called. */
    static final Map<String, String> SKIP = new HashMap<>();
    static {
        String hop = "world hop ends the session";
        SKIP.put("Client.changeWorld", hop);
        SKIP.put("Client.hopToWorld", hop);
        SKIP.put("Client.openWorldHopper", hop);
        SKIP.put("Client.setGameState", "a game state change logs out or reloads the scene");
        SKIP.put("Client.stopNow", "stops the client");
        SKIP.put("Client.menuAction", "sends the game's menu action to the server (walk, take, drop, trade)");
        SKIP.put("Client.runScript", "a client script can send packets to the server");
        SKIP.put("ScriptEvent.run", "runs a client script, which can send packets to the server");
        SKIP.put("Client.openInterface", "opens a game interface the server does not know about");
        SKIP.put("Client.closeInterface", "closes a game interface the server tracks");
        SKIP.put("Client.changeMemoryMode", "low-memory mode reloads the scene");
        String login = "login credential field";
        SKIP.put("Client.setUsername", login);
        SKIP.put("Client.setPassword", login);
        SKIP.put("Client.setOtp", login);
        String engine = "applet lifecycle: the app runs it once at start";
        SKIP.put("GameEngine.initialize", engine);
        SKIP.put("GameEngine.resizeCanvas", engine);
        SKIP.put("GameEngine.setConfiguration", engine);
        SKIP.put("GameEngine.unblockStartup", engine);
        SKIP.put("ClientConfiguration.onError", "reports a fatal client error");
        String scene = "removes the game's scene data, not restorable";
        SKIP.put("Scene.removeTile", scene);
        SKIP.put("Scene.removeGameObject", scene);
        String coll = "mutates a game-owned collection, not restorable";
        SKIP.put("Deque.clear", coll);
        SKIP.put("Deque.addLast", coll);
        SKIP.put("IterableHashTable.put", coll);
    }

    @Inject private Client client;
    @Inject private EventBus eventBus;
    @Inject @Named("runeLiteDir") private File dir;

    private final Map<Class<?>, Object> live = new HashMap<>();
    private final Set<String> nativeImpl = new HashSet<>();
    private final Map<String, List<Row>> byIface = new TreeMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> deferred = new ArrayList<>();
    private String apiJar = "?", build = "?";
    private Iterator<Row> current;
    private Object currentInstance;
    private boolean seeded, retried, done;

    static final class Row {
        String iface, name, desc, result, evidence = "";
        Method m;
        boolean nat;
    }

    @Override
    protected void startUp() throws Exception {
        ClassLoader api = Client.class.getClassLoader();
        try (BufferedReader r = reader(getClass().getClassLoader().getResourceAsStream("api-methods.txt"))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith("# ")) { apiJar = line.substring(2); continue; }
                String[] f = line.split("\t");
                if (EXCLUDED.containsKey(f[0])) continue;
                Row row = new Row();
                row.iface = f[0];
                row.name = f[1];
                row.desc = f[2];
                for (Method m : Class.forName(f[0], false, api).getDeclaredMethods())
                    if (m.getName().equals(f[1]) && desc(m).equals(f[2])) row.m = m;
                if (row.m == null) { row.result = "throw"; row.evidence = "method not found in the client's API classes"; }
                rows.add(row);
                byIface.computeIfAbsent(f[0], k -> new ArrayList<>()).add(row);
            }
        }
        try (BufferedReader r = reader(api.getResourceAsStream("api-native.txt"))) {
            for (String line; (line = r.readLine()) != null; ) nativeImpl.add(line);
        } catch (Exception e) {
            log.warn("api-probe: no api-native.txt in host.jar: {}", e.toString());
        }
        try (BufferedReader r = reader(api.getResourceAsStream("osrsrl-build.txt"))) {
            build = r.readLine();
        } catch (Exception e) {
            log.warn("api-probe: no osrsrl-build.txt in host.jar");
        }
        log.info("api-probe: {} rows, build {}", rows.size(), build);
    }

    private static BufferedReader reader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Subscribe
    public void onGameTick(GameTick t) {
        if (done || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
        try {
            if (!seeded) { seed(); seeded = true; return; }
            for (int n = 0; n < ROWS_PER_TICK; n++) {
                if (current == null || !current.hasNext()) {
                    current = null;
                    if (!next()) { finish(); return; }
                    continue;
                }
                Row row = current.next();
                if (row.result != null) continue;
                log.info("api-probe: {}.{}{}", row.iface, row.name, row.desc);
                probe(row, currentInstance);
            }
        } catch (Throwable e) {
            log.error("api-probe failed", e);
            done = true;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, "api-probe.tsv")), StandardCharsets.UTF_8)) {
                w.write("# error: " + e + "\n");  // ends tools/api-probe.sh's wait; it reports the missing rows
            } catch (Exception ignored) {
                // logcat has the failure
            }
        }
    }

    /** The next interface with a live instance and unprobed rows; then one retry of deferred rows. */
    private boolean next() {
        for (Map.Entry<String, List<Row>> e : byIface.entrySet()) {
            Object inst = instance(e.getKey());
            if (inst == null) continue;
            current = byIface.remove(e.getKey()).iterator();
            currentInstance = inst;
            return true;  // the loop ends here, so removing the entry is safe
        }
        if (!retried && !deferred.isEmpty()) {
            retried = true;
            for (Row r : deferred) {
                r.result = null;
                byIface.computeIfAbsent(r.iface, k -> new ArrayList<>()).add(r);
            }
            deferred.clear();
            return next();
        }
        return false;
    }

    private Object instance(String iface) {
        try {
            return live.get(Class.forName(iface, false, Client.class.getClassLoader()));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // ---- live instances ----

    /** Instances the safe-argument getters don't reach: the local player first (it is the Actor), a root widget, the scene's objects. */
    private void seed() {
        register(client, 0);
        try {
            seedLive();
        } catch (Throwable e) {
            log.warn("api-probe: seeding stopped: {}", e.toString());
        }
        log.info("api-probe: seeded {} live types", live.size());
    }

    private void seedLive() {
        Player me = client.getLocalPlayer();
        register(me, 0);
        register(me.getWorldLocation(), 0);
        register(me.getLocalLocation(), 0);
        register(new net.runelite.api.Point(0, 0), 0);
        // createChild/deleteAllChildren run on this widget, so it must hold no dynamic children of the game's.
        Widget[] roots = client.getWidgetRoots();
        if (roots != null)
            for (Widget w : roots)
                if (w != null && (w.getDynamicChildren() == null || w.getDynamicChildren().length == 0)) { register(w, 0); break; }
        register(client.getItemContainer(93), 0);  // inventory
        register(client.getMenu(), 0);
        register(client.getTopLevelWorldView(), 0);
        for (Object n : client.getTopLevelWorldView().npcs()) { register(n, 0); break; }
        for (net.runelite.api.NPC n : client.getTopLevelWorldView().npcs()) {  // overrides are rare: any NPC's
            register(n.getModelOverrides(), 0);
            register(n.getChatheadOverrides(), 0);
        }
        Scene scene = client.getTopLevelWorldView().getScene();
        register(scene, 0);
        LocalPoint lp = me.getLocalLocation();
        int plane = client.getTopLevelWorldView().getPlane();
        Tile[][][] tiles = scene.getTiles();
        register(tiles[plane][lp.getSceneX()][lp.getSceneY()], 0);
        List<java.util.function.Function<Tile, Object>> getters = new ArrayList<>(Arrays.asList(Tile::getGameObjects, Tile::getWallObject,
            Tile::getDecorativeObject, Tile::getGroundObject, Tile::getItemLayer, Tile::getGroundItems, Tile::getSceneTilePaint, Tile::getSceneTileModel));
        for (Tile[] col : tiles[plane]) {
            for (Tile tile : col) {
                if (tile == null) continue;
                for (Iterator<java.util.function.Function<Tile, Object>> it = getters.iterator(); it.hasNext(); ) {
                    try {
                        register(it.next().apply(tile), 0);
                    } catch (RuntimeException e) {
                        it.remove();  // a getter the host lacks: its rows say so
                    }
                }
            }
        }
    }

    /** Records `v` (and the elements of arrays, iterables and maps) as the live instance of each of its types not yet held. */
    private void register(Object v, int depth) {
        if (v == null || depth > 3) return;
        Class<?> c = v.getClass();
        if (c.isArray()) {
            if (c.getComponentType().isPrimitive()) return;
            for (int i = 0, n = Array.getLength(v); i < n; i++) register(Array.get(v, i), depth + 1);
            return;
        }
        if (v instanceof Map) {
            int n = 0;
            for (Object o : ((Map<?, ?>) v).values()) if (n++ < 64) register(o, depth + 1);
            return;
        }
        boolean inert = inert(v);
        for (Class<?> t : types(c)) {
            Object held = live.get(t);
            if (held == null || (inert(held) && !inert)) live.put(t, v);
        }
        if (v instanceof Iterable && !(v instanceof Collection && ((Collection<?>) v).size() > 4096)) {
            int n = 0;
            try {
                for (Object o : (Iterable<?>) v) if (n++ < 64) register(o, depth + 1); else break;
            } catch (Throwable e) {
                // a lazily backed iterable can fail; the instance itself is registered
            }
        }
    }

    private static Set<Class<?>> types(Class<?> c) {
        Set<Class<?>> out = new HashSet<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            out.add(k);
            addInterfaces(k, out);
        }
        return out;
    }

    private static void addInterfaces(Class<?> c, Set<Class<?>> out) {
        for (Class<?> i : c.getInterfaces()) if (out.add(i)) addInterfaces(i, out);
    }

    static boolean inert(Object v) {
        if (v == null || !Proxy.isProxyClass(v.getClass())) return false;
        try {
            return String.valueOf(v).startsWith("Inert ");
        } catch (Throwable e) {
            return false;
        }
    }

    // ---- one row ----

    private void probe(Row row, Object inst) {
        String key = simple(row.iface) + "." + row.name;
        Method m = row.m;
        Method impl;
        try {
            impl = inst.getClass().getMethod(m.getName(), m.getParameterTypes());
        } catch (NoSuchMethodException e) {
            impl = m;
        }
        String decl = impl.getDeclaringClass().getName();
        row.nat = nativeImpl.contains(decl + "\t" + impl.getName() + "\t" + desc(impl));
        String inertBy = inert(inst) ? "Inert proxy" : isGenerated(impl.getDeclaringClass()) ? impl.getDeclaringClass().getSimpleName() : null;
        if (SKIP.containsKey(key)) { set(row, "skipped", SKIP.get(key)); return; }
        try {
            Special s = SPECIAL.get(key + m.getParameterCount());
            if (s != null) s.run(this, row, inst);
            else if (setter(m) != null) roundTrip(row, inst, setter(m));
            else if (indexedGetter(m) != null) indexedRoundTrip(row, inst, indexedGetter(m));
            else if (m.getName().startsWith("set") && m.getReturnType() == void.class) set(row, "skipped", "setter without a getter to read back and restore");
            else call(row, inst);
        } catch (Missing e) {
            if (!retried) { row.result = "deferred"; deferred.add(row); return; }
            set(row, "unreached", "no live " + e.getMessage() + " for an argument");
            return;
        } catch (Throwable e) {
            Throwable c = e instanceof InvocationTargetException ? e.getCause() : e;
            set(row, "throw", c.getClass().getName() + ": " + c.getMessage());
        }
        if (inertBy != null && !"skipped".equals(row.result)) set(row, "inert", inertBy + " (" + row.result + ": " + row.evidence + ")");
    }

    private static boolean isGenerated(Class<?> c) {
        String n = c.getSimpleName();
        return n.endsWith("Stub") || n.endsWith("Bag") || n.contains("Inert");
    }

    private void call(Row row, Object inst) throws Exception {
        Object v = row.m.invoke(inst, args(row));
        Class<?> r = row.m.getReturnType();
        if (r == void.class) {
            if (row.nat) set(row, "unchecked", "native hook; its effect is not readable through the API");
            else set(row, "pass", "returned");
            return;
        }
        register(v, 0);
        if (inert(v)) { set(row, "inert", "returned an Inert " + r.getSimpleName()); return; }
        if (!r.isPrimitive() && v != null && !r.isInstance(v)) { set(row, "mismatch", "returned " + v.getClass().getName()); return; }
        set(row, "pass", describe(v));
    }

    /** setX(v) with a getter getX()/isX(): set an altered value, read it back, restore, read again. */
    private void roundTrip(Row row, Object inst, Method get) throws Exception {
        Object old = get.invoke(inst);
        Object alt = alter(old, row.m.getParameterTypes()[0]);
        row.m.invoke(inst, alt);
        Object read = get.invoke(inst);
        row.m.invoke(inst, old);
        Object back = get.invoke(inst);
        String ev = get.getName() + " " + describe(old) + " -> set " + describe(alt) + " -> " + describe(read) + " -> restored " + describe(back);
        set(row, same(read, alt) && same(back, old) ? "pass" : "mismatch", ev);
    }

    /** setX(k, v) with a getter getX(k)/getXValue(k). */
    private void indexedRoundTrip(Row row, Object inst, Method get) throws Exception {
        int k = KEYS.getOrDefault(simple(row.iface) + "." + row.name, 0);
        Object old = get.invoke(inst, k);
        Object alt = alter(old, row.m.getParameterTypes()[1]);
        row.m.invoke(inst, k, alt);
        Object read = get.invoke(inst, k);
        row.m.invoke(inst, k, old);
        Object back = get.invoke(inst, k);
        String ev = get.getName() + "(" + k + ") " + describe(old) + " -> set " + describe(alt) + " -> " + describe(read) + " -> restored " + describe(back);
        set(row, same(read, alt) && same(back, old) ? "pass" : "mismatch", ev);
    }

    /** Keys for indexed setters: ids the host's own set hook was verified with (docs/plugin-host.md). */
    static final Map<String, Integer> KEYS = new HashMap<>();
    static {
        KEYS.put("Client.setVarbit", 8119);
        KEYS.put("Client.setVarpValue", 1000);
        KEYS.put("Client.setVarpLongValue", 1000);
        KEYS.put("Client.setVarcIntValue", 1000);
        KEYS.put("Client.setVarcStrValue", 1001);
    }

    private static Method setter(Method m) {
        if (m.getParameterCount() != 1) return null;
        String n = m.getName();
        Class<?> p = m.getParameterTypes()[0];
        List<String> names = new ArrayList<>();
        if (n.startsWith("set") && n.length() > 3) {
            String suf = n.substring(3);
            names.add("get" + suf);
            names.add("is" + suf);
            if (suf.startsWith("Is")) names.add("is" + suf.substring(2));
        } else if (m.getReturnType() == m.getDeclaringClass()) {
            names.add(n);  // builder style: MenuEntry.onClick(Consumer) / onClick()
        } else {
            return null;
        }
        for (String g : names) {
            try {
                Method get = m.getDeclaringClass().getMethod(g);
                if (box(get.getReturnType()) == box(p) || p.isAssignableFrom(get.getReturnType())) return get;
            } catch (NoSuchMethodException e) {
                // next name
            }
        }
        return null;
    }

    private static Method indexedGetter(Method m) {
        if (!m.getName().startsWith("set") || m.getParameterCount() != 2 || m.getParameterTypes()[0] != int.class) return null;
        String suf = m.getName().substring(3);
        Class<?> p = m.getParameterTypes()[1];
        for (String g : new String[] {"get" + suf, "get" + suf + "Value"}) {
            try {
                Method get = m.getDeclaringClass().getMethod(g, int.class);
                if (box(get.getReturnType()) == box(p)) return get;
            } catch (NoSuchMethodException e) {
                // next name
            }
        }
        return null;
    }

    // ---- special effect checks ----

    interface Special {
        void run(ApiProbePlugin p, Row row, Object inst) throws Exception;
    }

    static final Map<String, Special> SPECIAL = new HashMap<>();
    static {
        // Hint arrows: set, observe hasHintArrow and its target, clear, observe it gone.
        Special arrow = (p, row, inst) -> {
            Client c = (Client) inst;
            if (c.hasHintArrow()) { p.set(row, "skipped", "a hint arrow was already set; the probe would lose it"); return; }
            Object target = row.name.equals("clearHintArrow") ? c.getLocalPlayer().getWorldLocation() : p.arg(row.m.getParameterTypes()[0]);
            Method set = Client.class.getMethod("setHintArrow", target instanceof WorldPoint ? WorldPoint.class : row.m.getParameterTypes()[0]);
            set.invoke(c, target);
            boolean on = c.hasHintArrow();
            String ev = "set " + describe(target) + ": hasHintArrow " + on + " type " + c.getHintArrowType() + " point " + describe(c.getHintArrowPoint())
                + " npc " + describe(c.getHintArrowNpc()) + " player " + describe(c.getHintArrowPlayer());
            c.clearHintArrow();
            boolean off = !c.hasHintArrow();
            p.set(row, on && off ? "pass" : "mismatch", ev + "; cleared: hasHintArrow " + !off);
        };
        SPECIAL.put("Client.setHintArrow1", arrow);
        SPECIAL.put("Client.clearHintArrow0", arrow);
        // The game reads chat lines it owns; the probe's line says what it is.
        SPECIAL.put("Client.addChatMessage4", (p, row, inst) -> {
            Object v = row.m.invoke(inst, ChatMessageType.GAMEMESSAGE, "", "api-probe: addChatMessage", null);
            p.set(row, v != null ? "pass" : "mismatch", describe(v) + " " + (v != null ? ((net.runelite.api.MessageNode) v).getValue() : ""));
            p.register(v, 0);
        });
        SPECIAL.put("Client.addChatMessage5", (p, row, inst) -> {
            Object v = row.m.invoke(inst, ChatMessageType.GAMEMESSAGE, "", "api-probe: addChatMessage", null, false);
            p.set(row, v != null ? "pass" : "mismatch", describe(v) + " " + (v != null ? ((net.runelite.api.MessageNode) v).getValue() : ""));
        });
        // Posing: the result's vertices against the input model's, so the evidence shows the sequence moved them.
        SPECIAL.put("Client.applyTransformations5", (p, row, inst) -> {
            Object[] a = p.args(row);
            net.runelite.api.Model in = (net.runelite.api.Model) a[0];
            net.runelite.api.Model out = (net.runelite.api.Model) row.m.invoke(inst, a);
            p.register(out, 0);
            if (out == null) { p.set(row, "mismatch", "null for sequence " + describe(a[1] == null ? null : ((net.runelite.api.Animation) a[1]).getId())); return; }
            int moved = 0, n = Math.min(in.getVerticesCount(), out.getVerticesCount());
            for (int i = 0; i < n; i++)
                if (in.getVerticesX()[i] != out.getVerticesX()[i] || in.getVerticesY()[i] != out.getVerticesY()[i] || in.getVerticesZ()[i] != out.getVerticesZ()[i]) moved++;
            p.set(row, moved > 0 ? "pass" : "mismatch", describe(out) + ": sequence " + ((net.runelite.api.Animation) a[1]).getId() + " frame 0 moved " + moved + " of " + n + " vertices");
        });
        // Widget position, size and actions: set altered values, read back, restore.
        SPECIAL.put("Widget.setPos2", (p, row, inst) -> {
            Widget w = (Widget) inst;
            int x = w.getOriginalX(), y = w.getOriginalY();
            w.setPos(x + 1, y + 1);
            int rx = w.getOriginalX(), ry = w.getOriginalY();
            w.setPos(x, y);
            p.set(row, rx == x + 1 && ry == y + 1 && w.getOriginalX() == x ? "pass" : "mismatch", "original " + x + "," + y + " -> " + rx + "," + ry + " -> restored " + w.getOriginalX() + "," + w.getOriginalY());
        });
        SPECIAL.put("Widget.setPos4", (p, row, inst) -> {
            Widget w = (Widget) inst;
            int x = w.getOriginalX(), y = w.getOriginalY(), xm = w.getXPositionMode(), ym = w.getYPositionMode();
            w.setPos(x + 1, y + 1, xm, ym);
            int rx = w.getOriginalX(), ry = w.getOriginalY();
            w.setPos(x, y, xm, ym);
            p.set(row, rx == x + 1 && ry == y + 1 && w.getOriginalX() == x ? "pass" : "mismatch", "original " + x + "," + y + " modes " + xm + "," + ym + " -> " + rx + "," + ry + " -> restored " + w.getOriginalX() + "," + w.getOriginalY());
        });
        SPECIAL.put("Widget.setSize2", (p, row, inst) -> {
            Widget w = (Widget) inst;
            int x = w.getOriginalWidth(), y = w.getOriginalHeight();
            w.setSize(x + 1, y + 1);
            int rx = w.getOriginalWidth(), ry = w.getOriginalHeight();
            w.setSize(x, y);
            p.set(row, rx == x + 1 && ry == y + 1 && w.getOriginalWidth() == x ? "pass" : "mismatch", "original " + x + "x" + y + " -> " + rx + "x" + ry + " -> restored " + w.getOriginalWidth() + "x" + w.getOriginalHeight());
        });
        SPECIAL.put("Widget.setSize4", (p, row, inst) -> {
            Widget w = (Widget) inst;
            int x = w.getOriginalWidth(), y = w.getOriginalHeight(), xm = w.getWidthMode(), ym = w.getHeightMode();
            w.setSize(x + 1, y + 1, xm, ym);
            int rx = w.getOriginalWidth(), ry = w.getOriginalHeight();
            w.setSize(x, y, xm, ym);
            p.set(row, rx == x + 1 && ry == y + 1 && w.getOriginalWidth() == x ? "pass" : "mismatch", "original " + x + "x" + y + " modes " + xm + "," + ym + " -> " + rx + "x" + ry + " -> restored " + w.getOriginalWidth() + "x" + w.getOriginalHeight());
        });
        SPECIAL.put("Widget.setAction2", (p, row, inst) -> {
            Widget w = (Widget) inst;
            String[] a = w.getActions();
            String old = a != null && a.length > 0 ? a[0] : null;
            w.setAction(0, "api-probe");
            String[] r = w.getActions();
            String read = r != null && r.length > 0 ? r[0] : null;
            w.setAction(0, old);
            p.set(row, "api-probe".equals(read) ? "pass" : "mismatch", "action 0 " + describe(old) + " -> " + describe(read) + " -> restored " + describe(w.getActions() != null && w.getActions().length > 0 ? w.getActions()[0] : null));
        });
        SPECIAL.put("Widget.clearActions0", (p, row, inst) -> {
            Widget w = (Widget) inst;
            String[] a = w.getActions();
            w.clearActions();
            String[] r = w.getActions();
            boolean cleared = r == null || Arrays.stream(r).allMatch(s -> s == null || s.isEmpty());
            if (a != null) for (int i = 0; i < a.length; i++) w.setAction(i, a[i]);
            p.set(row, cleared ? "pass" : "mismatch", "actions " + Arrays.toString(a) + " -> " + Arrays.toString(r) + " -> restored " + Arrays.toString(w.getActions()));
        });
        // Dynamic children on the probe's widget (it holds none of the game's): create, count, delete all, count.
        SPECIAL.put("Widget.deleteAllChildren0", (p, row, inst) -> {
            Widget w = (Widget) inst;
            w.createChild(-1, 3);
            int before = w.getDynamicChildren().length;
            w.deleteAllChildren();
            int after = w.getDynamicChildren().length;
            p.set(row, before == 1 && after == 0 ? "pass" : "mismatch", "dynamic children after createChild " + before + ", after deleteAllChildren " + after);
        });
        Special child = (p, row, inst) -> {
            Widget w = (Widget) inst;
            Widget c = row.m.getParameterCount() == 1 ? w.createChild(3) : w.createChild(-1, 3);
            int n = w.getDynamicChildren().length;
            w.deleteAllChildren();
            p.set(row, c != null && n == 1 ? "pass" : "mismatch", describe(c) + "; dynamic children " + n + " (deleted again)");
        };
        SPECIAL.put("Widget.createChild1", child);
        SPECIAL.put("Widget.createChild2", child);
        // The image must match the sprite's size (desktop throws otherwise); the live BufferedImage may be any size.
        SPECIAL.put("SpritePixels.toBufferedImage1", (p, row, inst) -> {
            net.runelite.api.SpritePixels s = (net.runelite.api.SpritePixels) inst;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(s.getWidth(), s.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            s.toBufferedImage(img);
            p.set(row, "pass", "drawn into a " + s.getWidth() + "x" + s.getHeight() + " image");
        });
        // Rasterizer draws: on a 20x20 corner of its pixels, cleared first; read the pixels back; restore them.
        SPECIAL.put("Rasterizer.fillRectangle5", (p, row, inst) -> raster(p, row, (Rasterizer) inst, (r, px, w) -> {
            r.fillRectangle(1, 1, 4, 4, 0x123456);
            return new int[] {px[w + 1], px[4 * w + 4], px[5 * w + 5]};
        }, v -> v[0] == 0x123456 && v[1] == 0x123456 && v[2] == 0));
        SPECIAL.put("Rasterizer.rasterFlat7", (p, row, inst) -> raster(p, row, (Rasterizer) inst, (r, px, w) -> {
            r.rasterFlat(0, 0, 16, 0, 16, 0, 0x654321);
            return new int[] {px[2 * w + 2], px[15 * w + 15]};
        }, v -> v[0] == 0x654321 && v[1] == 0));
        SPECIAL.put("Rasterizer.rasterGouraud9", (p, row, inst) -> raster(p, row, (Rasterizer) inst, (r, px, w) -> {
            r.rasterGouraud(0, 0, 16, 0, 16, 0, 0x7f, 0x7f, 0x7f);  // white
            return new int[] {px[2 * w + 2], px[15 * w + 15]};
        }, v -> v[0] != 0 && v[1] == 0));
        SPECIAL.put("Rasterizer.setDrawRegion4", (p, row, inst) -> raster(p, row, (Rasterizer) inst, (r, px, w) -> {
            r.setDrawRegion(4, 4, 8, 8);
            r.fillRectangle(0, 0, 20, 20, 0xffffff);
            r.resetRasterClipping();
            return new int[] {px[5 * w + 5], px[2 * w + 2], px[10 * w + 10]};
        }, v -> v[0] == 0xffffff && v[1] == 0 && v[2] == 0));
        SPECIAL.put("Rasterizer.resetRasterClipping0", (p, row, inst) -> raster(p, row, (Rasterizer) inst, (r, px, w) -> {
            r.setDrawRegion(4, 4, 8, 8);
            r.resetRasterClipping();
            r.fillRectangle(0, 0, 20, 20, 0xffffff);
            return new int[] {px[2 * w + 2], px[10 * w + 10]};
        }, v -> v[0] == 0xffffff && v[1] == 0xffffff));
        // Low res: one colour per 8-pixel run of a gouraud gradient; full res: the colour changes inside a run.
        SPECIAL.put("Rasterizer.setRasterGouraudLowRes1", (p, row, inst) -> raster(p, row, (Rasterizer) inst, (r, px, w) -> {
            r.setRasterGouraudLowRes(true);
            r.rasterGouraud(0, 0, 19, 0, 19, 0, 0x0000, 0x007f, 0x0000);
            int lowRunEqual = px[w + 8] == px[w + 15] ? 1 : 0;
            r.setRasterGouraudLowRes(false);
            r.rasterGouraud(0, 0, 19, 0, 19, 0, 0x0000, 0x007f, 0x0000);
            int fullRunDiffers = px[w + 8] != px[w + 15] ? 1 : 0;
            return new int[] {lowRunEqual, fullRunDiffers};
        }, v -> v[0] == 1 && v[1] == 1));
    }

    // Caches: the entry count (the host cache's size()) before and after reset, which runs the game's clear.
    static String resetCache(Object cache, Runnable reset) throws Exception {
        Method size = cache.getClass().getMethod("size");
        size.setAccessible(true);
        Object before = size.invoke(cache);
        reset.run();
        return "entries " + before + " -> " + size.invoke(cache);
    }

    static {
        SPECIAL.put("NodeCache.reset0", (p, row, inst) -> {
            net.runelite.api.NodeCache c = (net.runelite.api.NodeCache) inst;
            try {
                c.getClass().getMethod("size");
            } catch (NoSuchMethodException e) {
                c.reset();  // a cache with nothing host-side to count
                p.set(row, "pass", "returned");
                return;
            }
            String ev = describe(c) + ": " + resetCache(c, c::reset);
            p.set(row, ev.endsWith("-> 0") ? "pass" : "mismatch", ev);
        });
        SPECIAL.put("Client.resetHealthBarCaches0", (p, row, inst) -> {
            Client c = (Client) inst;
            Object hb = null;
            try {
                java.lang.reflect.Field f = Class.forName("net.runelite.host.CacheConfig", false, Client.class.getClassLoader()).getDeclaredField("CACHES");
                f.setAccessible(true);
                hb = Array.get(f.get(null), 5);
            } catch (ReflectiveOperationException e) {
                c.resetHealthBarCaches();
                p.set(row, "pass", "returned (no host cache to count)");
                return;
            }
            String ev = "health bar types + sprites " + resetCache(hb, c::resetHealthBarCaches);
            p.set(row, ev.endsWith("-> 0") ? "pass" : "mismatch", ev);
        });
        // Index reads on a group the live index has: the first group with files, its first file's bytes.
        SPECIAL.put("IndexDataBase.getFileIds1", (p, row, inst) -> {
            int[] g = firstFile((net.runelite.api.IndexDataBase) inst);
            int[] ids = ((net.runelite.api.IndexDataBase) inst).getFileIds(g == null ? 0 : g[0]);
            p.set(row, ids != null && ids.length > 0 ? "pass" : "mismatch", describe(inst) + " group " + (g == null ? 0 : g[0]) + ": " + describe(ids)
                + (ids != null && ids.length > 0 ? " first " + ids[0] + " last " + ids[ids.length - 1] : ""));
        });
        SPECIAL.put("IndexDataBase.loadData2", (p, row, inst) -> {
            net.runelite.api.IndexDataBase idx = (net.runelite.api.IndexDataBase) inst;
            int[] g = firstFile(idx);
            byte[] b = g == null ? null : idx.loadData(g[0], g[1]);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; b != null && i < Math.min(8, b.length); i++) hex.append(String.format("%02x", b[i] & 0xff));
            p.set(row, b != null && b.length > 0 ? "pass" : "mismatch", describe(inst) + (g == null ? " no group with files" : " group " + g[0] + " file " + g[1] + ": " + describe(b) + " " + hex));
        });
    }

    /** {group, file} of the first group of `idx` with a file, or null. */
    static int[] firstFile(net.runelite.api.IndexDataBase idx) {
        for (int g = 0; g < 4096; g++) {
            int[] ids = idx.getFileIds(g);
            if (ids != null && ids.length > 0) return new int[] {g, ids[0]};
        }
        return null;
    }

    interface Draw {
        int[] run(Rasterizer r, int[] px, int w);
    }

    private static void raster(ApiProbePlugin p, Row row, Rasterizer r, Draw draw, java.util.function.Predicate<int[]> ok) {
        int[] px = r.getPixels();
        int w = r.getWidth();
        if (w < 20 || r.getHeight() < 20) { p.set(row, "mismatch", "pixels " + w + "x" + r.getHeight() + " (smaller than 20x20)"); return; }
        int[] saved = px.clone();
        r.resetRasterClipping();
        r.fillRectangle(0, 0, 20, 20, 0);
        int[] v = draw.run(r, px, w);
        System.arraycopy(saved, 0, px, 0, px.length);
        String hex = Arrays.stream(v).mapToObj(Integer::toHexString).collect(java.util.stream.Collectors.joining(","));
        p.set(row, ok.test(v) ? "pass" : "mismatch", "drawn into " + w + "x" + r.getHeight() + " pixels; read back " + hex + " (restored)");
    }

    // ---- arguments ----

    static final class Missing extends Exception {
        Missing(String type) {
            super(type);
        }
    }

    /** Arguments that reach real data where 0 would not. */
    private Object[] args(Row row) throws Missing {
        String key = simple(row.iface) + "." + row.name + row.desc;
        switch (key) {
            case "Client.getItemContainer(I)Lnet/runelite/api/ItemContainer;": return new Object[] {93};
            case "Client.getItemDefinition(I)Lnet/runelite/api/ItemComposition;": return new Object[] {995};
            case "Client.createItemSprite(IIIIIZI)Lnet/runelite/api/SpritePixels;": return new Object[] {995, 1, 1, 3153952, 0, false, 512};
            case "Client.getWidget(I)Lnet/runelite/api/widgets/Widget;": return new Object[] {widgetId()};
            case "Client.getWidget(II)Lnet/runelite/api/widgets/Widget;": return new Object[] {widgetId() >>> 16, widgetId() & 0xffff};
            case "Client.getVarbitValue(I)I": return new Object[] {8119};
            case "Client.getVarpValue(I)I": return new Object[] {1000};
            // A model the cache has (the live NPC's) and that NPC's idle sequence, so the rows read real geometry.
            case "Client.loadModel(I)Lnet/runelite/api/Model;": return new Object[] {npcModel()};
            case "Client.loadModel(I[S[S)Lnet/runelite/api/Model;": return new Object[] {npcModel(), new short[0], new short[0]};
            case "Client.loadModelData(I)Lnet/runelite/api/ModelData;": return new Object[] {npcModel()};
            case "Client.applyTransformations(Lnet/runelite/api/Model;Lnet/runelite/api/Animation;ILnet/runelite/api/Animation;I)Lnet/runelite/api/Model;": {
                Object npc = live.get(net.runelite.api.NPC.class);
                int seq = npc != null ? ((net.runelite.api.NPC) npc).getIdlePoseAnimation() : -1;
                return new Object[] {arg(net.runelite.api.Model.class), client.loadAnimation(seq), 0, null, 0};
            }
            // The config archive (2), so the IndexDataBase rows read config files; a DB row the cache has.
            case "Client.getIndex(I)Lnet/runelite/api/IndexDataBase;": return new Object[] {2};
            case "Client.getDBRowConfig(I)Lnet/runelite/api/dbtable/DBRowConfig;": {
                for (int r = 0; r < 20000; r++) if (client.getDBRowConfig(r) != null) return new Object[] {r};
                return new Object[] {0};
            }
            // scale 0 would flatten the live model the Model rows read next; 128 = 1:1
            case "Mesh.scale(III)Lnet/runelite/api/Mesh;": return new Object[] {128, 128, 128};
            default: break;
        }
        Class<?>[] ps = row.m.getParameterTypes();
        Object[] a = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) a[i] = arg(ps[i]);
        return a;
    }

    private int npcModel() {
        Object npc = live.get(net.runelite.api.NPC.class);
        int[] m = npc != null && ((net.runelite.api.NPC) npc).getComposition() != null ? ((net.runelite.api.NPC) npc).getComposition().getModels() : null;
        return m != null && m.length > 0 ? m[0] : 0;
    }

    private int widgetId() {
        Object w = live.get(Widget.class);
        return w != null ? ((Widget) w).getId() : 0;
    }

    Object arg(Class<?> t) throws Missing {
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == boolean.class) return false;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return 'a';
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == String.class) return "api-probe";
        if (t.isArray()) return Array.newInstance(t.getComponentType(), 0);
        if (t.isEnum()) return t.getEnumConstants()[0];
        Object v = live.get(t);
        if (v != null) return v;
        if (t == Object.class) return 0;
        if (t == List.class || t == Collection.class || t == Iterable.class) return new ArrayList<>();
        if (t == Set.class) return new HashSet<>();
        if (t == Map.class) return new HashMap<>();
        if (t.isInterface() && (!t.getName().startsWith(API) || EXCLUDED.containsKey(t.getName())))
            return Proxy.newProxyInstance(t.getClassLoader(), new Class<?>[] {t}, (o, m, a) -> dflt(m.getReturnType()));
        throw new Missing(t.getName().startsWith(API) ? t.getName().substring(API.length()) : t.getName());
    }

    static Object dflt(Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == float.class) return 0f;
        if (r == double.class) return 0d;
        if (r == short.class) return (short) 0;
        if (r == byte.class) return (byte) 0;
        if (r == char.class) return (char) 0;
        return null;
    }

    /** A different value of the same type, so a read-back proves the write; objects round-trip unchanged. */
    static Object alter(Object old, Class<?> t) {
        if (t == int.class) return (Integer) old <= 0 ? (Integer) old + 1 : (Integer) old - 1;  // ids stay >= -1, 1-bit values flip
        if (t == boolean.class) return !(Boolean) old;
        if (t == long.class) return (Long) old + 1;
        if (t == short.class) return (short) ((Short) old + 1);
        if (t == byte.class) return (byte) ((Byte) old + 1);
        if (t == char.class) return (char) ((Character) old + 1);
        if (t == float.class) return (Float) old + 1;
        if (t == double.class) return (Double) old + 1;
        if (t == String.class) return old == null ? "api-probe" : old + "~";
        if (t.isEnum() && old != null) {
            Object[] all = t.getEnumConstants();
            return all[(((Enum<?>) old).ordinal() + 1) % all.length];
        }
        return old;
    }

    static boolean same(Object a, Object b) {
        return Arrays.deepEquals(new Object[] {a}, new Object[] {b});
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        return Array.get(Array.newInstance(c, 1), 0).getClass();
    }

    // ---- report ----

    void set(Row row, String result, String evidence) {
        row.result = result;
        row.evidence = evidence == null ? "" : evidence;
    }

    static String describe(Object v) {
        if (v == null) return "null";
        Class<?> c = v.getClass();
        String s;
        if (c.isArray()) s = c.getComponentType().getSimpleName() + "[" + Array.getLength(v) + "]";
        else if (v instanceof Collection) s = c.getSimpleName() + "(" + ((Collection<?>) v).size() + ")";
        else if (v instanceof Map) s = c.getSimpleName() + "(" + ((Map<?, ?>) v).size() + ")";
        else if (v instanceof Number || v instanceof Boolean || v instanceof Character || v instanceof Enum) s = String.valueOf(v);
        else if (v instanceof String) s = "\"" + v + "\"";
        else if (c.getName().startsWith("java.") || c.getName().startsWith(API)) s = String.valueOf(v);
        else if (ownToString(c)) s = String.valueOf(v);  // a host object that says what it holds
        else s = c.getSimpleName().isEmpty() ? c.getName() : c.getSimpleName();
        s = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }

    private static boolean ownToString(Class<?> c) {
        try {
            return c.getMethod("toString").getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private void finish() throws Exception {
        done = true;
        eventBus.unregister(this);
        for (Map.Entry<String, List<Row>> e : byIface.entrySet())
            for (Row r : e.getValue())
                if (r.result == null) set(r, "unreached", "no live " + simple(r.iface) + " instance");
        for (Row r : rows) if (r.result == null || r.result.equals("deferred")) set(r, "unreached", "no live " + simple(r.iface) + " instance");
        Map<String, Integer> counts = new TreeMap<>();
        for (Row r : rows) counts.merge(r.result, 1, Integer::sum);
        int pass = counts.getOrDefault("pass", 0);
        String pct = String.format(java.util.Locale.ROOT, "%.1f", 100.0 * pass / rows.size());
        File tmp = new File(dir, "api-probe.tsv.part");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write("# api-probe: every abstract method of every net.runelite.api interface, called on a live instance in the app\n");
            w.write("# api jar: " + apiJar + "\n");
            w.write("# build: " + build + "\n");
            for (Map.Entry<String, String> e : EXCLUDED.entrySet()) w.write("# excluded: " + e.getKey() + ": " + e.getValue() + "\n");
            w.write("# denominator: " + rows.size() + "\n");
            w.write("# counts: " + counts + "\n");
            w.write("# result: pass " + pass + " / " + rows.size() + " = " + pct + "%\n");
            w.write("interface\tmethod\tresult\tnative\tevidence\n");
            for (Row r : rows) w.write(r.iface + "\t" + r.name + r.desc + "\t" + r.result + "\t" + (r.nat ? "native" : "") + "\t" + clean(r.evidence) + "\n");
        }
        File out = new File(dir, "api-probe.tsv");
        if (!tmp.renameTo(out)) throw new java.io.IOException("rename to " + out + " failed");
        log.info("api-probe: done, pass {} / {} = {}%, {}", pass, rows.size(), pct, counts);
    }

    private static String clean(String s) {
        s = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        return s.length() > 300 ? s.substring(0, 297) + "..." : s;
    }

    private static String simple(String iface) {
        return iface.substring(iface.lastIndexOf('.') + 1);
    }

    static String desc(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(desc(p));
        return sb.append(')').append(desc(m.getReturnType())).toString();
    }

    static String desc(Class<?> c) {
        if (c.isArray()) return "[" + desc(c.getComponentType());
        if (!c.isPrimitive()) return "L" + c.getName().replace('.', '/') + ";";
        return String.valueOf("VZBCSIJFD".charAt(Arrays.asList(void.class, boolean.class, byte.class, char.class, short.class,
            int.class, long.class, float.class, double.class).indexOf(c)));
    }
}
