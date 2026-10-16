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
        if (isClientThread()) Native.addChatMessage(t, n, m);
        else Host.invoke(() -> Native.addChatMessage(t, n, m));
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

    @Override
    public void runScript(Object... args) {
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

    @Override public net.runelite.api.ScriptEventBuilder createScriptEventBuilder(Object... args) {
        return new AndroidScriptEvent(this, args);
    }

    @Override public int[] getIntStack() { return intStack; }
    @Override public int getIntStackSize() { return intStackSize; }
    @Override public void setIntStackSize(int size) { intStackSize = size; }
    @Override public Object[] getObjectStack() { return objectStack; }
    @Override public int getObjectStackSize() { return objectStackSize; }
    @Override public void setObjectStackSize(int size) { objectStackSize = size; }

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
    @Override public void refreshChat() {}

    /** No physical keyboard modifiers on a touch screen. */
    @Override public boolean isKeyPressed(int keycode) { return false; }

    /** No hover on a touch screen: (-1, -1) is what RuneLite reports with the mouse off the canvas. */
    @Override
    public net.runelite.api.Point getMouseCanvasPosition() {
        return new net.runelite.api.Point(-1, -1);
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
    @Override public boolean isMenuOpen() { return Native.menuOpen(); }
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
        int[] cam = Native.camera();
        double pitch = cam[3] * Math.PI * 2 / 16384, yaw = cam[4] * Math.PI * 2 / 16384;
        double[] f = {-Math.sin(yaw) * Math.cos(pitch), Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch)};
        return (float) (-d * f[axis]);
    }
    @Override public int getCameraPitch() { return Native.camera()[3]; }
    @Override public int getCameraYaw() { return Native.camera()[4]; }
    // Targets (+0x8949c/+0x894a0) aren't read separately; the camera settles on them within frames.
    @Override public int getCameraYawTarget() { return getCameraYaw(); }
    @Override public int getCameraPitchTarget() { return getCameraPitch(); }
    @Override public net.runelite.api.CameraFocusableEntity getCameraFocusEntity() { return getLocalPlayer(); }
    @Override public float getCameraFpX() { return Native.camera()[0] + eyeOffset(0); }
    @Override public float getCameraFpY() { return Native.camera()[1] + eyeOffset(1); }
    @Override public float getCameraFpZ() { return Native.camera()[2] + eyeOffset(2); }
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
        return scaleOverride > 0 ? scaleOverride : Math.round(285f * getViewportHeight() / 720f);
    }
    @Override public boolean isGpu() { return false; }
    @Override public boolean isResized() { return true; }
    @Override public int getViewportXOffset() { return 0; }
    @Override public int getViewportYOffset() { return 0; }
    @Override public int getViewportWidth() { return OverlayRenderer.canvasSize().width; }
    @Override public int getViewportHeight() { return OverlayRenderer.canvasSize().height; }
    @Override public int getCanvasWidth() { return getViewportWidth(); }
    @Override public int getCanvasHeight() { return getViewportHeight(); }

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
        return java.util.EnumSet.noneOf(net.runelite.api.WorldType.class);
    }

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
    private static final int ANDROID_ROOT = 601, ANDROID_MINIMAP_DRAW_AREA = ANDROID_ROOT << 16 | 34;

    static int mapDesktopComponent(int id) {
        switch (id) {
            case net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA:
            case net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA:
            case net.runelite.api.widgets.ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA:
                return Native.rootInterface() == ANDROID_ROOT ? ANDROID_MINIMAP_DRAW_AREA : id;
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
