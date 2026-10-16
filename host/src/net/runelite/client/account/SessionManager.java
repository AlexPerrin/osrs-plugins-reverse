package net.runelite.client.account;

/**
 * Replaces RuneLite's SessionManager (same name). There is no RuneLite account login on
 * Android (the desktop one runs a local com.sun HttpServer for the OAuth callback), so there is
 * never a session: plugins that sync with runelite.net (Loot Tracker) stay local.
 */
public class SessionManager {
    public AccountSession getAccountSession() { return null; }
    public void loadSession() {}
    public void login() {}
    public void logout() {}
}
