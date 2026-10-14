package net.runelite.host;

import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;

/** net.runelite.api.Client backed by the native game. Methods not overridden here throw. */
public class AndroidClient extends ClientStub {
    private final AndroidPlayer localPlayer = new AndroidPlayer();
    private final AndroidWorldView worldView;

    AndroidClient(AndroidWorldView worldView) {
        this.worldView = worldView;
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

    @Override
    public Player getLocalPlayer() {
        return getGameState() == GameState.LOGGED_IN ? localPlayer : null;
    }

    // --- camera and viewport (the viewport is the whole logical overlay canvas) ---

    /** Projection scale; calibrated against the game's GPU camera, see docs/offsets.md. */
    static volatile int scale = 1000;

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
                if (!v.isEmpty()) scale = Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return scale;
    }
    @Override public boolean isGpu() { return false; }
    @Override public boolean isResized() { return true; }
    @Override public int getViewportXOffset() { return 0; }
    @Override public int getViewportYOffset() { return 0; }
    @Override public int getViewportWidth() { return OverlayRenderer.canvasSize().width; }
    @Override public int getViewportHeight() { return OverlayRenderer.canvasSize().height; }
    @Override public int getCanvasWidth() { return getViewportWidth(); }
    @Override public int getCanvasHeight() { return getViewportHeight(); }

    @Override
    public boolean isClientThread() {
        return Native.isClientThread();
    }
}
