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

    @Override
    public boolean isClientThread() {
        return Native.isClientThread();
    }
}
