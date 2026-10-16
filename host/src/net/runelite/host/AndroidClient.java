package net.runelite.host;

import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;

/** net.runelite.api.Client backed by the native game. Methods not overridden here throw. */
public class AndroidClient extends ClientStub {
    private final AndroidWorldView worldView;

    AndroidClient(AndroidWorldView worldView) {
        this.worldView = worldView;
    }

    @Override
    public WorldView getWorldView(int id) {
        // Only the top-level world view (WorldView.TOPLEVEL = 0; -1 accepted too) exists until
        // world entities (boats) are mapped.
        return id == WorldView.TOPLEVEL || id == -1 ? getTopLevelWorldView() : null;
    }

    /**
     * Never null, as in RuneLite: plugins (QH's minimap overlay) call getPlane()/isInstance() on it
     * before login. Its accessors fall back to zeros/empty sets while no scene is loaded.
     */
    @Override
    public WorldView getTopLevelWorldView() {
        return worldView;
    }

    @Override
    public GameState getGameState() {
        GameState s = GameState.of(Native.gameState());
        return s != null ? s : GameState.UNKNOWN;
    }

    @Override
    public int getGameCycle() {
        return Native.gameCycle();
    }

    @Override
    public int getTickCount() {
        return Native.serverTick();
    }

    @Override
    public int[] getVarps() { return lastVarps = Native.varps(); }
    /** The array getVarps() last returned: RuneLite hands out the live array, which plugins edit
     *  (setVarbitValue) and then publish with queueChangedVarp. */
    private int[] lastVarps;

    @Override public void setVarbit(int varbit, int value) { Native.setVarbit(varbit, value); }
    @Override public void setVarbitValue(int[] varps, int varbit, int value) {
        int[] d = Native.varbitDef(varbit);
        if (d == null || varps == null || d[0] >= varps.length) return;
        int bits = d[2] - d[1] + 1;
        int mask = bits >= 32 ? -1 : (1 << bits) - 1;
        varps[d[0]] = (varps[d[0]] & ~(mask << d[1])) | ((value & mask) << d[1]);
    }
    @Override public void queueChangedVarp(int varp) {
        if (lastVarps != null && varp >= 0 && varp < lastVarps.length) Native.setVarp(varp, lastVarps[varp]);
    }
    @Override public net.runelite.api.VarbitComposition getVarbit(int id) {
        int[] d = Native.varbitDef(id);
        if (d == null) return null;
        return new net.runelite.api.VarbitComposition() {
            @Override public int getIndex() { return d[0]; }
            @Override public int getLeastSignificantBit() { return d[1]; }
            @Override public int getMostSignificantBit() { return d[2]; }
        };
    }
    @Override public void playSoundEffect(int id) { Native.playSound(id, 1, 0); }
    @Override public void playSoundEffect(int id, int volume) { Native.playSound(id, 1, 0); }
    @Override public void playSoundEffect(int id, int x, int y, int range) { Native.playSound(id, 1, 0); }
    @Override public void playSoundEffect(int id, int x, int y, int range, int delay) { Native.playSound(id, 1, delay); }
    @Override public void setVarcIntValue(int id, int value) { Native.setVarcInt(id, value); }
    @Override public void setVarcStrValue(int id, String value) { Native.setVarcString(id, value); }

    @Override public int getWeight() { return Native.weight(); }
    @Override public int getEnergy() { return Native.runEnergy(); }

    @Override public int getVarcIntValue(int id) { return Native.varcInt(id); }
    @Override public String getVarcStrValue(int id) { String s = Native.varcString(id); return s != null ? s : ""; }

    /**
     * Through the game's own addChatMessage, so the line shows in the chatbox (and comes back as
     * a ChatMessage event). Off the client thread it is queued to it. Latin-1 text; the game's
     * tags (<col=...>) work as on desktop.
     */
    @Override
    public net.runelite.api.MessageNode addChatMessage(net.runelite.api.ChatMessageType type, String name, String message, String sender) {
        return addChatMessage(type, name, message, sender, true);
    }

    @Override
    public net.runelite.api.MessageNode addChatMessage(net.runelite.api.ChatMessageType type, String name, String message, String sender, boolean postEvent) {
        final int t = type != null ? type.getType() : 0;
        final String n = name != null ? name : "", m = message != null ? message : "";
        AndroidMessageNode node = new AndroidMessageNode(net.runelite.api.ChatMessageType.of(t), n, m, sender, postEvent);
        synchronized (PENDING) {
            PENDING.add(node);
        }
        if (isClientThread()) Native.addChatMessage(t, n, m);
        else Host.invoke(() -> Native.addChatMessage(t, n, m));
        return node;
    }

    /** Nodes returned by addChatMessage whose game message hasn't been seen yet (oldest first). */
    private static final java.util.ArrayDeque<AndroidMessageNode> PENDING = new java.util.ArrayDeque<>();

