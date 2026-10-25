import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import org.objectweb.asm.*;

/**
 * Lists the host methods that reach the game's native hooks: every method of a class under
 * net/runelite/host that calls net.runelite.host.Native, directly or through host methods it calls
 * (static, special and virtual calls on host classes; interface calls are not followed). One line
 * per method, "class<TAB>name<TAB>descriptor". tools/api-probe.sh's probe tags these rows.
 *   java -cp asm.jar NativeCalls.java <classes dir>
 */
public class NativeCalls {
    static final String HOST = "net/runelite/host/";

    public static void main(String[] args) throws Exception {
        Map<String, Set<String>> calls = new HashMap<>();  // "owner.name desc" -> host callees
        Set<String> reach = new HashSet<>();
        List<Path> files;
        try (Stream<Path> s = Files.walk(Paths.get(args[0], HOST))) {
            files = s.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        }
        for (Path f : files) {
            new ClassReader(Files.readAllBytes(f)).accept(new ClassVisitor(Opcodes.ASM9) {
                String owner;
                @Override public void visit(int v, int a, String name, String sig, String sup, String[] ifs) { owner = name; }
                @Override public MethodVisitor visitMethod(int a, String name, String desc, String sig, String[] ex) {
                    String self = owner + "." + name + " " + desc;
                    Set<String> out = calls.computeIfAbsent(self, k -> new HashSet<>());
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int op, String o, String n, String d, boolean itf) {
                            if (o.equals(HOST + "Native")) reach.add(self);
                            else if (!itf && o.startsWith(HOST)) out.add(o + "." + n + " " + d);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
        }
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Map.Entry<String, Set<String>> e : calls.entrySet())
                if (!reach.contains(e.getKey()) && e.getValue().stream().anyMatch(reach::contains)) grew = reach.add(e.getKey());
        }
        reach.stream().filter(m -> !m.startsWith(HOST + "Native.")).sorted().forEach(m -> {
            int dot = m.lastIndexOf('.', m.indexOf(' ')), sp = m.indexOf(' ');
            System.out.println(m.substring(0, dot).replace('/', '.') + "\t" + m.substring(dot + 1, sp) + "\t" + m.substring(sp + 1));
        });
    }
}
