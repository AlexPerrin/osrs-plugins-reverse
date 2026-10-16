package net.runelite.host.compat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

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

    // java.time.Duration (Java 9, API 31).
    public static long toSeconds(Duration d) { return d.getSeconds(); }
    public static long toDaysPart(Duration d) { return d.getSeconds() / 86400; }
    public static int toHoursPart(Duration d) { return (int) (d.toHours() % 24); }
    public static int toMinutesPart(Duration d) { return (int) (d.toMinutes() % 60); }
    public static int toSecondsPart(Duration d) { return (int) (d.getSeconds() % 60); }
    public static int toMillisPart(Duration d) { return d.getNano() / 1_000_000; }

    public static long dividedBy(Duration d, Duration divisor) {
        return java.math.BigDecimal.valueOf(d.getSeconds()).movePointRight(9).add(java.math.BigDecimal.valueOf(d.getNano()))
            .divideToIntegralValue(java.math.BigDecimal.valueOf(divisor.getSeconds()).movePointRight(9)
                .add(java.math.BigDecimal.valueOf(divisor.getNano()))).longValueExact();
    }
}
