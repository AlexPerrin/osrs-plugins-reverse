package net.runelite.host.compat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Java 9+ library methods that Android 9 (API 28) lacks and d8 doesn't backport. tools/dex-plugin.sh
 * rewrites plugin call sites to these static versions (host/gen/CompatRewrite.java has the table).
 */
public final class JdkCompat {
    private JdkCompat() {}

    /** InputStream.readAllBytes() (Java 9, API 33). */
    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transferTo(in, out);
        return out.toByteArray();
    }

    /** InputStream.transferTo(OutputStream) (Java 9, API 33). */
    public static long transferTo(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        for (int n; (n = in.read(buf)) != -1; ) {
            out.write(buf, 0, n);
            total += n;
        }
        return total;
    }

    /**
     * Package.getImplementationVersion(): ART reports "0.0" for classes from a dex class loader,
     * where the JDK gives null without a manifest attribute (plugins then read their manifest).
     */
    public static String implementationVersion(Package p) {
        String v = p.getImplementationVersion();
        return "0.0".equals(v) ? null : v;
    }
}
