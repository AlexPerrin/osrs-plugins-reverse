import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prints the probe's denominator: every abstract method declared on a net.runelite.api interface
 * in the given runelite-api jar, one per line as "interface<TAB>name<TAB>descriptor", sorted.
 *   java -cp <api jar> ApiList.java <api jar>
 */
public class ApiList {
    public static void main(String[] args) throws Exception {
        List<String> rows = new ArrayList<>();
        try (ZipFile zip = new ZipFile(args[0])) {
            for (ZipEntry e : Collections.list(zip.entries())) {
                String n = e.getName();
                if (!n.startsWith("net/runelite/api/") || !n.endsWith(".class")) continue;
                Class<?> c = Class.forName(n.substring(0, n.length() - 6).replace('/', '.'), false, ApiList.class.getClassLoader());
                if (!c.isInterface() || c.isAnnotation()) continue;
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isAbstract(m.getModifiers()) && !m.isSynthetic())
                        rows.add(c.getName() + "\t" + m.getName() + "\t" + descriptor(m));
                }
            }
        }
        Collections.sort(rows);
        for (String r : rows) System.out.println(r);
    }

    static String descriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(desc(p));
        return sb.append(')').append(desc(m.getReturnType())).toString();
    }

    static String desc(Class<?> c) {
        if (c.isArray()) return "[" + desc(c.getComponentType());
        if (!c.isPrimitive()) return "L" + c.getName().replace('.', '/') + ";";
        return String.valueOf("VZBCSIJFD".charAt(
            java.util.Arrays.asList(void.class, boolean.class, byte.class, char.class, short.class, int.class, long.class, float.class, double.class).indexOf(c)));
    }
}
