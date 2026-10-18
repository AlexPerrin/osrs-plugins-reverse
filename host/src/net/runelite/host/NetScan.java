package net.runelite.host;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Load-time network gate for plugin jars (docs/app-telemetry.md, "Plugins and java.net", R1).
 * NetGuard only sees the shared OkHttp client; a plugin that uses java.net / javax.net directly
 * would bypass it. Before a jar is loaded, its dex string tables are searched for the type
 * descriptors of java.net I/O classes (the same list as host/plugins/hub/netcheck.sh); such a jar
 * is not loaded unless its file name (without .jar) is vetted below or listed in
 * `debug.osrsrl.netallow` (comma-separated). Nothing process-wide changes: the app's own Java
 * SDKs and the game's native sockets are untouched.
 */
final class NetScan {
    private NetScan() {}

    /** Jars reviewed by hand: raw java.net only in bundled code the plugin doesn't call. */
    private static final java.util.Set<String> VETTED = new java.util.HashSet<>(java.util.Arrays.asList(
            "hub-loot-lookup",  // jsoup's HttpConnection; the plugin fetches via the injected OkHttp client
            "devtools"));  // the devtools bridge's local HTTP server (InetSocketAddress), reached over adb only

    private static final String[] RAW = {
        "Ljava/net/URLConnection;", "Ljava/net/HttpURLConnection;", "Ljava/net/JarURLConnection;",
        "Ljava/net/Socket;", "Ljava/net/ServerSocket;", "Ljava/net/DatagramSocket;", "Ljava/net/MulticastSocket;",
        "Ljava/net/InetAddress;", "Ljava/net/Inet4Address;", "Ljava/net/Inet6Address;", "Ljava/net/InetSocketAddress;",
        "Ljava/net/Proxy;", "Ljava/net/ProxySelector;", "Ljava/net/CookieHandler;", "Ljava/net/Authenticator;",
        "Ljava/net/URLStreamHandler;", "Ljava/net/SocketImpl;", "Ljava/net/http/", "Ljavax/net/",
    };

    /** null if the jar may load, else the reason it may not. */
    static String refusal(java.io.File jar) {
        return refusal(jar, Native.property("debug.osrsrl.netallow"));
    }

    /** Same, with the allow-list given (comma-separated jar names without .jar); used by host/tools/netscan-check.sh. */
    static String refusal(java.io.File jar, String allow) {
        String name = jar.getName().replaceFirst("\\.jar$", "");
        if (VETTED.contains(name)) return null;
        for (String a : (allow == null ? "" : allow).split(","))
            if (!a.trim().isEmpty() && a.trim().equals(name)) return null;
        java.util.List<String> hits = new java.util.ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.getName().matches("classes\\d*\\.dex")) continue;
                byte[] dex = readAll(zip.getInputStream(e));
                for (String d : RAW) if (indexOf(dex, d) >= 0 && !hits.contains(d)) hits.add(d);
                // java.net.URL is often only a value type; it does I/O through openConnection/openStream.
                if (indexOf(dex, "Ljava/net/URL;") >= 0 && (indexOf(dex, "openConnection") >= 0 || indexOf(dex, "openStream") >= 0))
                    hits.add("Ljava/net/URL; openConnection/openStream");
            }
        } catch (java.io.IOException ex) {
            return "unreadable (" + ex.getMessage() + ")";
        }
        return hits.isEmpty() ? null : "java.net outside NetGuard: " + hits;
    }

    private static int indexOf(byte[] hay, String needle) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + n.length <= hay.length; i++) {
            if (hay[i] != n[0]) continue;
            for (int k = 1; k < n.length; k++) if (hay[i + k] != n[k]) continue outer;
            return i;
        }
        return -1;
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
        in.close();
        return out.toByteArray();
    }
}
