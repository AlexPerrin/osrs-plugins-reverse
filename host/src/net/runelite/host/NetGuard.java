package net.runelite.host;

import android.util.Log;

/**
 * OkHttp interceptor on the host's shared client (plugins get it by injection; clients derived
 * with newBuilder() keep it). The host plays on a private test server, so anything that would
 * upload to RuneLite or third parties is refused by default:
 * - every request that isn't GET/HEAD (loot tracker, GE, chat command and crowdsourcing
 *   submissions are POSTs);
 * - GETs that trigger a remote write: an `xp/update` path on any host (XP Tracker asks RuneLite to
 *   refresh the hiscores of the logged-out player's name).
 * Read-only GETs (static.runelite.net data, prices, hiscore lookups, the wiki) pass.
 * `setprop debug.osrsrl.netwrite 1` (read per request) lets everything through;
 * `setprop debug.osrsrl.netlog 1` logs every request (tag osrsrl-net).
 */
final class NetGuard implements okhttp3.Interceptor {
    static final String TAG = "osrsrl-net";

    @Override
    public okhttp3.Response intercept(Chain chain) throws java.io.IOException {
        okhttp3.Request req = chain.request();
        String why = blockReason(req);
        boolean allowAll = "1".equals(prop("debug.osrsrl.netwrite"));
        if ("1".equals(prop("debug.osrsrl.netlog")) || (why != null && !allowAll)) {
            Log.i(TAG, (why != null && !allowAll ? "blocked (" + why + "): " : "") + req.method() + " " + req.url().host() + req.url().encodedPath());
        }
        if (why == null || allowAll) return chain.proceed(req);
        throw new java.io.IOException("blocked by the plugin host: " + why + " (setprop debug.osrsrl.netwrite 1 to allow)");
    }

    static String blockReason(okhttp3.Request req) {
        String m = req.method();
        if (!"GET".equals(m) && !"HEAD".equals(m)) return "upload " + m;
        okhttp3.HttpUrl u = req.url();
        java.util.List<String> seg = u.pathSegments();
        // Matched on the path whatever the host (a mirror or proxy of the API would be the same write).
        for (int i = 0; i + 1 < seg.size(); i++)
            if ("xp".equals(seg.get(i)) && "update".equals(seg.get(i + 1))) return "xp update";
        return null;
    }

    private static String prop(String name) {
        try {
            return Native.property(name);
        } catch (Throwable t) {
            return "";
        }
    }
}
