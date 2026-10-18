import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** stdin: lines "owner name desc" (javap InterfaceMethod/Method refs into net/runelite/api).
 *  args[0]: re/host/classes dir. Prints refs whose every host implementation is a *Stub/*Bag or missing. */
public class StubCheck {
    public static void main(String[] a) throws Exception {
        ClassLoader cl = StubCheck.class.getClassLoader();
        List<Class<?>> host = new ArrayList<>();
        Path root = Paths.get(a[0]);
        try (var s = Files.walk(root.resolve("net/runelite/host"))) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String f = root.relativize(p).toString();
                if (!f.endsWith(".class")) continue;
                try { host.add(Class.forName(f.replace('/', '.').replace(".class", ""), false, cl)); } catch (Throwable t) {}
            }
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        String line; Set<String> seen = new TreeSet<>();
        while ((line = r.readLine()) != null) {
            String[] p = line.trim().split(" ");
            if (p.length < 3 || !seen.add(line)) continue;
            Class<?> iface;
            try { iface = Class.forName(p[0].replace('/', '.'), false, cl); } catch (Throwable t) { continue; }
            if (!iface.isInterface()) continue;
            Method im = null;
            for (Method m : iface.getMethods()) if (m.getName().equals(p[1]) && desc(m).equals(p[2])) im = m;
            if (im == null || !Modifier.isAbstract(im.getModifiers())) continue;
            List<String> impls = new ArrayList<>(); boolean ok = false;
            for (Class<?> h : host) {
                if (!iface.isAssignableFrom(h) || h.isInterface() || Modifier.isAbstract(h.getModifiers())) continue;
                String own;
                try { own = h.getMethod(im.getName(), im.getParameterTypes()).getDeclaringClass().getSimpleName(); } catch (Throwable t) { own = "?"; }
                boolean stub = own.endsWith("Stub") || own.endsWith("Bag");
                if (!stub) ok = true;
                impls.add(h.getSimpleName() + (stub ? "(stub)" : ""));
            }
            if (!ok) System.out.println("GAP " + iface.getSimpleName() + "." + im.getName() + " impls=" + impls);
        }
    }
    static String desc(Method m) {
        StringBuilder b = new StringBuilder("(");
        for (Class<?> c : m.getParameterTypes()) b.append(d(c));
        return b.append(')').append(d(m.getReturnType())).toString();
    }
    static String d(Class<?> c) {
        if (c.isArray()) return "[" + d(c.getComponentType());
        if (c == int.class) return "I"; if (c == void.class) return "V"; if (c == boolean.class) return "Z";
        if (c == long.class) return "J"; if (c == byte.class) return "B"; if (c == short.class) return "S";
        if (c == char.class) return "C"; if (c == float.class) return "F"; if (c == double.class) return "D";
        return "L" + c.getName().replace('.', '/') + ";";
    }
}