    /** The pending node for a new game message of this type and text, or null; drops stale ones. */
    static AndroidMessageNode takePending(net.runelite.api.ChatMessageType type, String text) {
        long now = System.currentTimeMillis();
        synchronized (PENDING) {
            while (!PENDING.isEmpty() && now - PENDING.peekFirst().pendingSince > 10000) PENDING.pollFirst();
            for (java.util.Iterator<AndroidMessageNode> it = PENDING.iterator(); it.hasNext(); ) {
                AndroidMessageNode p = it.next();
                if (p.getType() == type && java.util.Objects.equals(p.sentText, text)) {
                    it.remove();
                    return p;
                }
            }
        }
        return null;
    }

    @Override
    public int getVarpValue(int id) {
        return Native.varp(id);
    }

    @Override
    public int getServerVarpValue(int id) {
        return Native.serverVarp(id);
    }

    @Override
    public int getVarbitValue(int id) {
        return Native.varbit(id);
    }

    // --- client scripts: run in the game's VM; the stacks are a copy taken when the script returns ---

    private final int[] intStack = new int[1000];
    private final Object[] objectStack = new Object[1000];
    private int intStackSize, objectStackSize;

    // While a ScriptPreFired/PostFired is dispatched, the getters read the VM's live stacks
    // (loaded on first use) and changes to the int stack are written back afterwards.
    private boolean liveStacks, stacksLoaded;
    private int[] loadedInts;

    /**
     * Host-made stacks for a callback the game's scripts don't have (RuneLite injects
     * runelite_callback ops into its own copies of the chat scripts): the getters return these
     * until the next script event.
     */
    void fakeStacks(int[] ints, Object[] objs) {
        liveStacks = false;
        System.arraycopy(ints, 0, intStack, 0, ints.length);
        System.arraycopy(objs, 0, objectStack, 0, objs.length);
        intStackSize = ints.length;
        objectStackSize = objs.length;
    }

    void beginScriptEvent() {
        liveStacks = true;
        stacksLoaded = false;
    }

    void endScriptEvent() {
        if (stacksLoaded && loadedInts != null
            && (intStackSize != loadedInts.length || !java.util.Arrays.equals(java.util.Arrays.copyOf(intStack, intStackSize), loadedInts))) {
            Native.setScriptIntStack(intStack, Math.max(0, Math.min(intStackSize, intStack.length)));
        }
        liveStacks = false;
        stacksLoaded = false;
        loadedInts = null;
    }

    private void loadStacks() {
        if (!liveStacks || stacksLoaded) return;
        stacksLoaded = true;
        Object[] r = Native.scriptStacks();
        if (r == null) return;
        int[] ints = (int[]) r[0];
        Object[] objs = (Object[]) r[1];
        System.arraycopy(ints, 0, intStack, 0, ints.length);
        System.arraycopy(objs, 0, objectStack, 0, objs.length);
        intStackSize = ints.length;
        objectStackSize = objs.length;
        loadedInts = ints;
    }

    @Override
    public void runScript(Object... args) {
        if (liveStacks) {
            // The VM is mid-script; run it after the current one instead of corrupting its state.
            android.util.Log.w("osrsrl-host", "runScript(" + (args.length > 0 ? args[0] : "") + ") from a script event; deferred");
            Host.invoke(() -> runScript(args));
            return;
        }
        Object[] r = Native.runScript(args);
        int[] ints = (int[]) r[0];
        Object[] objs = (Object[]) r[1];
        System.arraycopy(ints, 0, intStack, 0, ints.length);
        System.arraycopy(objs, 0, objectStack, 0, objs.length);
        intStackSize = ints.length;
        objectStackSize = objs.length;
    }

