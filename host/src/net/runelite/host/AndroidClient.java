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
        // Only the top-level world view (-1) exists until world entities (boats) are mapped.
        return id == -1 ? getTopLevelWorldView() : null;
    }

    @Override
    public WorldView getTopLevelWorldView() {
        return getGameState() == GameState.LOGGED_IN ? worldView : null;
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
        return getGameState() == GameState.LOGGED_IN ? Host.localPlayer() : null;
    }

    // --- camera and viewport (the viewport is the whole logical overlay canvas) ---

    /**
     * Projection scale fitted to on-screen NPC positions at the default zoom: 285 for a 720-high
     * canvas (docs/offsets.md), scaled to the canvas height. The zoom field isn't mapped yet.
     */
    static volatile int scaleOverride = -1;

    @Override public int getCameraX() { return Native.camera()[0]; }
    @Override public int getCameraY() { return Native.camera()[1]; }
    @Override public int getCameraZ() { return Native.camera()[2]; }
    @Override public int getCameraPitch() { return Native.camera()[3]; }
    @Override public int getCameraYaw() { return Native.camera()[4]; }
    @Override public float getCameraFpX() { return getCameraX(); }
    @Override public float getCameraFpY() { return getCameraY(); }
    @Override public float getCameraFpZ() { return getCameraZ(); }
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
        return Native.widgetExists(componentId) ? new AndroidWidget(componentId) : null;
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
