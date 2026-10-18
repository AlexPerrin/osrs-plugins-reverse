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
        if (Host.sceneReloading) return GameState.LOADING;
        GameState s = GameState.of(Native.gameState());
        // Desktop is LOGGED_IN only once the local player exists. GameStateChanged(LOGGED_IN) waits
        // for it (Host.pendingLoggedIn); the game's player list can still be empty for a few cycles
        // after its state turns 30, and plugins guarded by LOGGED_IN then read a null local player
        // (Runecrafting Utilities' ClientTick). Latched to the posted event, not the live list.
        if (s == GameState.LOGGED_IN && !Host.loggedInPosted) return GameState.LOADING;
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

    // --- getArray: script int arrays (frame object locals). Arrays a plugin fetched during a
    // callback are written back afterwards (desktop hands out the live array). arrayFrameCaller
    // selects the frame a RETURN restores (callbacks raised from ScriptPostFired).
    boolean arrayFrameCaller;
    private final java.util.Map<Integer, int[]> fetchedArrays = new java.util.HashMap<>();

    @Override
    public int[] getArray(int id) {
        if (!isClientThread()) return null;
        int[] a = fetchedArrays.get(id);
        if (a != null) return a;
        a = Native.scriptArray(arrayFrameCaller, id);
        if (a != null) fetchedArrays.put(id, a);
        return a;
    }

    @Override
    public int getArraySizes(int id) {
        int[] a = getArray(id);
        return a != null ? a.length : 0;
    }

    /** Writes back the arrays fetched since the last call (end of a callback). */
    void flushArrays() {
        for (java.util.Map.Entry<Integer, int[]> e : fetchedArrays.entrySet())
            Native.setScriptArray(arrayFrameCaller, e.getKey(), e.getValue());
        fetchedArrays.clear();
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
        // Desktop has the new scene's regions while LOADING (Herbiboar checks them on GameStateChanged(LOADING)).
        if (wv == null || getGameState().getState() < net.runelite.api.GameState.LOADING.getState()) return new int[0];
        if (AndroidWorldView.instanced()) return AndroidWorldView.instanceRegions();
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
    /**
     * 1 while the screen is held, 0 otherwise; during a WidgetDrag the release cycle reads 0 (desktop:
     * the left button is up on the frame the drag completes). // unverified: touch state values outside drags
     */
    @Override public int getMouseCurrentButton() {
        if (dragEventTouch >= 0) return dragEventTouch == 4 ? 0 : 1;
        return Native.touchState() == 3 ? 1 : 0;
    }
    /** Touch state of the WidgetDrag being posted (Host), -1 outside it. */
    int dragEventTouch = -1;

    /** The game canvas is the overlay canvas (UI-canvas units). */
    @Override public java.awt.Dimension getRealDimensions() { return new java.awt.Dimension(getCanvasWidth(), getCanvasHeight()); }
    @Override public java.awt.Dimension getStretchedDimensions() { return getRealDimensions(); }

    // Widget drag from the game's drag state (docs/offsets.md "Widget drag"); null when nothing is held.
    private static net.runelite.api.widgets.Widget dragWidget(int comp, int index) {
        if (comp < 0) return null;
        return AndroidWidget.of(comp, index);
    }
    @Override public net.runelite.api.widgets.Widget getDraggedWidget() {
        int[] d = Native.dragState();
        return d != null ? dragWidget(d[0], d[1]) : null;
    }
    @Override public net.runelite.api.widgets.Widget getDraggedOnWidget() {
        int[] d = Native.dragState();
        return d != null ? dragWidget(d[2], d[3]) : null;
    }
    @Override public boolean isDraggingWidget() {
        int[] d = Native.dragState();
        return d != null && d[4] != 0;
    }
    @Override public int getDragTime() {
        int[] d = Native.dragState();
        return d != null ? d[5] : 0;
    }
    /**
     * null clears the game's target, so the release does no swap (Bank Tags' preventTagTabDrags,
     * Layouts; verified 2026-10-17 gen 25: a tag-tab layout swap left the real bank order unchanged). Setting
     * another widget is not written to the game. // unverified: non-null targets
     */
    @Override public void setDraggedOnWidget(net.runelite.api.widgets.Widget w) {
        if (w == null) Native.dragClearTarget();
        else android.util.Log.w(Host.TAG, "setDraggedOnWidget(" + w.getId() + "): not written to the game");
    }
    private boolean widgetSelected;
    @Override public boolean isWidgetSelected() { return widgetSelected; }
    /** Kept host-side only; the game's spell/item-use target state is not written. */
    @Override public void setWidgetSelected(boolean selected) { widgetSelected = selected; }
    @Override public void setAllWidgetsAreOpTargetable(boolean yes) { }
    /**
     * Non-zero = not the live game. The host runs against a private test server, and Loot Tracker
     * and Grand Exchange skip their runelite.net submissions when this is non-zero, which keeps
     * test-server data out of RuneLite's datasets.
     */
    @Override public int getEnvironment() { return 1; }
    /** Projectiles aren't read from the game yet: always empty. // unverified: no projectile reader */
    private final net.runelite.api.Deque<net.runelite.api.Projectile> projectiles = new net.runelite.api.Deque<net.runelite.api.Projectile>() {
        @Override public void addLast(net.runelite.api.Projectile p) { }
        @Override public void clear() { }
        @Override public java.util.Iterator<net.runelite.api.Projectile> iterator() { return java.util.Collections.emptyIterator(); }
    };
    @Override public net.runelite.api.Deque<net.runelite.api.Projectile> getProjectiles() { return projectiles; }
    private final net.runelite.api.Deque<net.runelite.api.AmbientSoundEffect> ambientSounds = new net.runelite.api.Deque<net.runelite.api.AmbientSoundEffect>() {
        @Override public void addLast(net.runelite.api.AmbientSoundEffect e) { }
        @Override public void clear() { }
        @Override public java.util.Iterator<net.runelite.api.AmbientSoundEffect> iterator() { return java.util.Collections.emptyIterator(); }
    };
    /** Area sounds aren't read from the game: always empty (Music's area-sound muting has nothing to act on). */
    @Override public net.runelite.api.Deque<net.runelite.api.AmbientSoundEffect> getAmbientSoundEffects() { return ambientSounds; }
    /** Desktop's world-map loading percentage; the mobile world map isn't modelled. */
    @Override public int getExpandedMapLoading() { return 0; }
    /** The 8 GE slots read from the game (GeOffers; desktop never returns null here). */
    @Override public net.runelite.api.GrandExchangeOffer[] getGrandExchangeOffers() {
        return GeOffers.read();
    }
    @Override public net.runelite.api.widgets.Widget getSelectedWidget() { return null; }
    // unverified: default (no CS2 input field focus is read from the mobile client yet; Key Remapping only checks it for null)
    @Override public net.runelite.api.widgets.Widget getFocusedInputFieldWidget() { return null; }
    /** The script VM's active component (CC_CREATE/CC_FIND), as desktop's scriptActiveWidget. Game thread. */
    @Override public net.runelite.api.widgets.Widget getScriptActiveWidget() { return scriptWidget(false); }
    @Override public net.runelite.api.widgets.Widget getScriptDotWidget() { return scriptWidget(true); }
    private static net.runelite.api.widgets.Widget scriptWidget(boolean dot) {
        int[] w = Native.scriptActiveWidget(dot);
        if (w == null) return null;
        return AndroidWidget.of(w[0], w[1]);
    }
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

    /**
     * The touch point stands in for the mouse: while a finger is down and for a few seconds after
     * it lifts (Sidebar.hoverPoint), so hover tooltips (Run Energy, Item Stats) show on a tap or a
     * long-press. Otherwise (-1, -1), what RuneLite reports with the mouse off the canvas.
     */
    @Override
    public net.runelite.api.Point getMouseCanvasPosition() {
        java.awt.Point v = DevInput.virtualMouse();
        if (v != null) return new net.runelite.api.Point(v.x, v.y);
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
    // RuneLiteObjects (Quest Helper's fake NPCs): registration is tracked so isActive() answers as on
    // desktop, but nothing is drawn (no scene renderables are injected; orchestrator decision, gen 28).
    private final java.util.Set<net.runelite.api.RuneLiteObjectController> runeLiteObjects =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.runelite.api.RuneLiteObjectController, Boolean>()));
    @Override public void registerRuneLiteObject(net.runelite.api.RuneLiteObjectController o) { if (o != null) runeLiteObjects.add(o); }
    @Override public void removeRuneLiteObject(net.runelite.api.RuneLiteObjectController o) { runeLiteObjects.remove(o); }
    @Override public boolean isRuneLiteObjectRegistered(net.runelite.api.RuneLiteObjectController o) { return runeLiteObjects.contains(o); }

    // Animations aren't read from the game (SeqType): an inert one keeps fake-NPC code that reads
    // getNumFrames() after setAnimation() from failing (Quest Helper's FakeNpc). Nothing animates.
    // unverified: placeholder values (one frame, no frame lengths, no hand items)
    @Override public net.runelite.api.Animation loadAnimation(final int id) {
        if (id < 0) return null;
        return new net.runelite.api.Animation() {
            private int restartMode;
            @Override public int getId() { return id; }
            @Override public boolean isMayaAnim() { return false; }
            @Override public int getNumFrames() { return 1; }
            @Override public int getRestartMode() { return restartMode; }
            @Override public void setRestartMode(int m) { restartMode = m; }
            @Override public int getDuration() { return 0; }
            @Override public int getFrameStep() { return 0; }
            @Override public int[] getFrameLengths() { return new int[] {1}; }
            @Override public int getLeftHandItem() { return -1; }
            @Override public int getRightHandItem() { return -1; }
        };
    }

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
    /**
     * The game's world map (Client +0x418660, the object behind the CS2 worldmap_* opcodes; static RE
     * gen 31, docs/offsets.md "World map"): position, zoom, loaded flag, the surface map area test and
     * the smooth jump, and the regions' map icons (gen 32; region tiles are not read).
     */
    static final int SURFACE_MAP = 0;  // map area id 0 = the main surface map // unverified: from desktop
    private final WorldMapBag worldMap = new WorldMapBag() {
        private int[] state() {
            int[] s = Native.worldMap();
            return s != null && s[0] != 0 ? s : null;
        }
        private final WorldMapDataBag data = new WorldMapDataBag() {
            /**
             * worldmap_coordinmap on the surface map area, level 0. When the area table isn't
             * readable, the surface coordinate band (underground and separate areas sit at
             * y >= 4160 or x < 1024). Quest Helper's far minimap arrow and World Map's overlay use it.
             * // unverified: map id 0 / level 0 as desktop's surfaceContainsPosition
             */
            @Override public boolean surfaceContainsPosition(int x, int y) {
                int r = Native.worldMapContains(SURFACE_MAP, 0, x, y);
                if (r >= 0) return r == 1;
                return x >= 1024 && x < 4160 && y >= 2304 && y < 4160;
            }
        };
        @Override public net.runelite.api.worldmap.WorldMapData getWorldMapData() { return data; }
        @Override public net.runelite.api.Point getWorldMapPosition() {
            int[] s = state();
            return s == null || s[3] < 0 ? null : new net.runelite.api.Point(s[3], s[4]);
        }
        @Override public float getWorldMapZoom() {
            int[] s = state();
            return s == null ? 4f : s[2] / 1000f;
        }
        @Override public void setWorldMapPositionTarget(net.runelite.api.coords.WorldPoint p) {
            if (p != null) Native.worldMapJump(p.getPlane(), p.getX(), p.getY());  // unverified: on the device
        }
        private final net.runelite.api.worldmap.WorldMapRenderer renderer = new net.runelite.api.worldmap.WorldMapRenderer() {
            @Override public boolean isLoaded() {
                int[] s = state();
                return s != null && s[1] != 0;
            }
            private net.runelite.api.worldmap.WorldMapRegion[][] regions = new net.runelite.api.worldmap.WorldMapRegion[0][0];
            private int regionsCycle = Integer.MIN_VALUE, regionsMap = Integer.MIN_VALUE;
            /**
             * The current map's region grid with its icons (game.cpp world_map_icons; outer index =
             * the game's row array). World Map reads it every client tick while loaded, so it is
             * re-read when the map changes and otherwise every 50 cycles. Tiles aren't read.
             */
            @Override public net.runelite.api.worldmap.WorldMapRegion[][] getMapRegions() {
                int[] s = state();
                int map = s == null ? -1 : s[5];
                int cycle = Native.gameCycle();
                if (map == regionsMap && regionsCycle != Integer.MIN_VALUE && cycle >= regionsCycle && cycle - regionsCycle < 50) return regions;
                int[] v = Native.worldMapIcons();
                if (v == null) return regions;  // off the client thread or not loaded: last result
                regionsCycle = cycle;
                regionsMap = map;
                int rows = v[0], cols = v[1];
                @SuppressWarnings("unchecked")
                java.util.List<net.runelite.api.worldmap.WorldMapIcon>[][] icons = new java.util.List[rows][cols];
                for (int i = 2; i + 5 < v.length; i += 6) {
                    int r = v[i], c = v[i + 1];
                    if (r < 0 || r >= rows || c < 0 || c >= cols) continue;
                    if (icons[r][c] == null) icons[r][c] = new java.util.ArrayList<>();
                    final int type = v[i + 2];
                    final net.runelite.api.coords.WorldPoint at = new net.runelite.api.coords.WorldPoint(v[i + 4], v[i + 5], v[i + 3]);
                    icons[r][c].add(new net.runelite.api.worldmap.WorldMapIcon() {
                        @Override public int getType() { return type; }
                        @Override public net.runelite.api.coords.WorldPoint getCoordinate() { return at; }
                        @Override public String toString() { return "WorldMapIcon(" + type + " @ " + at + ")"; }
                    });
                }
                net.runelite.api.worldmap.WorldMapRegion[][] out = new net.runelite.api.worldmap.WorldMapRegion[rows][cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        final java.util.Collection<net.runelite.api.worldmap.WorldMapIcon> list = icons[r][c] == null
                            ? java.util.Collections.<net.runelite.api.worldmap.WorldMapIcon>emptyList()
                            : java.util.Collections.unmodifiableList(icons[r][c]);
                        out[r][c] = () -> list;
                    }
                }
                regions = out;
                return out;
            }
        };
        @Override public net.runelite.api.worldmap.WorldMapRenderer getWorldMapRenderer() { return renderer; }
    };
    @Override public net.runelite.api.worldmap.WorldMap getWorldMap() { return worldMap; }
    /** Map element (mel) config: category from the game's table (game.cpp map_element); null for an unknown id. */
    @Override public net.runelite.api.worldmap.MapElementConfig getMapElementConfig(int id) {
        int[] m = Native.mapElement(id);
        if (m == null) return null;
        final int category = m[0];
        return new net.runelite.api.worldmap.MapElementConfig() {
            @Override public int getCategory() { return category; }
            @Override public net.runelite.api.SpritePixels getMapIcon(boolean large) { return null; }  // unverified: default (sprite id m[1] not decoded)
        };
    }
    /** Minimap zoom isn't read from the game; RuneLite's default. */
    @Override public double getMinimapZoom() { return 4.0; }
    @Override public void setMinimapZoom(double zoom) { }  // unverified: the mobile minimap zoom isn't mapped
    @Override public void setMinimapZoom(boolean enabled) { }
    /**
     * Desktop's minimap dot sprites (item, NPC, player, friend, team, friends chat, clan). The
     * mobile client styles its dots per category instead (dot colour, outline colour, thickness;
     * its `setminimapdotstyle` console command, game `minimap_dot_style`). Plugins get placeholder
     * sprites; when one replaces an entry (Minimap's recolouring), `applyMapDots` sets that
     * category's dot colour to the sprite's main colour, and putting the placeholder back
     * restores the original style. // unverified: not run on the device yet
     */
    private final net.runelite.api.SpritePixels[] mapDotPlaceholders = newMapDots();
    private final net.runelite.api.SpritePixels[] mapDots = mapDotPlaceholders.clone();
    private final net.runelite.api.SpritePixels[] mapDotsSeen = mapDotPlaceholders.clone();
    private final int[][] mapDotOriginal = new int[7][];
    /**
     * Desktop map dot index (item, NPC, player, friend, team, friends chat, clan) -> the mobile
     * minimap's category. The dot draw 0x58cab4 takes the category in w6: items 5 and NPCs 6
     * (constants at its callers; verified on device, gen 22), players by precedence in 0x58c6f0..
     * 0x58c79c: 0 (0x461564), 1 (team, player +0x790), 2 (0x4615ec), 3 (0x4774d0), else 4.
     * // unverified: 0 = friend, 2 = friends chat, 3 = clan are inferred from desktop's precedence
     */
    private static final int[] MOBILE_DOT_CATEGORY = {5, 6, 4, 0, 1, 2, 3};
    /** Desktop's default dot colours, for restoring a category the game hadn't styled. */
    private static final int[] MAP_DOT_DEFAULTS = {0xff0000, 0xffff00, 0xffffff, 0x00ff00, 0x0000ff, 0x8000ff, 0x00ffff};
    private static net.runelite.api.SpritePixels[] newMapDots() {
        net.runelite.api.SpritePixels[] a = new net.runelite.api.SpritePixels[7];
        for (int i = 0; i < a.length; i++) a[i] = new HostSpritePixels(new int[16], 4, 4);
        return a;
    }
    @Override public net.runelite.api.SpritePixels[] getMapDots() { return mapDots; }

    /** Game thread, every few cycles: pushes replaced map dot sprites into the game's dot styles. */
    void applyMapDots() {
        if (getGameState() != GameState.LOGGED_IN) return;  // the minimap's base dot isn't set up before
        for (int i = 0; i < 7; i++) {
            net.runelite.api.SpritePixels sp = mapDots[i];
            if (sp == mapDotsSeen[i]) continue;
            int cat = MOBILE_DOT_CATEGORY[i];
            int[] cur = Native.minimapDot(cat, 0, 0, -1);
            android.util.Log.i(Host.TAG, "map dot " + i + " (mobile category " + cat + ") replaced; game style " + java.util.Arrays.toString(cur) + (sp != null ? " colour " + Integer.toHexString(mainColor(sp)) : ""));
            if (cur == null) return;  // minimap not built yet: retry later
            if (mapDotOriginal[i] == null) mapDotOriginal[i] = cur;
            int[] o = mapDotOriginal[i];
            if (sp == null || sp == mapDotPlaceholders[i]) {
                if (o[0] != 0) Native.minimapDot(cat, o[1], o[2], o[3]);
                else Native.minimapDot(cat, MAP_DOT_DEFAULTS[i], 0, 1);
            } else {
                Native.minimapDot(cat, mainColor(sp), o[0] != 0 ? o[2] : 0, o[0] != 0 ? o[3] : 1);
            }
            mapDotsSeen[i] = sp;
        }
    }

    /** The most common opaque, non-black colour of a sprite (a dot's fill), RGB. */
    private static int mainColor(net.runelite.api.SpritePixels sp) {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        int best = 0, bestN = 0;
        for (int p : sp.getPixels()) {
            int rgb = p & 0xffffff;
            if (p == 0 || rgb == 0) continue;
            int n = counts.merge(rgb, 1, Integer::sum);
            if (n > bestN) { bestN = n; best = rgb; }
        }
        return best;
    }

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
    /** AWT events dispatched here become Android input (DevInput). */
    @Override public java.awt.Canvas getCanvas() { return DevInput.CANVAS; }
    /** Runs an entry through the game's menu (DevMenu). Game thread. */
    @Override public void menuAction(int p0, int p1, net.runelite.api.MenuAction action, int id, int itemId, String option, String target) {
        DevMenu.menuAction(p0, p1, action, id, itemId, option, target);
    }
    /** RuneLite's 3D zoom is the viewport zoom (same value as getScale). */
    @Override public int get3dZoom() { return getScale(); }
    @Override public int getViewportXOffset() { int[] z = gameViewport(); return z != null ? z[1] : 0; }
    @Override public int getViewportYOffset() { int[] z = gameViewport(); return z != null ? z[2] : 0; }
    @Override public int getViewportWidth() { int[] z = gameViewport(); return z != null ? z[3] : OverlayRenderer.canvasSize().width; }
    @Override public int getViewportHeight() { int[] z = gameViewport(); return z != null ? z[4] : OverlayRenderer.canvasSize().height; }
    /** Centre of the 3D viewport on the canvas. */
    @Override public int getCenterX() { return getViewportXOffset() + getViewportWidth() / 2; }
    @Override public int getCenterY() { return getViewportYOffset() + getViewportHeight() / 2; }
    /** Frames per second the game draws (BeforeRender counted per second; the ImGui overlay shows the same rate). */
    @Override public int getFPS() { return Host.fps; }
    // The mobile client has no stretched mode, minimap ctrl-zoom, free camera / oculus orb or camera
    // shake toggle; these report desktop's defaults. No Jagex launcher either (null display name, as
    // RuneLite started without it).
    @Override public boolean isStretchedEnabled() { return false; }
    @Override public boolean isStretchedFast() { return false; }
    @Override public boolean isMinimapZoom() { return false; }
    @Override public int getCameraMode() { return 0; }
    @Override public int getOculusOrbState() { return 0; }
    @Override public boolean isCameraShakeDisabled() { return false; }
    @Override public String getLauncherDisplayName() { return null; }
    /** A varbit's value within a given varp array (as getVarbitValue reads the live one). */
    @Override public int getVarbitValue(int[] varps, int varbit) {
        int[] d = Native.varbitDef(varbit);
        if (d == null || varps == null || d[0] < 0 || d[0] >= varps.length) return 0;
        int bits = d[2] - d[1] + 1;
        int mask = bits >= 32 ? -1 : (1 << bits) - 1;
        return (varps[d[0]] >>> d[1]) & mask;
    }
    /**
     * The server's IF_SETEVENTS settings covering a widget (Menu Entry Swapper's UI swaps read its click
     * mask), or null when none covers it, as desktop's widget flags table. // unverified: on the device.
     */
    @Override public net.runelite.api.widgets.WidgetConfigNode getWidgetConfig(net.runelite.api.widgets.Widget w) {
        if (w == null) return null;
        int index = w.getIndex();
        int[] e = Native.widgetEvents(w.getId(), index);
        if (e == null) return null;
        long hash = ((long) w.getId() << 32) | (index & 0xffffffffL);
        return new net.runelite.api.widgets.WidgetConfigNode() {
            @Override public int getClickMask() { return e[0]; }
            @Override public int getOpMask() { return e[1]; }
            @Override public long getHash() { return hash; }
            @Override public net.runelite.api.Node getNext() { return null; }
            @Override public net.runelite.api.Node getPrevious() { return null; }
        };
    }
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

    @Override public boolean isInInstancedRegion() { return AndroidWorldView.instanced(); }

    @Override
    public java.util.EnumSet<net.runelite.api.WorldType> getWorldType() {
        return net.runelite.api.WorldType.fromMask(Native.worldFlags());
    }

    // Client +0x3174 / +0x2f64 (the CS2 map_world / worldflags handlers).
    @Override public int getWorld() { return Native.world(); }
    @Override public net.runelite.api.World createWorld() { return new AndroidWorld(); }
    /** GE item search results that OC_FINDNEXT returns (game Client +0x417ba8; unverified on device). */
    @Override public void setGeSearchResultIds(short[] ids) { if (isClientThread()) Native.setGeSearch(ids, Integer.MIN_VALUE, Integer.MIN_VALUE); }
    @Override public void setGeSearchResultCount(int count) { if (isClientThread()) Native.setGeSearch(null, count, Integer.MIN_VALUE); }
    @Override public void setGeSearchResultIndex(int index) { if (isClientThread()) Native.setGeSearch(null, Integer.MIN_VALUE, index); }
    /**
     * Desktop sets the world the next login connects to. The mobile client picks its world in its
     * own world list, so this only logs. // unverified: no world switch is attempted
     */
    @Override public void changeWorld(net.runelite.api.World world) {
        android.util.Log.i(Host.TAG, "changeWorld(" + world + ") ignored: the mobile client picks the world itself");
    }
    /** Marks a skill changed (as a stat update does) so the skill tab's onStatTransmit scripts redraw it (Virtual Levels). */
    @Override public void queueChangedSkill(net.runelite.api.Skill skill) {
        if (skill != null && isClientThread()) Native.queueChangedSkill(skill.ordinal());
    }

    /**
     * As on desktop, the `tupleIndex`-th field of every tuple in the column (a column of type
     * (int, string) with three tuples gives three ints for 0, three strings for 1), through the
     * game's db_getfieldcount and db_getfield. Null off the client thread.
     */
    @Override
    public Object[] getDBTableField(int rowID, int column, int tupleIndex) {
        if (!isClientThread()) return null;
        // The lookups run the game's db_getfield handler on the script VM's stacks, which can't be
        // used while a script runs (script events, runelite_callback emulation such as
        // questFilter). Database rows are static cache data, so results are kept; one asked for
        // mid-script that isn't known yet reads as empty and is fetched on the next cycle.
        long key = ((long) rowID << 32) | ((long) (column & 0xffff) << 16) | (tupleIndex & 0xffff);
        Object[] cached = dbCache.get(key);
        if (cached != null) return cached.clone();
        if (liveStacks) {
            dbWanted.add(key);
            return new Object[0];
        }
        Object[] r = readDBTableField(rowID, column, tupleIndex);
        if (r.length > 0 && dbCache.size() < 65536) dbCache.put(key, r.clone());
        return r;
    }

    private final java.util.Map<Long, Object[]> dbCache = new java.util.HashMap<>();
    private final java.util.Set<Long> dbWanted = new java.util.LinkedHashSet<>();

    /** Game thread, each cycle outside scripts: fetches the db fields asked for mid-script. */
    void fetchWantedDbFields() {
        if (dbWanted.isEmpty() || liveStacks) return;
        java.util.List<Long> keys = new java.util.ArrayList<>(dbWanted);
        dbWanted.clear();
        for (long k : keys) {
            Object[] r = readDBTableField((int) (k >>> 32), (int) (k >>> 16) & 0xffff, (int) k & 0xffff);
            if (r.length > 0 && dbCache.size() < 65536) dbCache.put(k, r);
        }
    }

    /** Remembers a field so it is known before a script asks for it (see getDBTableField). */
    void wantDbField(int rowID, int column, int tupleIndex) {
        long key = ((long) rowID << 32) | ((long) (column & 0xffff) << 16) | (tupleIndex & 0xffff);
        if (!dbCache.containsKey(key)) dbWanted.add(key);
    }

    private Object[] readDBTableField(int rowID, int column, int tupleIndex) {
        int n = Native.dbFieldCount(rowID, column);
        // A row without values in the column reads the table's column default (db_getfield on
        // tuple 0 returns it), as on desktop.
        if (n == 0) n = 1;
        java.util.List<Object> out = new java.util.ArrayList<>();
        for (int t = 0; t < n && t < 4096; t++) {
            Object[] r = Native.dbField(rowID, column, t);
            if (r != null && tupleIndex >= 0 && tupleIndex < r.length) out.add(r[tupleIndex]);
        }
        return out.toArray();
    }

    // Friends chat / clans: not in a channel (RuneLite returns null then).
    @Override public net.runelite.api.FriendsChatManager getFriendsChatManager() { return ChatChannels.friendsChat(); }
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
    /**
     * As desktop: closes the sub-interface (the node from getComponentTable: hash = component,
     * id = group) through the game's own IF_CLOSESUB path. Client thread only, as on desktop.
     * Verified 2026-10-17 gen 29 (probe `wclose 12 0` closed the bank; client-only, as desktop).
     */
    @Override public void closeInterface(net.runelite.api.WidgetNode node, boolean unload) {
        if (node == null) return;
        Native.closeSubInterface((int) node.getHash(), node.getId(), unload);
    }
    /**
     * As desktop: the scene's minimap image of a plane (736x736 = desktop's 184-tile extended scene
     * at 4 px per tile; the mobile 104-tile scene is the middle 416 px, as Instance Map crops it with
     * getExpandedMapLoading() 0), drawn by the game's own minimap renderer (`Native.drawMinimap`).
     * Client thread.
     */
    @Override public net.runelite.api.SpritePixels drawInstanceMap(int plane) {
        int[] px = Native.drawMinimap(plane);
        return px == null ? null : new HostSpritePixels(px, 736, 736);
    }
    /** Only LOADING is honoured (plugins use it to rebuild the scene), via the game's own reload. */
    @Override public void setGameState(GameState state) {
        if (state != GameState.LOADING) return;
        if (isClientThread()) Native.reloadScene();
        else Host.invoke(Native::reloadScene);
    }
    @Override public net.runelite.api.NameableContainer<net.runelite.api.Ignore> getIgnoreContainer() { return ignores; }
    private AndroidNameables.Entry[] friendsCycle = new AndroidNameables.Entry[0];
    private int friendsCycleStamp = -1;
    /**
     * Whether a player is on the friends list (Player.isFriend: desktop sets the flag per player
     * from the same list). The list is read once per cycle; draw listeners ask for every player.
     */
    boolean isFriendCached(String name) {
        if (name == null) return false;
        if (friendsCycleStamp != Host.cycleStamp) {
            friendsCycle = AndroidNameables.read(false);
            friendsCycleStamp = Host.cycleStamp;
        }
        return AndroidNameables.find(friendsCycle, name) != null;
    }
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
    @Override public net.runelite.api.clan.ClanChannel getClanChannel() { return ChatChannels.clan(net.runelite.api.clan.ClanID.CLAN); }
    @Override public net.runelite.api.clan.ClanChannel getGuestClanChannel() { return ChatChannels.clan(-1); }
    @Override public net.runelite.api.clan.ClanSettings getClanSettings() { return ChatChannels.settings(net.runelite.api.clan.ClanID.CLAN); }
    @Override public net.runelite.api.clan.ClanSettings getGuestClanSettings() { return ChatChannels.settings(-1); }
    @Override public net.runelite.api.clan.ClanChannel getClanChannel(int clanId) { return clanId >= 0 ? ChatChannels.clan(clanId) : null; }
    @Override public net.runelite.api.clan.ClanSettings getClanSettings(int clanId) { return clanId >= 0 ? ChatChannels.settings(clanId) : null; }

    // Sprite overrides are written into the game's cached sprite of the same id and size
    // (HostSprites.Overrides); widget sprite overrides are kept but not applied.
    private final java.util.Map<Integer, net.runelite.api.SpritePixels> spriteOverrides = new HostSprites.Overrides();
    private final java.util.Map<Integer, net.runelite.api.SpritePixels> widgetSpriteOverrides = new java.util.HashMap<>();
    @Override public java.util.Map<Integer, net.runelite.api.SpritePixels> getSpriteOverrides() { return spriteOverrides; }
    @Override public java.util.Map<Integer, net.runelite.api.SpritePixels> getWidgetSpriteOverrides() { return widgetSpriteOverrides; }
    /** Overrides are written into the game's sprite cache directly, so there is nothing to reset. */
    @Override public net.runelite.api.NodeCache getWidgetSpriteCache() { return () -> {}; }
    /** Struct compositions are read from the game per call (nothing cached host-side), so reset is a no-op. PostStructComposition is not posted. */
    @Override public net.runelite.api.NodeCache getStructCompositionCache() { return () -> {}; }
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
    // Chat (mod) icons: the font's <img=N> table. The array starts with placeholders for the game's
    // icons (sized from the game); plugins (ChatIconManager: Emojis, chat rank icons) append or replace
    // entries, and syncModIcons hands the result to the native side, which installs it in the game.
    private net.runelite.api.IndexedSprite[] modIcons, syncedIcons;
    private Object[] syncedPixels;
    @Override public synchronized net.runelite.api.IndexedSprite[] getModIcons() {
        if (modIcons == null) {
            int[] sizes = Native.modIconSizes();
            if (sizes == null) return null;  // not loaded yet (desktop: null too)
            modIcons = new net.runelite.api.IndexedSprite[sizes.length / 2];
            for (int i = 0; i < modIcons.length; i++) modIcons[i] = HostIndexedSprite.game(i, sizes[2 * i], sizes[2 * i + 1]);
        }
        return modIcons;
    }
    @Override public synchronized void setModIcons(net.runelite.api.IndexedSprite[] icons) {
        modIcons = icons;
        syncModIcons();
    }

    /** Game thread, every cycle and on setModIcons: pushes the table when an entry changed. */
    synchronized void syncModIcons() {
        net.runelite.api.IndexedSprite[] icons = modIcons;
        if (icons == null) return;
        int n = icons.length;
        boolean same = syncedIcons != null && syncedIcons.length == n;
        for (int i = 0; same && i < n; i++)
            same = syncedIcons[i] == icons[i] && (icons[i] == null || syncedPixels[i] == icons[i].getPixels());
        if (same) return;
        syncedIcons = icons.clone();
        syncedPixels = new Object[n];
        boolean custom = false;
        int[] meta = new int[n * 7];
        byte[][] px = new byte[n][];
        int[][] pal = new int[n][];
        for (int i = 0; i < n; i++) {
            net.runelite.api.IndexedSprite s = icons[i];
            syncedPixels[i] = s != null ? s.getPixels() : null;
            int at = i * 7;
            if (s instanceof HostIndexedSprite && ((HostIndexedSprite) s).gameIndex >= 0) {
                meta[at] = ((HostIndexedSprite) s).gameIndex;
                continue;
            }
            meta[at] = -1;
            if (s == null) continue;
            custom = true;
            meta[at + 1] = s.getWidth(); meta[at + 2] = s.getHeight();
            meta[at + 3] = s.getOffsetX(); meta[at + 4] = s.getOffsetY();
            meta[at + 5] = s.getOriginalWidth(); meta[at + 6] = s.getOriginalHeight();
            px[i] = s.getPixels();
            pal[i] = s.getPalette();
        }
        // Nothing to install while the table is only the game's own icons.
        if (custom || syncedOnce) { Native.setModIcons(meta, px, pal); syncedOnce = true; }
    }
    private boolean syncedOnce;

    // Preferences (Metronome reads the sound effect volume): the game's own audio settings aren't
    // read; values a plugin sets are kept.
    private final net.runelite.api.Preferences preferences = new net.runelite.api.Preferences() {
        private String remembered = "";
        private int sfx = 127, areaSfx = 127;
        @Override public String getRememberedUsername() { return remembered; }
        @Override public void setRememberedUsername(String u) { remembered = u != null ? u : ""; }
        @Override public int getSoundEffectVolume() { return sfx; }
        @Override public void setSoundEffectVolume(int v) { sfx = v; }
        @Override public int getAreaSoundEffectVolume() { return areaSfx; }
        @Override public void setAreaSoundEffectVolume(int v) { areaSfx = v; }
        @Override public boolean getHideUsername() { return false; }
    };
    @Override public net.runelite.api.Preferences getPreferences() { return preferences; }
    /** The login name (KPH Tracker): the local player's name, as the test accounts log in by name. */
    @Override public String getUsername() {
        net.runelite.api.Player p = Host.localPlayer();
        return p != null && p.getName() != null ? p.getName() : "";
    }

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
    @Override public int[][][] getInstanceTemplateChunks() { return AndroidWorldView.templateChunks(); }

    // --- inventories ---

    @Override
    public net.runelite.api.ItemContainer getItemContainer(int id) {
        return AndroidItemContainer.of(id);
    }

    @Override
    public net.runelite.api.ItemContainer getItemContainer(net.runelite.api.InventoryID id) {
        return AndroidItemContainer.of(id.getId());
    }

    /** Every inventory the client holds, keyed by inventory id (snapshots, as getItemContainer). */
    @Override
    public net.runelite.api.HashTable<net.runelite.api.ItemContainer> getItemContainers() {
        final java.util.LinkedHashMap<Long, net.runelite.api.ItemContainer> m = new java.util.LinkedHashMap<>();
        int[] ids = Native.inventoryIds();
        if (ids != null) for (int id : ids) {
            net.runelite.api.ItemContainer c = AndroidItemContainer.of(id);
            if (c != null) m.put((long) id, c);
        }
        return new net.runelite.api.HashTable<net.runelite.api.ItemContainer>() {
            @Override public net.runelite.api.ItemContainer get(long key) { return m.get(key); }
            @Override public java.util.Iterator<net.runelite.api.ItemContainer> iterator() { return m.values().iterator(); }
        };
    }

    // --- widgets ---

    @Override
    public net.runelite.api.widgets.Widget getWidget(int componentId) {
        componentId = mapDesktopComponent(componentId);
        return Native.widgetExists(componentId) ? AndroidWidget.of(componentId) : null;
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

    /** The toplevel interface's root components (layout parent -1), as desktop's getWidgetRoots. */
    @Override
    public net.runelite.api.widgets.Widget[] getWidgetRoots() {
        int group = Native.rootInterface();
        if (group < 0) return new net.runelite.api.widgets.Widget[0];
        java.util.List<net.runelite.api.widgets.Widget> out = new java.util.ArrayList<>();
        for (int c = 0, n = Native.widgetChildCount(group); c < n; c++) {
            int[] g = Native.widgetGeometry(group << 16 | c);
            if (g != null && g.length > 4 && g[4] == -1) out.add(getWidget(group << 16 | c));
        }
        return out.toArray(new net.runelite.api.widgets.Widget[0]);
    }

    /** The mobile client's revision (build 241.4). */
    @Override public int getRevision() { return 241; }

    /**
     * Desktop's IF1 inventory drag delay (5 cycles by default). The mobile drag block
     * (`0x41d0a4`) only compares each widget's own dead time `+0x324` (all mobile inventories are
     * IF3 children, which Anti Drag sets directly), so there is no global to write; kept for reads.
     * // unverified: no IF1 inventory drag path found in the mobile drag block (static RE gen 29)
     */
    private volatile int inventoryDragDelay = 5;
    @Override public void setInventoryDragDelay(int delay) { inventoryDragDelay = delay; }

    @Override
    public boolean isClientThread() {
        return Native.isClientThread();
    }

    // ---- Plugin Hub needs (2026-10-16) ----

    private volatile Thread clientThread;
    private final java.util.Map<Integer, java.util.List<Integer>> dbTableRows = new java.util.HashMap<>();
    private int itemCount = -1;

    /** The game thread (Thread objects compare equal by identity, as plugins use it). */
    @Override public Thread getClientThread() {
        if (isClientThread() && !Edt.isEdt()) clientThread = Thread.currentThread();  // the EDT only stands in for it
        return clientThread;
    }

    /**
     * Rows of a DB table, found by scanning row ids through the game's DBRowType cache (rows are
     * numbered densely across tables); scanned once, then cached. Client thread only (empty
     * otherwise, and not cached).
     */
    @Override public java.util.List<Integer> getDBTableRows(int table) {
        synchronized (dbTableRows) {
            java.util.List<Integer> cached = dbTableRows.get(table);
            if (cached != null) return cached;
        }
        if (!isClientThread()) return java.util.Collections.emptyList();
        java.util.Map<Integer, java.util.List<Integer>> all = new java.util.HashMap<>();
        int misses = 0;
        for (int row = 0; row <= 0xffff && misses < 4096; row++) {
            int t = Native.dbRowTable(row);
            if (t < 0) { misses++; continue; }
            misses = 0;
            all.computeIfAbsent(t, k -> new java.util.ArrayList<>()).add(row);
        }
        synchronized (dbTableRows) {
            for (java.util.Map.Entry<Integer, java.util.List<Integer>> e : all.entrySet())
                dbTableRows.put(e.getKey(), java.util.Collections.unmodifiableList(e.getValue()));
            java.util.List<Integer> r = dbTableRows.get(table);
            if (r == null) dbTableRows.put(table, r = java.util.Collections.emptyList());
            return r;
        }
    }

    /**
     * Rows of `table` whose `column` holds `value` in field `tupleIndex` of any tuple (desktop
     * uses the cache's DB index; this scans the table's rows). Client thread only.
     */
    @Override public java.util.List<Integer> getDBRowsByValue(int table, int column, int tupleIndex, Object value) {
        if (!isClientThread() || value == null) return java.util.Collections.emptyList();
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int row : getDBTableRows(table)) {
            Object[] vals = getDBTableField(row, column, tupleIndex);
            if (vals == null) continue;
            for (Object v : vals) {
                if (value.equals(v) || (value instanceof Number && v instanceof Number
                        && ((Number) value).longValue() == ((Number) v).longValue())) {
                    out.add(row);
                    break;
                }
            }
        }
        return out;
    }

    /** Number of item definitions: one past the highest id the game's obj cache resolves. */
    @Override public int getItemCount() {
        if (itemCount >= 0 || !isClientThread()) return Math.max(itemCount, 0);
        int lo = 0, hi = 0x10000;  // lookup(lo) exists, lookup(hi) assumed not
        if (getItemDefinition(hi - 1) != null) return itemCount = hi;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (getItemDefinition(mid) != null) lo = mid; else hi = mid;
        }
        return itemCount = hi;
    }

    /** Followers (pets) aren't tracked yet. */
    @Override public net.runelite.api.NPC getFollower() { return null; }

    @Override public net.runelite.api.Deque<net.runelite.api.GraphicsObject> getGraphicsObjects() {
        return Inert.of(net.runelite.api.Deque.class);
    }

    /** The sprite index stands for the game's sprite cache; getSprites reads frame 0 from it. */
    private final net.runelite.api.IndexDataBase spriteIndex = Inert.of(net.runelite.api.IndexDataBase.class);
    @Override public net.runelite.api.IndexDataBase getIndexSprites() { return spriteIndex; }

    @Override public net.runelite.api.SpritePixels[] getSprites(net.runelite.api.IndexDataBase source, int archiveId, int fileId) {
        if (source != spriteIndex || fileId != 0 || !isClientThread()) return null;
        int[] r = Native.sprite(archiveId);
        if (r == null) return null;
        int w = r[0], h = r[1];
        return new net.runelite.api.SpritePixels[] {new HostSpritePixels(java.util.Arrays.copyOfRange(r, 2, 2 + w * h), w, h)};
    }
}
