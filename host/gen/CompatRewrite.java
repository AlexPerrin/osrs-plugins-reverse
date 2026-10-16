import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Rewrites calls to Java 9+ library methods that Android 9 lacks (and d8 doesn't backport) into
 * static calls to net.runelite.host.compat.JdkCompat, before d8 (tools/dex-plugin.sh).
 * usage: java -cp asm-9.7.jar CompatRewrite.java in.jar out.jar
 */
public class CompatRewrite {
    // owner.name+desc of an instance method -> JdkCompat static method (receiver becomes arg 0)
    static final Map<String, String> TABLE = Map.of(
        "java/io/InputStream.readAllBytes()[B", "readAllBytes",
        "java/io/InputStream.transferTo(Ljava/io/OutputStream;)J", "transferTo",
        "java/lang/Package.getImplementationVersion()Ljava/lang/String;", "implementationVersion");
    static final String COMPAT = "net/runelite/host/compat/JdkCompat";
    static int rewritten;

    public static void main(String[] a) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(a[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                byte[] b = in.readAllBytes();
                if (e.getName().endsWith(".class")) b = rewrite(b);
                out.putNextEntry(new ZipEntry(e.getName()));
                out.write(b);
                out.closeEntry();
            }
        }
        System.out.println("compat rewrites: " + rewritten);
    }

    static byte[] rewrite(byte[] cls) {
        ClassReader r = new ClassReader(cls);
        ClassWriter w = new ClassWriter(0);
        r.accept(new ClassVisitor(Opcodes.ASM9, w) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, name, desc, sig, ex)) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        String target = op == Opcodes.INVOKEVIRTUAL ? TABLE.get(owner + "." + n + d) : null;
                        if (target != null) {
                            rewritten++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, target, "(L" + owner + ";" + d.substring(1), false);
                        } else {
                            super.visitMethodInsn(op, owner, n, d, itf);
                        }
                    }
                };
            }
        }, 0);
        return w.toByteArray();
    }
}
