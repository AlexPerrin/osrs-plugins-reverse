package net.runelite.host;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Opens links for LinkBrowser (net.runelite.client.util): an ACTION_VIEW intent from the game's Activity. */
public final class AndroidLinks {
    private AndroidLinks() {}

    /** http(s) only; false when there is no Activity yet. The browser opens on the main thread. */
    public static boolean view(final String url) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            Log.w(Host.TAG, "LinkBrowser: not a web link, ignored: " + url);
            return false;
        }
        final Activity a;
        try {
            a = Sidebar.currentActivity();
        } catch (Exception e) {
            Log.e(Host.TAG, "LinkBrowser: no activity", e);
            return false;
        }
        if (a == null) return false;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                a.startActivity(i);
                Log.i(Host.TAG, "LinkBrowser: opened " + url);
            } catch (Throwable t) {
                Log.e(Host.TAG, "LinkBrowser: could not open " + url, t);
            }
        });
        return true;
    }
}
