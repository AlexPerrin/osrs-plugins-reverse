package net.runelite.client.util;

import net.runelite.host.AndroidLinks;

/**
 * Same-name replacement of RuneLite's LinkBrowser: desktop uses java.awt.Desktop / xdg-open; here
 * web links open in the device's browser through an ACTION_VIEW intent. Local paths (open) have no
 * file manager to go to and are only logged.
 */
public class LinkBrowser {
    public LinkBrowser() {}

    public static void browse(String url) {
        AndroidLinks.view(url);
    }

    public static void open(String directory) {
        android.util.Log.i("osrsrl-host", "LinkBrowser.open(" + directory + ") ignored: no file manager on the device");
    }
}
