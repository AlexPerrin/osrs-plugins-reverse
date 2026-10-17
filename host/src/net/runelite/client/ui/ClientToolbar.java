package net.runelite.client.ui;

import android.util.Log;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replaces RuneLite's ClientToolbar (same name). Records sidebar navigation buttons; there
 * is no Swing sidebar on Android, so panels are built headless and not shown yet.
 */
public class ClientToolbar {
    private final List<NavigationButton> buttons = new CopyOnWriteArrayList<>();

    public void addNavigation(NavigationButton button) {
        buttons.add(button);
        Log.i("osrsrl-host", "sidebar button added: " + button.getTooltip());
    }

    public void removeNavigation(NavigationButton button) { buttons.remove(button); }
    private volatile NavigationButton requested;

    /** As desktop: opens the sidebar on this button's panel (the host Sidebar picks it up). */
    public void openPanel(NavigationButton button) { requested = button; }

    /** Host: the button a plugin asked to open since the last call, or null. */
    public NavigationButton takeRequested() {
        NavigationButton b = requested;
        requested = null;
        return b;
    }

    /** Host: buttons added by plugins. */
    public List<NavigationButton> getButtons() { return buttons; }
}
