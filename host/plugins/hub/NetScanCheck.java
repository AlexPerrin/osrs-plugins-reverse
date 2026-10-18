package net.runelite.host;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Off-device check of the host's load-time gate (NetScan, R1 in docs/app-telemetry.md). Run by
 * host/plugins/hub/netscan-check.sh, which compiles it with NetScan.java and a stub Native.
 * First a self-test on synthetic jars, then one line per jar given: "load" or "REFUSE <reason>".
 * Exit status: 1 if the self-test fails, else 0.
 */
public final class NetScanCheck {
    public static void main(String[] args) throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("netscan").toFile();
        int fail = 0;
        fail += expect(jar(dir, "t-clean", "Lnet/runelite/api/Client;Ljava/net/URI;"), "", false);
        fail += expect(jar(dir, "t-socket", "Ljava/net/Socket;"), "", true);
        fail += expect(jar(dir, "t-socket", "Ljava/net/Socket;"), "x, t-socket ,y", false);
        fail += expect(jar(dir, "t-socket", "Ljava/net/Socket;"), "t-sock", true);
        fail += expect(jar(dir, "t-http", "Ljava/net/http/HttpClient;"), "", true);
        fail += expect(jar(dir, "t-urlvalue", "Ljava/net/URL;getResource"), "", false);
        fail += expect(jar(dir, "t-urlopen", "Ljava/net/URL;openStream"), "", true);
        fail += expect(jar(dir, "t-dex2", null), "", true);  // only classes2.dex has the reference
        fail += expect(jar(dir, "hub-loot-lookup", "Lorg/jsoup/helper/HttpConnection;Ljava/net/HttpURLConnection;"), "", false);
        System.out.println(fail == 0 ? "self-test ok" : "self-test FAILED: " + fail);
        for (String a : args) {
            String r = NetScan.refusal(new File(a), "");
            System.out.println((r == null ? "load   " : "REFUSE ") + a + (r == null ? "" : "  " + r));
        }
        System.exit(fail == 0 ? 0 : 1);
    }

    private static int expect(File jar, String allow, boolean refused) {
        String r = NetScan.refusal(jar, allow);
        if ((r != null) == refused) return 0;
        System.out.println("  FAIL " + jar.getName() + " allow='" + allow + "': " + r);
        return 1;
    }

    private static File jar(File dir, String name, String strings) throws Exception {
        File f = new File(dir, name + ".jar");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            z.putNextEntry(new ZipEntry("classes.dex"));
            z.write(("dex\n035\0" + (strings == null ? "Lclean;" : strings)).getBytes("US-ASCII"));
            if (strings == null) {
                z.putNextEntry(new ZipEntry("classes2.dex"));
                z.write("dex\n035\0Ljavax/net/ssl/SSLSocketFactory;".getBytes("US-ASCII"));
            }
            z.putNextEntry(new ZipEntry("plugin-classes.txt"));
        }
        f.deleteOnExit();
        return f;
    }
}
