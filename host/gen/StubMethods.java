import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Replaces the bodies of the named methods in a jar with `return` (void) or
 * `throw new UnsupportedOperationException` (others), e.g. to take a plugin's self-updater out of
 * our build (host/plugins/devtools/build.sh). Fails if a named method isn't found.
 * usage: java -cp asm-9.7.jar StubMethods.java in.jar out.jar owner/Class.method ...
 */
public class StubMethods {
    public static void main(String[] a) throws Exception {
        Set<String> wanted = new HashSet<>(Arrays.asList(a).subList(2, a.length));
        Set<String> done = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(a[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                byte[] b = in.readAllBytes();
                if (e.getName().endsWith(".class")) b = stub(b, wanted, done);
                out.putNextEntry(new ZipEntry(e.getName()));
                out.write(b);
                out.closeEntry();
            }
        }
        wanted.removeAll(done);
        if (!wanted.isEmpty()) throw new IllegalStateException("methods not found: " + wanted);
        System.out.println("stubbed: " + done);
    }

    static byte[] stub(byte[] cls, Set<String> wanted, Set<String> done) {
        ClassReader r = new ClassReader(cls);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner = r.getClassName();
        r.accept(new ClassVisitor(Opcodes.ASM9, w) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (!wanted.contains(owner + "." + name) || (acc & Opcodes.ACC_ABSTRACT) != 0) return mv;
                done.add(owner + "." + name);
                mv.visitCode();
                if (Type.getReturnType(desc) == Type.VOID_TYPE) {
                    mv.visitInsn(Opcodes.RETURN);
                } else {
                    mv.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitLdcInsn("disabled in the Android host build");
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
                    mv.visitInsn(Opcodes.ATHROW);
                }
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                return null;  // drop the original body
            }
        }, 0);
        return w.toByteArray();
    }
}