    /**
     * Client.getMessages: the messages seen since the host started (the same nodes ChatMessage
     * carried), newest 1000 kept, keyed by message id.
     */
    private static final java.util.LinkedHashMap<Long, net.runelite.api.MessageNode> MESSAGES =
        new java.util.LinkedHashMap<Long, net.runelite.api.MessageNode>() {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, net.runelite.api.MessageNode> e) { return size() > 1000; }
        };

    static void rememberMessage(net.runelite.api.MessageNode node) {
        synchronized (MESSAGES) { MESSAGES.put((long) node.getId(), node); }
    }

    @Override public net.runelite.api.IterableHashTable<net.runelite.api.MessageNode> getMessages() {
        return new net.runelite.api.IterableHashTable<net.runelite.api.MessageNode>() {
            @Override public net.runelite.api.MessageNode get(long id) { synchronized (MESSAGES) { return MESSAGES.get(id); } }
            @Override public void put(net.runelite.api.MessageNode n, long id) { synchronized (MESSAGES) { MESSAGES.put(id, n); } }
            @Override public java.util.Iterator<net.runelite.api.MessageNode> iterator() {
                synchronized (MESSAGES) { return new java.util.ArrayList<>(MESSAGES.values()).iterator(); }
            }
        };
    }

    /**
     * Client.getChatLineMap: per chat type, the messages seen since the host started, newest
     * first (as the game's buffers). removeMessageNode also removes the line from the game's chat
     * channel (Native.chatRemove) and makes the chatbox rebuild.
     */
    @Override public java.util.Map<Integer, net.runelite.api.ChatLineBuffer> getChatLineMap() {
        java.util.Map<Integer, java.util.List<net.runelite.api.MessageNode>> byType = new java.util.HashMap<>();
        synchronized (MESSAGES) {
            for (net.runelite.api.MessageNode n : MESSAGES.values()) {
                byType.computeIfAbsent(n.getType().getType(), k -> new java.util.ArrayList<>()).add(n);
            }
        }
        java.util.Map<Integer, net.runelite.api.ChatLineBuffer> out = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, java.util.List<net.runelite.api.MessageNode>> e : byType.entrySet()) {
            java.util.List<net.runelite.api.MessageNode> list = e.getValue();
            java.util.Collections.reverse(list);
            final net.runelite.api.MessageNode[] lines = list.toArray(new net.runelite.api.MessageNode[0]);
            out.put(e.getKey(), new net.runelite.api.ChatLineBuffer() {
                @Override public net.runelite.api.MessageNode[] getLines() { return lines; }
                @Override public int getLength() { return lines.length; }
                @Override public void removeMessageNode(net.runelite.api.MessageNode node) {
                    synchronized (MESSAGES) { MESSAGES.values().remove(node); }
                    final int type = node.getType().getType(), id = node.getId();
                    if (id < 0) return;
                    if (isClientThread()) removeFromGame(type, id);
                    else Host.invoke(() -> removeFromGame(type, id));
                }
            });
        }
        return out;
    }

    @Override public net.runelite.api.ScriptEventBuilder createScriptEventBuilder(Object... args) {
        return new AndroidScriptEvent(this, args);
    }

    @Override public int[] getIntStack() { loadStacks(); return intStack; }
    @Override public int getIntStackSize() { loadStacks(); return intStackSize; }
    @Override public void setIntStackSize(int size) { loadStacks(); intStackSize = size; }
    @Override public Object[] getObjectStack() { loadStacks(); return objectStack; }
    @Override public int getObjectStackSize() { loadStacks(); return objectStackSize; }
    @Override public void setObjectStackSize(int size) { loadStacks(); objectStackSize = size; }

    @Override
    public net.runelite.api.ItemComposition getItemDefinition(int id) {
        return AndroidItemComposition.lookup(id);
    }

    @Override
    public net.runelite.api.NPCComposition getNpcDefinition(int id) {
        return AndroidNPCComposition.lookup(id);
    }

    /** Map regions spanned by the loaded scene (13 x 13 chunks from the scene base). */
    @Override public int[] getMapRegions() {
        WorldView wv = getTopLevelWorldView();
        if (wv == null || getGameState().getState() < net.runelite.api.GameState.LOGGED_IN.getState()) return new int[0];
        int x0 = wv.getBaseX() >> 6, x1 = (wv.getBaseX() + 96) >> 6, y0 = wv.getBaseY() >> 6, y1 = (wv.getBaseY() + 96) >> 6;
        int[] out = new int[(x1 - x0 + 1) * (y1 - y0 + 1)];
        int i = 0;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++) out[i++] = x << 8 | y;
        return out;
    }

    /** Prayers are varbits (the prayer orbs' state), as RuneLite's own client reads them. */
    @Override public boolean isPrayerActive(net.runelite.api.Prayer p) { return getVarbitValue(p.getVarbit()) == 1; }

    /** Walk destination (Client +0xecdc/+0xece0), null when not walking. */
    @Override public net.runelite.api.coords.LocalPoint getLocalDestinationLocation() {
        int[] d = Native.walkDestination();
        if (d == null || d[0] < 0 || d[1] < 0 || d[0] >= 104 || d[1] >= 104) return null;
        return net.runelite.api.coords.LocalPoint.fromScene(d[0], d[1], getTopLevelWorldView());
    }

    /** Idle time since the last touch or key event (client cycles of 20 ms), as RuneLite counts them. */
    private static int idleCycles() { return (int) Math.min(Integer.MAX_VALUE, (android.os.SystemClock.uptimeMillis() - Sidebar.lastInputMillis) / 20); }
    @Override public int getMouseIdleTicks() { return idleCycles(); }
    /** Wall-clock time of the last touch/key event (desktop compares it with currentTimeMillis). */
    @Override public long getMouseLastPressedMillis() {
        return System.currentTimeMillis() - (android.os.SystemClock.uptimeMillis() - Sidebar.lastInputMillis);
    }
    // The game's own AFK logout isn't driven from here; plugins only read/raise this value.
    private int idleTimeout = 15000;
    private boolean idleTimeoutSet;
    /**
     * The game logs out after its AFKTimeout setting (whole minutes) without input; RuneLite counts
     * in 20 ms ticks. Set values are rounded up to minutes and re-applied at each login.
     */
    @Override public int getIdleTimeout() {
        int m = Native.afkTimeout();
        return m > 0 ? m * 3000 : idleTimeout;
    }
    @Override public void setIdleTimeout(int ticks) {
        idleTimeout = ticks;
        idleTimeoutSet = true;
        if (isClientThread()) applyIdleTimeout();
        else Host.invoke(this::applyIdleTimeout);
    }
    void applyIdleTimeout() {
        if (!idleTimeoutSet) return;
        int minutes = (int) Math.max(1, Math.min(35000, ((long) idleTimeout * 20 + 59999) / 60000));
        int before = Native.afkTimeout();
        boolean ok = Native.setAfkTimeout(minutes);
        android.util.Log.i(Host.TAG, "idle timeout: " + before + " -> " + minutes + " min (" + (ok ? "set" : "not set") + ")");
    }
    /** Varbit over the server's copy of its varp (RuneLite's getServerVarbitValue). */
    @Override public int getServerVarbitValue(int id) {
        net.runelite.api.VarbitComposition v = getVarbit(id);
        if (v == null) return 0;
        int lsb = v.getLeastSignificantBit(), msb = v.getMostSignificantBit();
        int bits = msb - lsb + 1;
        int mask = bits >= 32 ? -1 : (1 << bits) - 1;
        return (getServerVarpValue(v.getIndex()) >>> lsb) & mask;
    }
    @Override public int getKeyboardIdleTicks() { return idleCycles(); }
    @Override public int getMouseCurrentButton() { return 0; }

    /** The game canvas is the overlay canvas (UI-canvas units). */
    @Override public java.awt.Dimension getRealDimensions() { return new java.awt.Dimension(getCanvasWidth(), getCanvasHeight()); }
    @Override public java.awt.Dimension getStretchedDimensions() { return getRealDimensions(); }

    // No drag/selection/cursor state on a touch screen (RuneLite returns null when nothing is dragged).
    @Override public net.runelite.api.widgets.Widget getDraggedWidget() { return null; }
    @Override public net.runelite.api.widgets.Widget getDraggedOnWidget() { return null; }
    @Override public net.runelite.api.widgets.Widget getSelectedWidget() { return null; }
    @Override public net.runelite.api.widgets.Widget getScriptActiveWidget() { return null; }
    /** The game redraws its chat itself; nothing to rebuild. */
    private static void removeFromGame(int type, int id) {
        boolean removed = Native.chatRemove(type, id);
        if (removed) Native.chatChanged();
        android.util.Log.i(Host.TAG, "chat remove type " + type + " id " + id + (removed ? ": removed" : ": not found"));
    }

    /** The game's chat cycle bump: the chatbox rebuilds (shows rewritten or removed lines). */
    @Override public void refreshChat() {
        if (isClientThread()) Native.chatChanged();
        else Host.invoke(Native::chatChanged);
    }

    /**
     * No physical keyboard on a touch screen: Shift is the virtual toggle under the RL button
     * (Ground/Object Markers add their entries to the long-press menu while it is on).
     */
    @Override public boolean isKeyPressed(int keycode) {
        return keycode == net.runelite.api.KeyCode.KC_SHIFT && Native.shiftHeld();
    }
    @Override public net.runelite.api.StructComposition getStructComposition(int id) { return new AndroidStruct(id); }

    /** No "Use" / spell target selection is tracked yet. */
    @Override public boolean isWidgetSelected() { return false; }

    /**
     * The touch point stands in for the mouse: while a finger is down and for a few seconds after
     * it lifts (Sidebar.hoverPoint), so hover tooltips (Run Energy, Item Stats) show on a tap or a
     * long-press. Otherwise (-1, -1), what RuneLite reports with the mouse off the canvas.
     */
    @Override
    public net.runelite.api.Point getMouseCanvasPosition() {
        float[] p = Sidebar.hoverPoint();
        int[] screen = Native.screenSize();
        java.awt.Dimension c = OverlayRenderer.canvasSize();
        if (p == null || screen == null || screen[0] <= 0 || screen[1] <= 0 || c.width <= 0) return new net.runelite.api.Point(-1, -1);
        return new net.runelite.api.Point(Math.round(p[0] * c.width / screen[0]), Math.round(p[1] * c.height / screen[1]));
    }

    @Override
    public net.runelite.api.ObjectComposition getObjectDefinition(int id) {
        return AndroidObjectComposition.lookup(id);
    }

    // --- models: not read from the game; inert placeholders so fake-object code runs (nothing is drawn) ---

    static final class InertModelData extends ModelDataBag {
        @Override public net.runelite.api.Model light() { return new ModelBag(); }
        @Override public net.runelite.api.Model light(int a, int b, int c, int d, int e) { return new ModelBag(); }
    }

    @Override public net.runelite.api.ModelData loadModelData(int id) { return new InertModelData(); }
    @Override public net.runelite.api.ModelData mergeModels(net.runelite.api.ModelData... m) { return new InertModelData(); }
    @Override public net.runelite.api.ModelData mergeModels(net.runelite.api.ModelData[] m, int n) { return new InertModelData(); }
    @Override public net.runelite.api.Model loadModel(int id) { return new ModelBag(); }
    @Override public net.runelite.api.Model loadModel(int id, short[] a, short[] b) { return new ModelBag(); }
    @Override public net.runelite.api.RuneLiteObject createRuneLiteObject() { return new net.runelite.api.RuneLiteObject(this); }
    @Override public void registerRuneLiteObject(net.runelite.api.RuneLiteObjectController o) {}
    @Override public void removeRuneLiteObject(net.runelite.api.RuneLiteObjectController o) {}
    @Override public boolean isRuneLiteObjectRegistered(net.runelite.api.RuneLiteObjectController o) { return false; }

    @Override public net.runelite.api.Animation loadAnimation(int id) { return null; }

    // Hint arrows: not read from the game (HINT_ARROW packet); plugin-set arrows are remembered only.
    private Object hintArrow;
    @Override public boolean hasHintArrow() { return hintArrow != null; }
    @Override public int getHintArrowType() { return hintArrow instanceof net.runelite.api.NPC ? 1 : hintArrow instanceof Player ? 10 : hintArrow != null ? 2 : 0; }
    @Override public void clearHintArrow() { hintArrow = null; }
    @Override public void setHintArrow(net.runelite.api.coords.WorldPoint p) { hintArrow = p; }
    @Override public void setHintArrow(net.runelite.api.coords.LocalPoint p) { hintArrow = p; }
    @Override public void setHintArrow(Player p) { hintArrow = p; }
    @Override public void setHintArrow(net.runelite.api.NPC n) { hintArrow = n; }
    @Override public net.runelite.api.coords.WorldPoint getHintArrowPoint() { return hintArrow instanceof net.runelite.api.coords.WorldPoint ? (net.runelite.api.coords.WorldPoint) hintArrow : null; }
    @Override public Player getHintArrowPlayer() { return hintArrow instanceof Player ? (Player) hintArrow : null; }
    @Override public net.runelite.api.NPC getHintArrowNpc() { return hintArrow instanceof net.runelite.api.NPC ? (net.runelite.api.NPC) hintArrow : null; }

    @Override
    public net.runelite.api.EnumComposition getEnum(int id) {
        return AndroidEnum.lookup(id);
    }
    /** The world map isn't mapped: an inert map whose data contains no positions. */
    private final WorldMapBag worldMap = new WorldMapBag() {
        private final WorldMapDataBag data = new WorldMapDataBag();
        @Override public net.runelite.api.worldmap.WorldMapData getWorldMapData() { return data; }
    };
    @Override public net.runelite.api.worldmap.WorldMap getWorldMap() { return worldMap; }
    /** Minimap zoom isn't read from the game; RuneLite's default. */
    @Override public double getMinimapZoom() { return 4.0; }

    /** The long-press menu (game::read_menu); plugin-created entries stay host-side for now. */
    static final AndroidMenu MENU = new AndroidMenu();
    @Override public boolean isMenuOpen() { return !AndroidMenu.presorting && Native.menuOpen(); }
    @Override public int getMenuX() { return MENU.getMenuX(); }
    @Override public int getMenuY() { return MENU.getMenuY(); }
    @Override public int getMenuWidth() { return MENU.getMenuWidth(); }
    @Override public int getMenuHeight() { return MENU.getMenuHeight(); }
    @Override public boolean isMenuScrollable() { return false; }
    @Override public int getMenuScroll() { return 0; }
    @Override public net.runelite.api.Menu getMenu() { return MENU; }
    @Override public net.runelite.api.MenuEntry[] getMenuEntries() { return MENU.getMenuEntries(); }
    @Override public void setMenuEntries(net.runelite.api.MenuEntry[] e) { MENU.setMenuEntries(e); }
    @Override public net.runelite.api.MenuEntry createMenuEntry(int idx) { return MENU.createMenuEntry(idx); }

    /** Only the top-level world view exists (no boats / world entities yet). */
    @Override
    public WorldView findWorldViewFromWorldPoint(net.runelite.api.coords.WorldPoint p) {
        return getTopLevelWorldView();
    }

    /** CS1 text macros aren't expanded; widget text set by the server/scripts is returned as is. */
    @Override
    public String macroExpand(String text) {
        return text;
    }

    @Override
    public Player getLocalPlayer() {
        // RuneLite keeps the local player through LOADING (teleports, region changes); QH's
        // NpcStep.scanForNpcs calls getLocalPlayer().getWorldView() on GameStateChanged(LOADING).
        GameState s = getGameState();
        return s == GameState.LOGGED_IN || s == GameState.LOADING ? Host.localPlayer() : null;
    }

    // --- camera and viewport (the viewport is the whole logical overlay canvas) ---

    /**
     * Projection scale fitted to on-screen NPC positions at the default zoom: 285 for a 720-high
     * canvas (docs/offsets.md), scaled to the canvas height. The zoom field isn't mapped yet.
     */
    static volatile int scaleOverride = -1;

    @Override public int getCameraX() { return Math.round(getCameraFpX()); }
    @Override public int getCameraY() { return Math.round(getCameraFpY()); }
    @Override public int getCameraZ() { return Math.round(getCameraFpZ()); }

    /**
     * Experimental (`setprop debug.osrsrl.eyeback D`): moves the reported camera back by D units
     * along the view axis. Perspective's depth then gains D while the lateral terms stay, which tests
     * whether the game's eye sits behind its camera position (the fitted scale only matches at one depth).
     */
    private float eyeBack;
    private long eyeCheckedAt;
    private float eyeBack() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - eyeCheckedAt > 1000) {
            eyeCheckedAt = now;
            try { String v = Native.property("debug.osrsrl.eyeback"); eyeBack = v.isEmpty() ? 0 : Float.parseFloat(v); }
            catch (NumberFormatException ignored) {}
        }
        return eyeBack;
    }
    private float eyeOffset(int axis) {
        float d = eyeBack();
        if (d == 0) return 0;
        int[] cam = cam();
        double pitch = cam[3] * Math.PI * 2 / 16384, yaw = cam[4] * Math.PI * 2 / 16384;
        double[] f = {-Math.sin(yaw) * Math.cos(pitch), Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch)};
        return (float) (-d * f[axis]);
    }
    @Override public int getCameraPitch() { return cam()[3]; }
    @Override public int getCameraYaw() { return cam()[4]; }
    // Targets (+0x8949c/+0x894a0) aren't read separately; the camera settles on them within frames.
    @Override public int getCameraYawTarget() { return getCameraYaw(); }
    @Override public int getCameraPitchTarget() { return getCameraPitch(); }
    @Override public net.runelite.api.CameraFocusableEntity getCameraFocusEntity() { return getLocalPlayer(); }
    @Override public float getCameraFpX() { return cam()[0] + eyeOffset(0); }
    @Override public float getCameraFpY() { return cam()[1] + eyeOffset(1); }
    @Override public float getCameraFpZ() { return cam()[2] + eyeOffset(2); }
    @Override public float getCameraFpPitch() { return (float) (getCameraPitch() * Math.PI * 2 / 16384); }
    @Override public float getCameraFpYaw() { return (float) (getCameraYaw() * Math.PI * 2 / 16384); }
    private long scaleCheckedAt;

    @Override
    public int getScale() {
        long now = System.currentTimeMillis();
        if (now - scaleCheckedAt > 1000) {  // `setprop debug.osrsrl.scale N` overrides, for calibration
            scaleCheckedAt = now;
            try {
                String v = Native.property("debug.osrsrl.scale");
                scaleOverride = v.isEmpty() ? -1 : Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
            }
        }
        if (scaleOverride > 0) return scaleOverride;
        int[] z = gameViewport();
        // As the desktop client's viewportZoom: zoom * viewport height / 334.
        return z != null ? z[0] * z[4] / 334 : Math.round(285f * getViewportHeight() / 720f);
    }

    /**
     * The game's 3D viewport in UI canvas pixels: {zoom, x, y, width, height} from Client +0x46d4 and
     * +0x46e0..+0x46ec (0x5561e8, RuneLite's viewportZoom/viewport bounds; follows the zoom settings
     * and pinch zoom). Null while unset; `setprop debug.osrsrl.scale N` still wins for getScale.
     */
    private int[] gameViewport() {
        int stamp = Host.cycleStamp;
        if (viewportStamp != stamp || viewportCache == null) {
            int[] z = Native.viewportZoom();
            viewportCache = new Object[] {z != null && z[0] > 0 && z[3] > 0 && z[4] > 0 ? z : null};
            viewportStamp = stamp;
        }
        return (int[]) viewportCache[0];
    }
    // Camera and viewport are read once per client cycle: Perspective calls the getters per vertex
    // (NPC model hulls), and a JNI read each time took the game to ~0 fps with many NPCs highlighted.
    private int viewportStamp = -1, cameraStamp = -1;
    private Object[] viewportCache;
    private int[] cameraCache;
    private int[] cam() {
        int stamp = Host.cycleStamp;
        if (cameraStamp != stamp || cameraCache == null) {
            cameraCache = Native.camera();
            cameraStamp = stamp;
        }
        return cameraCache;
    }
    @Override public boolean isGpu() { return false; }
    @Override public boolean isResized() { return true; }
    @Override public int getViewportXOffset() { int[] z = gameViewport(); return z != null ? z[1] : 0; }
    @Override public int getViewportYOffset() { int[] z = gameViewport(); return z != null ? z[2] : 0; }
    @Override public int getViewportWidth() { int[] z = gameViewport(); return z != null ? z[3] : OverlayRenderer.canvasSize().width; }
    @Override public int getViewportHeight() { int[] z = gameViewport(); return z != null ? z[4] : OverlayRenderer.canvasSize().height; }
    @Override public int getCanvasWidth() { return OverlayRenderer.canvasSize().width; }
    @Override public int getCanvasHeight() { return OverlayRenderer.canvasSize().height; }

    // --- skills (Skill.ordinal() is the game's stat index) ---

    private static final int SKILLS = 25;

    private static int[] slice(int part) {
        return java.util.Arrays.copyOfRange(Native.skills(), part * SKILLS, (part + 1) * SKILLS);
    }

    @Override public int getBoostedSkillLevel(net.runelite.api.Skill s) { return Native.skills()[s.ordinal()]; }
    @Override public int getRealSkillLevel(net.runelite.api.Skill s) { return Native.skills()[SKILLS + s.ordinal()]; }
    @Override public int getSkillExperience(net.runelite.api.Skill s) { return Native.skills()[2 * SKILLS + s.ordinal()]; }
    @Override public int[] getBoostedSkillLevels() { return slice(0); }
    @Override public int[] getRealSkillLevels() { return slice(1); }
    @Override public int[] getSkillExperiences() { return slice(2); }

    @Override
    public int getTotalLevel() {
        int total = 0;
        for (int l : slice(1)) total += l;
        return total;
    }

    @Override
    public long getOverallExperience() {
        long total = 0;
        for (int x : slice(2)) total += x;
        return total;
    }

    // --- regions (no instances yet) ---

    @Override public boolean isInInstancedRegion() { return false; }

    @Override
    public java.util.EnumSet<net.runelite.api.WorldType> getWorldType() {
        return net.runelite.api.WorldType.fromMask(Native.worldFlags());
    }

    // Client +0x3174 / +0x2f64 (the CS2 map_world / worldflags handlers).
    @Override public int getWorld() { return Native.world(); }

    /** db_getfield through the game's script VM; null off the client thread or for a missing row. */
    @Override
    public Object[] getDBTableField(int rowID, int column, int tupleIndex) {
        if (!isClientThread()) return null;
        Object[] r = Native.dbField(rowID, column, tupleIndex);
        return r != null ? r : new Object[0];
    }

    // Friends chat / clans: not in a channel (RuneLite returns null then).
    @Override public net.runelite.api.FriendsChatManager getFriendsChatManager() { return null; }
    private final AndroidNameables.Friends friends = new AndroidNameables.Friends();
    private final AndroidNameables.Ignores ignores = new AndroidNameables.Ignores();
    @Override public net.runelite.api.FriendContainer getFriendContainer() { return friends; }
    /** Open sub-interfaces keyed by the component they're mounted in (RuneLite's componentTable). */
    @Override public net.runelite.api.HashTable<net.runelite.api.WidgetNode> getComponentTable() {
        int[] t = Native.subInterfaces();
        final java.util.Map<Long, net.runelite.api.WidgetNode> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 2 < t.length; i += 3) {
            final int comp = t[i], group = t[i + 1], mode = t[i + 2];
            m.put((long) comp, new net.runelite.api.WidgetNode() {
                @Override public int getId() { return group; }
                @Override public int getModalMode() { return mode; }
                @Override public net.runelite.api.Node getNext() { return null; }
                @Override public net.runelite.api.Node getPrevious() { return null; }
                @Override public long getHash() { return comp; }
            });
        }
        return new net.runelite.api.HashTable<net.runelite.api.WidgetNode>() {
            @Override public net.runelite.api.WidgetNode get(long key) { return m.get(key); }
            @Override public java.util.Iterator<net.runelite.api.WidgetNode> iterator() { return m.values().iterator(); }
        };
    }
    /** Only LOADING is honoured (plugins use it to rebuild the scene), via the game's own reload. */
    @Override public void setGameState(GameState state) {
        if (state != GameState.LOADING) return;
        if (isClientThread()) Native.reloadScene();
        else Host.invoke(Native::reloadScene);
    }
    @Override public net.runelite.api.NameableContainer<net.runelite.api.Ignore> getIgnoreContainer() { return ignores; }
    @Override public boolean isFriended(String name, boolean mustBeLoggedIn) {
        net.runelite.api.Friend f = friends.findByName(name);
        return f != null && (!mustBeLoggedIn || f.getWorld() > 0);
    }
    // Player menu options (MenuManager.addPlayerMenuItem: Hiscore's "Lookup"). Kept host-side;
    // the game's player menus are built from its own option list.
    private final String[] playerOptions = new String[8];
    private final boolean[] playerOptionsPriorities = new boolean[8];
    private final int[] playerMenuTypes = new int[8];
    @Override public String[] getPlayerOptions() { return playerOptions; }
    @Override public boolean[] getPlayerOptionsPriorities() { return playerOptionsPriorities; }
    @Override public int[] getPlayerMenuTypes() { return playerMenuTypes; }
    @Override public net.runelite.api.clan.ClanChannel getClanChannel() { return null; }
    @Override public net.runelite.api.clan.ClanChannel getGuestClanChannel() { return null; }
    @Override public net.runelite.api.clan.ClanSettings getClanSettings() { return null; }
    @Override public net.runelite.api.clan.ClanSettings getGuestClanSettings() { return null; }
    @Override public net.runelite.api.clan.ClanChannel getClanChannel(int clanId) { return null; }
    @Override public net.runelite.api.clan.ClanSettings getClanSettings(int clanId) { return null; }

    // Sprite overrides are written into the game's cached sprite of the same id and size
    // (HostSprites.Overrides); widget sprite overrides are kept but not applied.
    private final java.util.Map<Integer, net.runelite.api.SpritePixels> spriteOverrides = new HostSprites.Overrides();
    private final java.util.Map<Integer, net.runelite.api.SpritePixels> widgetSpriteOverrides = new java.util.HashMap<>();
    @Override public java.util.Map<Integer, net.runelite.api.SpritePixels> getSpriteOverrides() { return spriteOverrides; }
    @Override public java.util.Map<Integer, net.runelite.api.SpritePixels> getWidgetSpriteOverrides() { return widgetSpriteOverrides; }
    /** Overrides are written into the game's sprite cache directly, so there is nothing to reset. */
    @Override public net.runelite.api.NodeCache getWidgetSpriteCache() { return () -> {}; }
    /**
     * The game's own item icon renderer (createItemSprite, docs/offsets.md): 36x32, border 0/1/2,
     * shadow colour, quantity text per `stackable` (0 never, 1 always, 2 if the item stacks).
     * Game thread only; null elsewhere or while the item's model is still loading. `scale` other
     * than 512 isn't applied.
     */
    @Override
    public net.runelite.api.SpritePixels createItemSprite(int itemId, int quantity, int border, int shadowColor,
                                                          int stackable, boolean noted, int scale) {
        if (!Native.isClientThread()) return null;
        int textMode = 0;
        if (stackable == 1) textMode = 1;
        else if (stackable == 2 && quantity != 1) {
            net.runelite.api.ItemComposition c = getItemDefinition(itemId);
            textMode = c != null && c.isStackable() ? 1 : 0;
        }
        int[] r = Native.itemSprite(itemId, quantity, border, shadowColor, textMode, noted);
        if (r == null) return null;
        int w = r[0], h = r[1];
        return new HostSpritePixels(java.util.Arrays.copyOfRange(r, 2, 2 + w * h), w, h);
    }

    @Override public net.runelite.api.SpritePixels createSpritePixels(int[] pixels, int width, int height) {
        return new HostSpritePixels(pixels, width, height);
    }
    // Chat (mod) icons are kept host-side: plugins (ChatIconManager) append their icons to this
    // array; the game's own chat doesn't draw them. Starts with blank 13x13 slots for the game's icons.
    private net.runelite.api.IndexedSprite[] modIcons;
    @Override public synchronized net.runelite.api.IndexedSprite[] getModIcons() {
        if (modIcons == null) {
            modIcons = new net.runelite.api.IndexedSprite[32];
            for (int i = 0; i < modIcons.length; i++) modIcons[i] = HostIndexedSprite.blank(13, 13);
        }
        return modIcons;
    }
    @Override public synchronized void setModIcons(net.runelite.api.IndexedSprite[] icons) { modIcons = icons; }
    @Override public net.runelite.api.IndexedSprite createIndexedSprite() { return new HostIndexedSprite(); }

    /**
     * OAuthApi.getAccountHash (XP Tracker, GE, profiles): -1 when logged out, as RuneLite; otherwise a
     * stable per-name value (the game's real account hash isn't read).
     */
    @Override public long getAccountHash() {
        if (getGameState() != net.runelite.api.GameState.LOGGED_IN) return -1;
        net.runelite.api.Player p = getLocalPlayer();
        String n = p != null ? p.getName() : null;
        return n == null ? -1 : (n.toLowerCase().hashCode() & 0xffffffffL);
    }
    // No instances yet: an empty template table.
    @Override public int[][][] getInstanceTemplateChunks() { return new int[4][13][13]; }

    // --- inventories ---

    @Override
    public net.runelite.api.ItemContainer getItemContainer(int id) {
        return AndroidItemContainer.of(id);
    }

    @Override
    public net.runelite.api.ItemContainer getItemContainer(net.runelite.api.InventoryID id) {
        return AndroidItemContainer.of(id.getId());
    }

    // --- widgets ---

    @Override
    public net.runelite.api.widgets.Widget getWidget(int componentId) {
        componentId = mapDesktopComponent(componentId);
        return Native.widgetExists(componentId) ? new AndroidWidget(componentId) : null;
    }

    /** Android's root interface (601) has no desktop group ids; route the ones the API needs. */
    private static final int ANDROID_ROOT = 601, ANDROID_MINIMAP_DRAW_AREA = ANDROID_ROOT << 16 | 34,
        ANDROID_SIDE_PANEL = ANDROID_ROOT << 16 | 111, RESIZABLE_INVENTORY_CONTAINER = 161 << 16 | 73;

    static int mapDesktopComponent(int id) {
        switch (id) {
            case net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA:
            case net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA:
            case net.runelite.api.widgets.ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA:
                return Native.rootInterface() == ANDROID_ROOT ? ANDROID_MINIMAP_DRAW_AREA : id;
            case RESIZABLE_INVENTORY_CONTAINER:
                // The side panel (inventory, skills, ...): Status Bars draws its bars beside it.
                return Native.rootInterface() == ANDROID_ROOT ? ANDROID_SIDE_PANEL : id;
            default:
                return id;
        }
    }

    /** Deprecated, but RuneLite's own Perspective.localToMinimap still calls it. */
    @Override
    @SuppressWarnings("deprecation")
    public net.runelite.api.widgets.Widget getWidget(net.runelite.api.widgets.WidgetInfo info) {
        return getWidget(info.getId());
    }

    @Override
    public net.runelite.api.widgets.Widget getWidget(int group, int child) {
        return getWidget(group << 16 | child);
    }

    @Override
    public int getTopLevelInterfaceId() {
        return Native.rootInterface();
    }

    @Override
    public boolean isClientThread() {
        return Native.isClientThread();
    }
}
