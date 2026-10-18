import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Generates a headless stand-in for the java.awt / javax.swing classes that RuneLite and its
 * plugins reference but Android lacks: same class hierarchy as the JDK, referenced members
 * only, methods do nothing and return defaults, constants keep the JDK's values.
 *
 *   java GenHeadless.java <refs.txt> <classes.txt> <handwritten shim dir> <out dir> <android.jar>
 *
 * refs.txt: "owner name descriptor" per line (JVM internal names); classes.txt: one class per line.
 * Classes that exist in the hand-written shim are never generated.
 */
public class GenHeadless {
    static Path shimDir;
    static final Set<String> androidClasses = new HashSet<>();

    /** True if the class exists on Android (android.jar) or will exist (shim / generated). */
    static boolean available(Class<?> c) {
        while (c.isArray()) c = c.getComponentType();
        if (c.isPrimitive()) return true;
        if (candidate(c)) return handwritten(c) || generate.contains(c) || want(c);
        return androidClasses.contains(c.getName().replace('.', '/'));
    }
    static final Map<Class<?>, Set<Member>> members = new LinkedHashMap<>();
    static final Set<Class<?>> generate = new LinkedHashSet<>();

    public static void main(String[] a) throws Exception {
        shimDir = Paths.get(a[2]);
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(a[4])) {
            z.stream().map(e -> e.getName()).filter(n -> n.endsWith(".class")).forEach(n -> androidClasses.add(n.substring(0, n.length() - 6)));
        }
        Path out = Paths.get(a[3]);
        for (String line : Files.readAllLines(Paths.get(a[1]))) if (!line.isBlank()) want(load(line.trim()));
        // Default implementations that returnValue() hands out for interface-typed results.
        for (String k : new String[] {"javax/swing/text/PlainDocument", "javax/swing/DefaultListModel", "javax/swing/DefaultButtonModel",
                "javax/swing/text/DefaultCaret", "javax/swing/DefaultListSelectionModel"}) want(load(k));
        // JTextPane's document (getStyledDocument) when a plugin uses text panes (Slayer Assistant's TextTab).
        if (generate.contains(javax.swing.JTextPane.class)) want(load("javax/swing/text/DefaultStyledDocument"));
        for (String line : Files.readAllLines(Paths.get(a[0]))) {
            String[] p = line.trim().split(" ");
            if (p.length != 3) continue;
            Class<?> owner = load(p[0]);
            if (owner == null) continue;
            Member m = resolve(owner, p[1].replace("\"", ""), p[2]);
            if (m == null) { System.err.println("unresolved " + line); continue; }
            Class<?> decl = m.getDeclaringClass();
            if (!want(decl)) continue;
            if (!types(m).stream().allMatch(GenHeadless::available)) { System.err.println("skipped (types) " + line); continue; }
            members.computeIfAbsent(decl, k -> new LinkedHashSet<>()).add(m);
            for (Class<?> t : types(m)) want(t);
        }
        // Property partners (setX <-> getX/isX) and listener trios, so state round-trips.
        for (Class<?> c : new ArrayList<>(members.keySet())) {
            for (Member m : new ArrayList<>(members.get(c))) {
                if (!(m instanceof Method)) continue;
                for (Method partner : partners(c, (Method) m)) {
                    if (types(partner).stream().allMatch(GenHeadless::available) && members.get(c).add(partner)) {
                        for (Class<?> t : types(partner)) want(t);
                    }
                }
            }
        }
        // A property accessor whose other half lives in a superclass (JComponent.setVisible vs
        // Component.isVisible) delegates to it, so pull the superclass's pair in too.
        for (Class<?> c : new ArrayList<>(members.keySet())) {
            for (Member m : new ArrayList<>(members.get(c))) {
                if (!(m instanceof Method) || prop((Method) m) == null || !partners(c, (Method) m).isEmpty()) continue;
                Method sup = superAccessor(c, (Method) m);
                if (sup == null) continue;
                Class<?> sc = sup.getDeclaringClass();
                if (members.computeIfAbsent(sc, k -> new LinkedHashSet<>()).add(sup)) for (Class<?> t : types(sup)) want(t);
                for (Method partner : partners(sc, sup))
                    if (types(partner).stream().allMatch(GenHeadless::available) && members.get(sc).add(partner))
                        for (Class<?> t : types(partner)) want(t);
            }
        }
        // Concrete classes must implement abstract methods of their generated supertypes.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Class<?> c : new ArrayList<>(generate)) {
                for (Method m : abstractToImplement(c)) {
                    // A JDK implementation in a generated superclass belongs there (PlainDocument must
                    // inherit AbstractDocument.addDocumentListener, not shadow it with a no-op).
                    Class<?> d = m.getDeclaringClass();
                    Class<?> target = d != c && !d.isInterface() && generate.contains(d) ? d : c;
                    if (members.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(m)) {
                        changed = true;
                        for (Class<?> t : types(m)) want(t);
                    }
                }
                if (c.isInterface()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()
                                && members.computeIfAbsent(c, k -> new LinkedHashSet<>()).add(m)) {
                            changed = true;
                            for (Class<?> t : types(m)) want(t);
                        }
                    }
                }
            }
        }
        int n = 0;
        for (Class<?> c : generate) {
            if (c.getEnclosingClass() != null) continue;  // emitted inside the top-level class
            Path f = out.resolve(c.getName().replace('.', '/') + ".java");
            Files.createDirectories(f.getParent());
            Files.writeString(f, source(c));
            n++;
        }
        System.err.println("generated " + n + " files, " + generate.size() + " classes");
    }

    static Class<?> load(String internal) {
        try {
            return Class.forName(internal.replace('/', '.'), false, GenHeadless.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean candidate(Class<?> c) {
        if (c == null || c.isArray() || c.isPrimitive()) return false;
        String n = c.getName();
        return n.startsWith("java.awt.") || n.startsWith("javax.swing.") || n.startsWith("javax.accessibility.");
    }

    static boolean handwritten(Class<?> c) {
        Class<?> top = c;
        while (top.getEnclosingClass() != null) top = top.getEnclosingClass();
        return Files.exists(shimDir.resolve(top.getName().replace('.', '/') + ".java"));
    }

    /** Adds c (and its enclosing classes and supertypes) to the generated set; false if not ours. */
    static boolean want(Class<?> c) {
        while (c != null && c.isArray()) c = c.getComponentType();
        if (!candidate(c) || handwritten(c) || !Modifier.isPublic(c.getModifiers()) && c.getEnclosingClass() == null) return false;
        if (!generate.add(c)) return true;
        if (c.getEnclosingClass() != null) want(c.getEnclosingClass());
        want(c.getSuperclass());
        for (Class<?> i : c.getInterfaces()) want(i);
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (k.getParameterCount() == 0) members.computeIfAbsent(c, x -> new LinkedHashSet<>()).add(k);
        }
        return true;
    }

    static String prop(Method m) {
        String n = m.getName();
        if (n.startsWith("set") && m.getParameterCount() == 1 && m.getReturnType() == void.class) return n.substring(3);
        if (n.startsWith("get") && m.getParameterCount() == 0 && m.getReturnType() != void.class) return n.substring(3);
        if (n.startsWith("is") && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) return n.substring(2);
        return null;
    }

    /** The same accessor in the nearest generated superclass that also declares its partner. */
    static Method superAccessor(Class<?> c, Method m) {
        for (Class<?> s = c.getSuperclass(); s != null && candidate(s) && !handwritten(s); s = s.getSuperclass()) {
            try {
                Method x = s.getDeclaredMethod(m.getName(), m.getParameterTypes());
                if (Modifier.isPublic(x.getModifiers()) && !Modifier.isAbstract(x.getModifiers()) && !partners(s, x).isEmpty()) return x;
            } catch (NoSuchMethodException e) { /* keep looking */ }
        }
        return null;
    }

    static String listenerKind(Method m) {
        String n = m.getName();
        if (!n.endsWith("Listener") && !n.endsWith("Listeners")) return null;
        if ((n.startsWith("add") || n.startsWith("remove")) && m.getParameterCount() == 1 && n.endsWith("Listener"))
            return n.substring(n.startsWith("add") ? 3 : 6);
        if (n.startsWith("get") && n.endsWith("Listeners") && m.getParameterCount() == 0 && m.getReturnType().isArray())
            return n.substring(3, n.length() - 1);
        return null;
    }

    /** The other half of a property (setter/getter) or listener trio, declared in the same class. */
    static List<Method> partners(Class<?> c, Method m) {
        List<Method> out = new ArrayList<>();
        String p = prop(m), l = listenerKind(m);
        for (Method x : c.getDeclaredMethods()) {
            if (x.equals(m) || x.isBridge() || x.isSynthetic() || !Modifier.isPublic(x.getModifiers()) || Modifier.isStatic(x.getModifiers())) continue;
            if (p != null && p.equals(prop(x))) out.add(x);
            if (l != null && l.equals(listenerKind(x))) out.add(x);
        }
        return out;
    }

    static List<Class<?>> types(Member m) {
        List<Class<?>> t = new ArrayList<>();
        if (m instanceof Method) { t.add(((Method) m).getReturnType()); t.addAll(Arrays.asList(((Method) m).getParameterTypes())); }
        if (m instanceof Constructor) t.addAll(Arrays.asList(((Constructor<?>) m).getParameterTypes()));
        if (m instanceof Field) t.add(((Field) m).getType());
        return t;
    }

    static Member resolve(Class<?> owner, String name, String desc) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            Member m = find(c, name, desc);
            if (m != null) return m;
            for (Class<?> i : allInterfaces(c)) { m = find(i, name, desc); if (m != null) return m; }
        }
        return null;
    }

    static Set<Class<?>> allInterfaces(Class<?> c) {
        Set<Class<?>> s = new LinkedHashSet<>();
        Deque<Class<?>> q = new ArrayDeque<>(Arrays.asList(c.getInterfaces()));
        while (!q.isEmpty()) { Class<?> i = q.poll(); if (s.add(i)) q.addAll(Arrays.asList(i.getInterfaces())); }
        return s;
    }

    static Member find(Class<?> c, String name, String desc) {
        if (name.equals("<init>")) {
            for (Constructor<?> k : c.getDeclaredConstructors()) if (desc(k.getParameterTypes(), void.class).equals(desc)) return k;
            return null;
        }
        if (!desc.startsWith("(")) {
            for (Field f : c.getDeclaredFields()) if (f.getName().equals(name) && descOf(f.getType()).equals(desc)) return f;
            return null;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && desc(m.getParameterTypes(), m.getReturnType()).equals(desc)) {
                if (!m.isBridge()) return m;
                // A bridge's descriptor is what callers link against; emit the real method instead,
                // whose covariant return still satisfies that descriptor at runtime only if emitted
                // as well, so keep the bridge's erased signature.
                return m;
            }
        }
        return null;
    }

    static String desc(Class<?>[] params, Class<?> ret) {
        return "(" + Arrays.stream(params).map(GenHeadless::descOf).collect(Collectors.joining()) + ")" + descOf(ret);
    }

    static String descOf(Class<?> t) {
        if (t == void.class) return "V";
        if (t.isPrimitive()) return Map.of(int.class, "I", long.class, "J", boolean.class, "Z", byte.class, "B",
                char.class, "C", short.class, "S", float.class, "F", double.class, "D").get(t);
        if (t.isArray()) return "[" + descOf(t.getComponentType());
        return "L" + t.getName().replace('.', '/') + ";";
    }

    /** Abstract methods a concrete generated class inherits from generated supertypes. */
    static List<Method> abstractToImplement(Class<?> c) {
        if (c.isInterface()) return List.of();
        if (Modifier.isAbstract(c.getModifiers())) {
            // Abstract classes: emit the interface methods the JDK class itself implements (MouseAdapter etc.).
            List<Method> out = new ArrayList<>();
            for (Class<?> i : allInterfaces(c)) {
                if (!generate.contains(i) && !(handwritten(i) || !candidate(i) && available(i))) continue;
                for (Method m : i.getDeclaredMethods()) {
                    if (!Modifier.isAbstract(m.getModifiers())) continue;
                    try {
                        Method x = c.getDeclaredMethod(m.getName(), m.getParameterTypes());
                        if (!Modifier.isAbstract(x.getModifiers())) out.add(x);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
            return out;
        }
        List<Method> out = new ArrayList<>();
        Set<String> have = new HashSet<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) if (!Modifier.isAbstract(m.getModifiers())) have.add(m.getName() + desc(m.getParameterTypes(), m.getReturnType()));
        }
        List<Class<?>> supers = new ArrayList<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) { supers.add(k); supers.addAll(allInterfaces(k)); }
        for (Class<?> s : supers) {
            if (!generate.contains(s) && !(s.isInterface() && (handwritten(s) || !candidate(s) && available(s)))) continue;
            for (Method m : s.getDeclaredMethods()) {
                if (!Modifier.isAbstract(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
                // Implemented somewhere in the JDK chain: generate that implementation in c.
                Method impl = null;
                for (Class<?> k = c; k != null && impl == null; k = k.getSuperclass()) {
                    try {
                        Method x = k.getDeclaredMethod(m.getName(), m.getParameterTypes());
                        if (!Modifier.isAbstract(x.getModifiers())) impl = x;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                Method pick = impl != null && generate.contains(impl.getDeclaringClass()) ? impl : impl == null ? m : null;
                if (pick == null) continue;
                if (!generate.contains(pick.getDeclaringClass()) && pick.getDeclaringClass() != c) {
                    // Implement in c itself (the declaring class isn't generated).
                    out.add(m);
                } else {
                    out.add(pick);
                }
            }
        }
        return out;
    }

    // --- source emission ---

    static String typeName(Class<?> t) {
        if (t.isArray()) return typeName(t.getComponentType()) + "[]";
        return t.getName().replace('$', '.');
    }

    static String defaultValue(Class<?> t) {
        if (t == void.class) return null;
        if (t == boolean.class) return "false";
        if (t == char.class) return "(char) 0";
        if (t.isPrimitive()) return "(" + t.getName() + ") 0";
        if (t == String.class) return "\"\"";
        return "null";
    }

    /** Non-null stand-ins for method results, so headless UI code doesn't trip over nulls. */
    static String returnValue(Class<?> t) {
        String d = defaultValue(t);
        if (!"null".equals(d)) return d;
        if (t.isArray()) {
            Class<?> base = t;
            int dims = 0;
            while (base.isArray()) { base = base.getComponentType(); dims++; }
            return "new " + typeName(base) + "[0]" + "[]".repeat(dims - 1);
        }
        switch (t.getName()) {
            case "java.awt.Dimension": return "new java.awt.Dimension()";
            case "java.awt.Point": return "new java.awt.Point()";
            case "java.awt.Rectangle": return "new java.awt.Rectangle()";
            case "java.awt.Insets": return "new java.awt.Insets(0, 0, 0, 0)";
            case "java.awt.Font": return "new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12)";
            case "java.awt.Color": return "java.awt.Color.BLACK";
            case "java.util.List": case "java.util.Collection": return "new java.util.ArrayList()";
            case "java.util.Set": return "new java.util.HashSet()";
            case "java.util.Map": return "new java.util.HashMap()";
        }
        Map<String, String> impl = Map.of("javax.swing.text.Document", "javax.swing.text.PlainDocument",
                "javax.swing.ListModel", "javax.swing.DefaultListModel", "javax.swing.ButtonModel", "javax.swing.DefaultButtonModel",
                "javax.swing.text.Caret", "javax.swing.text.DefaultCaret", "javax.swing.ListSelectionModel", "javax.swing.DefaultListSelectionModel");
        if (impl.containsKey(t.getName())) {
            Class<?> k = load(impl.get(t.getName()).replace('.', '/'));
            if (k != null && generate.contains(k)) return "new " + typeName(k) + "()";
        }
        if (generate.contains(t) && !t.isInterface() && !t.isEnum() && !Modifier.isAbstract(t.getModifiers())
                && t.getEnclosingClass() == null) {
            return "new " + typeName(t) + "()";
        }
        return "null";
    }

    static String source(Class<?> c) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by host/gen/GenHeadless.java: headless stand-in for ").append(c.getName()).append(". Do not edit.\n");
        sb.append("package ").append(c.getPackageName()).append(";\n\n");
        sb.append("@SuppressWarnings({\"unchecked\", \"rawtypes\", \"deprecation\"})\n");
        emit(sb, c, "");
        return sb.toString();
    }

    static void emit(StringBuilder sb, Class<?> c, String ind) {
        int mod = c.getModifiers();
        sb.append(ind).append("public ");
        if (c.getEnclosingClass() != null && Modifier.isStatic(mod)) sb.append("static ");
        boolean iface = c.isInterface();
        if (c.isEnum()) {
            sb.append("enum ").append(c.getSimpleName()).append(" {\n").append(ind).append("    ");
            sb.append(Arrays.stream(c.getEnumConstants()).map(e -> ((Enum<?>) e).name()).collect(Collectors.joining(", "))).append(";\n");
            sb.append(ind).append("}\n");
            return;
        }
        if (!iface && Modifier.isAbstract(mod)) sb.append("abstract ");
        sb.append(iface ? "interface " : "class ").append(c.getSimpleName());
        Class<?> sup = c.getSuperclass();
        if (!iface && sup != null && sup != Object.class) {
            if (candidate(sup) && !generate.contains(sup) && !handwritten(sup)) sup = Object.class;
            if (sup != Object.class) sb.append(" extends ").append(typeName(sup));
        }
        List<String> ifs = new ArrayList<>();
        for (Class<?> i : c.getInterfaces()) {
            if (candidate(i) && !generate.contains(i) && !handwritten(i)) continue;
            if (!Modifier.isPublic(i.getModifiers()) || !available(i)) continue;
            ifs.add(typeName(i));
        }
        if (!ifs.isEmpty()) sb.append(iface ? " extends " : " implements ").append(String.join(", ", ifs));
        sb.append(" {\n");
        String in = ind + "    ";
        Set<Member> ms = members.getOrDefault(c, Set.of());
        boolean hasCtor = false;
        for (Member m : ms) {
            if (m instanceof Field) {
                Field f = (Field) m;
                int fm = f.getModifiers();
                sb.append(in).append("public ");
                if (Modifier.isStatic(fm)) sb.append("static ");
                Object v = null;
                if (Modifier.isStatic(fm) && Modifier.isFinal(fm) && (f.getType().isPrimitive() || f.getType() == String.class)) {
                    try { f.setAccessible(true); v = f.get(null); } catch (Throwable ignored) {}
                }
                if (v != null) sb.append("final ");
                sb.append(typeName(f.getType())).append(" ").append(f.getName());
                if (v != null) sb.append(" = ").append(literal(v, f.getType()));
                else if (!Modifier.isStatic(fm)) {
                    String init = instanceDefault(c, f);  // GridBagConstraints: gridx RELATIVE, insets (0,0,0,0)
                    if (init != null) sb.append(" = ").append(init);
                }
                sb.append(";\n");
            }
        }
        for (Member m : ms) {
            if (m instanceof Constructor) {
                if (iface) continue;
                Constructor<?> k = (Constructor<?>) m;
                if (k.getParameterCount() == 0) hasCtor = true;
                sb.append(in).append("public ").append(c.getSimpleName())
                  .append("(").append(params(k.getParameterTypes())).append(") {");
                sb.append(superCall(c));
                sb.append(ctorProperties(c, k.getParameterTypes()));
                if (c == javax.swing.GroupLayout.class && k.getParameterCount() == 1) sb.append(" __host = a0;");
                // Swing's JList(ListModel) stores the model directly (no setModel call, which subclasses
                // override with checks: Bank Memory's ItemList casts getModel() to its own model).
                if (c == javax.swing.JList.class && k.getParameterCount() == 1 && k.getParameterTypes()[0] == javax.swing.ListModel.class)
                    sb.append(" __Model = a0; __ModelSet = true;");
                if (c == javax.swing.JTable.class && k.getParameterCount() == 1 && k.getParameterTypes()[0] == javax.swing.table.TableModel.class
                        && generatedHas(c, "setModel", javax.swing.table.TableModel.class))
                    sb.append(" setModel(a0);");
                if (c == javax.swing.border.CompoundBorder.class && k.getParameterCount() == 2) sb.append(" __outside = a0; __inside = a1;");
                if (c == javax.swing.border.MatteBorder.class && k.getParameterCount() == 5 && k.getParameterTypes()[4] == java.awt.Color.class) sb.append(" __matte = a4;");
                sb.append(" }\n");
            }
        }
        if (!iface && !hasCtor) {
            sb.append(in).append("public ").append(c.getSimpleName()).append("() {");
            sb.append(superCall(c));
            sb.append(" }\n");
        }
        // Backing state for properties and listeners declared by this class.
        Map<String, Class<?>> props = new LinkedHashMap<>();
        Set<String> listenerKinds = new LinkedHashSet<>();
        if (!iface) {
            for (Member m : ms) {
                if (!(m instanceof Method) || Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() != c) continue;
                Method x = (Method) m;
                String l = listenerKind(x);
                if (l != null) { listenerKinds.add(l); continue; }
                String p = prop(x);
                if (p != null && x.getName().startsWith("set")) props.put(p, x.getParameterTypes()[0]);
            }
            for (Map.Entry<String, Class<?>> e : props.entrySet()) {
                sb.append(in).append("private ").append(typeName(e.getValue())).append(" __").append(e.getKey()).append(";\n");
                sb.append(in).append("private boolean __").append(e.getKey()).append("Set;\n");
            }
            for (String l : listenerKinds) sb.append(in).append("private final java.util.List __").append(l).append("s = new java.util.ArrayList();\n");
            if (c == java.awt.Container.class) sb.append(in).append("private final java.util.List __children = new java.util.ArrayList();\n");
            if (c == java.awt.CardLayout.class)
                sb.append(in).append("public final java.util.Map __cards = new java.util.HashMap();\n")
                  .append(in).append("public final java.util.Map __current = new java.util.WeakHashMap();\n")
                  .append(in).append("/** Host: the card show() selected in parent (default: the first child). */\n")
                  .append(in).append("public java.awt.Component __visibleCard(java.awt.Container p) { Object n = __current.get(p); Object v = n != null ? __cards.get(n) : null; if (v instanceof java.awt.Component) return (java.awt.Component) v; java.awt.Component[] cs = p.getComponents(); return cs.length > 0 ? cs[0] : null; }\n");
            if (c == javax.swing.SwingUtilities.class)
                sb.append(in).append("/** Runnables posted to the \"EDT\"; the host's EDT thread (Edt) runs them while the game thread waits. */\n")
                  .append(in).append("public static final java.util.concurrent.LinkedBlockingQueue<Runnable> __queue = new java.util.concurrent.LinkedBlockingQueue<Runnable>();\n")
                  .append(in).append("public static volatile Thread __edt;\n")
                  .append(in).append("/** Host: runs invokeAndWait's runnable (null: queued without waiting). */\n")
                  .append(in).append("public static volatile java.util.function.Consumer<Runnable> __invokeAndWait;\n");
            if (c == java.awt.Component.class) sb.append(in).append("java.awt.Container __parent;\n");
            // GroupLayout adds the components its groups name to the host container (Equipment Inspector).
            if (c == javax.swing.GroupLayout.class) sb.append(in).append("public java.awt.Container __host;\n");
            if (c == javax.swing.GroupLayout.Group.class) sb.append(in).append("public javax.swing.GroupLayout __layout;\n");
            if (c == javax.swing.text.AbstractDocument.class)
                sb.append(in).append("/** Host: the document's text (JTextComponent.get/setText read and write it, as in Swing). */\n")
                  .append(in).append("public final java.lang.StringBuilder __content = new java.lang.StringBuilder();\n");
            // Borders keep their parts (Inventory Setups reads CompoundBorder.getInsideBorder().getMatteColor()).
            if (c == javax.swing.border.CompoundBorder.class)
                sb.append(in).append("public javax.swing.border.Border __outside, __inside;\n");
            if (c == javax.swing.border.MatteBorder.class)
                sb.append(in).append("public java.awt.Color __matte;\n");
            if (c == java.awt.Toolkit.class)
                sb.append(in).append("/** Host: getDefaultToolkit's instance (RuneLite's SwingUtil.pumpPendingEvents peeks its event queue). */\n")
                  .append(in).append("public static final java.awt.Toolkit __default = new java.awt.Toolkit() {};\n");
            if (c == javax.swing.JOptionPane.class)
                sb.append(in).append("/** Host: answers the static show*Dialog calls (kind, {message, title, options, initial, optionType}); null = none. */\n")
                  .append(in).append("public static volatile java.util.function.BiFunction<String, Object[], Object> __dialog;\n");
            if (c == javax.swing.JPopupMenu.class)
                sb.append(in).append("/** Host: the popup a listener last show()ed (the sidebar lists its menu items in a picker). */\n")
                  .append(in).append("public static volatile javax.swing.JPopupMenu __shown;\n");
            if (c == javax.swing.JComboBox.class)
                sb.append(in).append("/** Host: the items (the sidebar lists them in a picker). */\n")
                  .append(in).append("public final java.util.List __items = new java.util.ArrayList();\n");
            if (c == java.awt.event.ItemEvent.class)
                sb.append(in).append("/** Host: set by the sidebar when it fires item events. */\n")
                  .append(in).append("public java.lang.Object __item;\n")
                  .append(in).append("public int __state;\n");
        }
        for (Member m : ms) {
            if (!(m instanceof Method)) continue;
            Method x = (Method) m;
            String special = iface ? null : Modifier.isStatic(x.getModifiers()) ? staticBody(c, x) : specialBody(c, x, props, listenerKinds);
            if (special != null) {
                sb.append(in).append(Modifier.isProtected(x.getModifiers()) ? "protected " : "public ")
                  .append(Modifier.isStatic(x.getModifiers()) ? "static " : "")
                  .append(typeName(x.getReturnType())).append(" ").append(x.getName()).append("(").append(params(x.getParameterTypes()))
                  .append(") { ").append(special).append(" }\n");
                continue;
            }
            int xm = x.getModifiers();
            Class<?> ret = x.getReturnType();
            sb.append(in);
            boolean isStatic = Modifier.isStatic(xm);
            if (iface) {
                if (isStatic) sb.append("static ");
                else if (!Modifier.isAbstract(xm)) sb.append("default ");
            } else {
                sb.append(Modifier.isProtected(xm) ? "protected " : "public ");
                if (isStatic) sb.append("static ");
                // Toolkit's abstract methods get default bodies so getDefaultToolkit can return an instance.
                if (Modifier.isAbstract(xm) && Modifier.isAbstract(c.getModifiers()) && c != java.awt.Toolkit.class) sb.append("abstract ");
            }
            sb.append(typeName(ret)).append(" ").append(x.getName()).append("(").append(params(x.getParameterTypes())).append(")");
            boolean body = iface ? (isStatic || !Modifier.isAbstract(xm)) : !(Modifier.isAbstract(xm) && Modifier.isAbstract(c.getModifiers()) && c != java.awt.Toolkit.class);
            if (!body) { sb.append(";\n"); continue; }
            String dv = ret == void.class ? null : returnValue(ret);
            // add(x) style methods hand their argument back, as Swing does.
            Class<?>[] ps = x.getParameterTypes();
            if (dv != null && ps.length >= 1 && ret.isAssignableFrom(ps[0]) && !ret.isPrimitive() && ret != String.class) dv = "a0";
            sb.append(dv == null ? " {}\n" : " { return " + dv + "; }\n");
        }
        for (Class<?> inner : c.getDeclaredClasses()) {
            if (generate.contains(inner)) { sb.append("\n"); emit(sb, inner, in); }
        }
        sb.append(ind).append("}\n");
    }

    /** Constructor arguments that are properties (text, icon, scroll pane view, selection) are stored. */
    static String ctorProperties(Class<?> c, Class<?>[] ps) {
        StringBuilder b = new StringBuilder();
        boolean textual = javax.swing.JLabel.class.isAssignableFrom(c) || javax.swing.AbstractButton.class.isAssignableFrom(c)
                || javax.swing.text.JTextComponent.class.isAssignableFrom(c);
        boolean textDone = false;
        for (int i = 0; i < ps.length; i++) {
            String a = "a" + i;
            if (textual && ps[i] == String.class && !textDone && generatedHas(c, "setText", String.class)) { b.append(" setText(").append(a).append(");"); textDone = true; }
            else if (ps[i] == java.awt.LayoutManager.class && generatedHas(c, "setLayout", java.awt.LayoutManager.class)) b.append(" setLayout(").append(a).append(");");
            else if (ps[i] == javax.swing.Icon.class && generatedHas(c, "setIcon", javax.swing.Icon.class)) b.append(" setIcon(").append(a).append(");");
            else if (c == javax.swing.JScrollPane.class && ps[i] == java.awt.Component.class && generatedHas(c, "setViewportView", java.awt.Component.class)) b.append(" setViewportView(").append(a).append(");");
            else if (javax.swing.AbstractButton.class.isAssignableFrom(c) && ps[i] == boolean.class && generatedHas(c, "setSelected", boolean.class)) b.append(" setSelected(").append(a).append(");");
            else if (c == javax.swing.ImageIcon.class && ps[i] == java.awt.Image.class && generatedHas(c, "setImage", java.awt.Image.class)) b.append(" setImage(").append(a).append(");");
            else if (javax.swing.JComboBox.class.isAssignableFrom(c) && ps[i] == Object[].class && generatedHas(c, "addItem", Object.class)) b.append(" if (").append(a).append(" != null) for (Object o : ").append(a).append(") addItem(o);");
            // JSpinner(SpinnerModel) keeps the model and starts at its value (Banked Experience sets it through getModel()).
            else if (ps[i] == javax.swing.SpinnerModel.class && generatedHas(c, "setModel", javax.swing.SpinnerModel.class)) {
                b.append(" setModel(").append(a).append(");");
                if (generatedHas(c, "setValue", Object.class)) b.append(" if (").append(a).append(" != null) setValue(").append(a).append(".getValue());");
            }
        }
        // SpinnerNumberModel(value, minimum, maximum, stepSize)
        if (c == javax.swing.SpinnerNumberModel.class && ps.length == 4 && generatedHas(c, "setValue", Object.class))
            b.append(" setValue(a0); setMinimum(a1); setMaximum(a2); setStepSize(a3);");
        return b.toString();
    }

    static boolean generatedHas(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null && generate.contains(k); k = k.getSuperclass()) {
            for (Member m : members.getOrDefault(k, Set.of()))
                if (m instanceof Method && m.getName().equals(name) && Arrays.equals(((Method) m).getParameterTypes(), params)) return true;
        }
        return false;
    }

    static String superCall(Class<?> c) {
        return superCall0(c) + ctorExtras(c);
    }

    // Swing installs UI-delegate listeners in constructors; code like IconTextField relies on them.
    static String ctorExtras(Class<?> c) {
        if (c.getName().equals("javax.swing.text.JTextComponent") && generate.contains(java.awt.event.MouseAdapter.class))
            return " addMouseListener(new java.awt.event.MouseAdapter() {});";
        return "";
    }

    static String superCall0(Class<?> c) {
        Class<?> s = c.getSuperclass();
        if (s == null || s == Object.class || c.isEnum()) return "";
        if (s == java.util.EventObject.class) return " super(new Object());";
        if (generate.contains(s)) return hasNoArgCtor(s) ? "" : " super(" + superArgs(s) + ");";
        if (candidate(s) && !handwritten(s)) return "";
        try { s.getDeclaredConstructor(); return ""; } catch (NoSuchMethodException e) { /* fall through */ }
        for (Constructor<?> k : s.getDeclaredConstructors()) {
            if (Modifier.isPrivate(k.getModifiers())) continue;
            return " super(" + Arrays.stream(k.getParameterTypes()).map(t -> t.isPrimitive() ? defaultValue(t) : "(" + typeName(t) + ") " + defaultValue(t)).collect(Collectors.joining(", ")) + ");";
        }
        return "";
    }

    /** Swing's event-dispatch entry points queue work for the host's EDT (net.runelite.host.Edt). */
    static String staticBody(Class<?> c, Method x) {
        String n = x.getName();
        Class<?>[] ps = x.getParameterTypes();
        String q = "javax.swing.SwingUtilities.__queue";
        if ((c == javax.swing.SwingUtilities.class || c == java.awt.EventQueue.class) && ps.length == 1 && ps[0] == Runnable.class) {
            if (n.equals("invokeLater")) return q + ".add(a0);";
            if (n.equals("invokeAndWait")) return "if (Thread.currentThread() == javax.swing.SwingUtilities.__edt) a0.run(); else if (javax.swing.SwingUtilities.__invokeAndWait != null) javax.swing.SwingUtilities.__invokeAndWait.accept(a0); else " + q + ".add(a0);";
        }
        if (c == java.awt.Toolkit.class && n.equals("getDefaultToolkit") && ps.length == 0) return "return __default;";
        if (c == javax.swing.BorderFactory.class && n.equals("createCompoundBorder") && ps.length == 2) return "return new javax.swing.border.CompoundBorder(a0, a1);";
        if (c == javax.swing.BorderFactory.class && n.equals("createMatteBorder") && ps.length == 5 && ps[4] == java.awt.Color.class) return "return new javax.swing.border.MatteBorder(a0, a1, a2, a3, a4);";
        if ((c == javax.swing.SwingUtilities.class || c == java.awt.EventQueue.class) && n.equals("isEventDispatchThread") && ps.length == 0)
            return "return Thread.currentThread() == javax.swing.SwingUtilities.__edt;";
        // JOptionPane dialogs go to the host (modal Android dialogs); without one: cancelled/closed.
        if (c == javax.swing.JOptionPane.class && n.startsWith("show") && n.endsWith("Dialog")) {
            String kind = n.equals("showInputDialog") ? (x.getReturnType() == String.class ? "input" : "inputSelect")
                : n.equals("showConfirmDialog") ? "confirm" : n.equals("showOptionDialog") ? "option"
                : n.equals("showMessageDialog") ? "message" : null;
            if (kind != null) {
                int msg = -1, title = -1, arr = -1, init = -1, type = -1;
                for (int i = 0; i < ps.length; i++) {
                    if (ps[i] == Object.class) { if (msg < 0) msg = i; else init = i; }
                    else if (ps[i] == String.class && title < 0) title = i;
                    else if (ps[i] == Object[].class) arr = i;
                    else if (ps[i] == int.class && type < 0 && title >= 0) type = i;
                }
                String a = "new Object[] {" + (msg >= 0 ? "a" + msg : "null") + ", " + (title >= 0 ? "a" + title : "null") + ", "
                    + (arr >= 0 ? "a" + arr : "null") + ", " + (init >= 0 ? "a" + init : "null") + ", "
                    + (type >= 0 && kind.equals("confirm") ? "a" + type : "-1") + "}";
                String call = "Object r = __dialog != null ? __dialog.apply(\"" + kind + "\", " + a + ") : null; ";
                Class<?> rt = x.getReturnType();
                if (rt == void.class) return call;
                if (rt == int.class) return call + "return r instanceof Integer ? (Integer) r : -1;";
                return call + "return (" + rt.getName() + ") r;";
            }
        }
        // As the JDK: the event's button (press/release/click) or its extended modifiers (drags).
        if (c == javax.swing.SwingUtilities.class && ps.length == 1 && ps[0] == java.awt.event.MouseEvent.class) {
            int b = n.equals("isLeftMouseButton") ? 1 : n.equals("isMiddleMouseButton") ? 2 : n.equals("isRightMouseButton") ? 3 : 0;
            int mask = b == 1 ? 1024 : b == 2 ? 2048 : 4096;
            if (b != 0) return "return a0.getButton() == " + b + " || (a0.getModifiersEx() & " + mask + ") != 0;";
        }
        return null;
    }

    /** Container.add moves a component that already has a parent (desktop's add(errorPanel) twice keeps one child). */
    static final String REPARENT0 = "if (a0.__parent != null) a0.__parent.remove(a0); ";
    static final String REPARENT1 = "if (a1.__parent != null) a1.__parent.remove(a1); ";

    /** Stateful bodies for properties, listeners and Container children; null = default. */
    static String specialBody(Class<?> c, Method x, Map<String, Class<?>> props, Set<String> listenerKinds) {
        String n = x.getName();
        Class<?>[] ps = x.getParameterTypes();
        // Plugins set accessible names on their panels (Hunter Rumours); never null, state not kept.
        if (n.equals("getAccessibleContext") && ps.length == 0 && x.getReturnType() == javax.accessibility.AccessibleContext.class)
            return "return new javax.accessibility.AccessibleContext() {};";
        if (c == java.awt.CardLayout.class) {
            if (n.equals("show") && ps.length == 2) return "__current.put(a0, a1);";
            if (n.equals("addLayoutComponent") && ps.length == 2 && ps[0] == String.class) return "__cards.put(a0, a1);";
            if (n.equals("addLayoutComponent") && ps.length == 2 && ps[1] == Object.class) return "if (a1 instanceof String) __cards.put(a1, a0);";
        }
        // JTable: a new table has an empty DefaultTableModel; the column model is built from the
        // model (createDefaultColumnsFromModel) and rebuilt after setModel (Slayer Assistant's TableTab).
        if (c == javax.swing.JTable.class && props.containsKey("Model")) {
            if (n.equals("getModel") && ps.length == 0)
                return "if (!__ModelSet) { __Model = new javax.swing.table.DefaultTableModel(); __ModelSet = true; } return __Model;";
            if (n.equals("setModel") && ps.length == 1)
                return "__Model = a0; __ModelSet = true;" + (props.containsKey("ColumnModel") ? " __ColumnModel = null; __ColumnModelSet = false;" : "");
            if (n.equals("getColumnModel") && ps.length == 0 && props.containsKey("ColumnModel"))
                return "if (!__ColumnModelSet) { __ColumnModel = javax.swing.table.DefaultTableColumnModel.__fromModel(getModel()); __ColumnModelSet = true; } return __ColumnModel;";
        }
        // JList.setListData: a read-only model over a copy of the data (Slayer Assistant's task list).
        if (c == javax.swing.JList.class && n.equals("setListData") && ps.length == 1 && generatedHas(c, "setModel", javax.swing.ListModel.class)) {
            String data = ps[0] == Object[].class ? "a0 == null ? new Object[0] : a0.clone()" : "a0 == null ? new Object[0] : a0.toArray()";
            return "final Object[] d = " + data + "; setModel(new javax.swing.ListModel() {"
                    + " public int getSize() { return d.length; } public Object getElementAt(int i) { return d[i]; }"
                    + " public void addListDataListener(javax.swing.event.ListDataListener l) {} public void removeListDataListener(javax.swing.event.ListDataListener l) {} });";
        }
        if (c == javax.swing.JPopupMenu.class && n.equals("show") && ps.length == 3) return "__shown = this;";
        // JList selection (the sidebar lists rows and selects one on a tap): -1 when nothing is
        // selected, values come from the model, setSelectedIndex notifies the selection listeners.
        // The headless list has no geometry; a tap selects its row before the list's mouse listeners
        // run, so locationToIndex maps any point to the selected row.
        if (c == javax.swing.JList.class && props.containsKey("SelectedIndex")) {
            if (n.equals("getSelectedIndex") && ps.length == 0) return "return __SelectedIndexSet ? __SelectedIndex : -1;";
            if (n.equals("clearSelection") && ps.length == 0) return "__SelectedIndex = -1; __SelectedIndexSet = true;";
            if (n.equals("locationToIndex") && ps.length == 1) return "return getSelectedIndex();";
            if (n.equals("getSelectedValue") && ps.length == 0)
                return "int i = getSelectedIndex(); javax.swing.ListModel m = getModel(); return i >= 0 && i < m.getSize() ? m.getElementAt(i) : null;";
            if (n.equals("getSelectedValuesList") && ps.length == 0)
                return "java.util.List l = new java.util.ArrayList(); Object v = getSelectedValue(); if (v != null) l.add(v); return l;";
            if (n.equals("setSelectedIndex") && ps.length == 1 && listenerKinds.contains("ListSelectionListener"))
                return "__SelectedIndex = a0; __SelectedIndexSet = true; for (Object l : __ListSelectionListeners.toArray()) ((javax.swing.event.ListSelectionListener) l).valueChanged(new javax.swing.event.ListSelectionEvent());";
        }
        if (c == javax.swing.border.CompoundBorder.class && n.equals("getInsideBorder") && ps.length == 0) return "return __inside;";
        if (c == javax.swing.border.CompoundBorder.class && n.equals("getOutsideBorder") && ps.length == 0) return "return __outside;";
        if (c == javax.swing.border.MatteBorder.class && n.equals("getMatteColor") && ps.length == 0) return "return __matte != null ? __matte : java.awt.Color.BLACK;";
        // Component.getFontMetrics: the shim's Paint-measured metrics (Loot Lookup's Util.truncateToFit
        // measures labels); a null font measures the default one.
        if (java.awt.Component.class.isAssignableFrom(c) && n.equals("getFontMetrics") && ps.length == 1 && ps[0] == java.awt.Font.class)
            return "return java.awt.FontMetrics.of(a0 != null ? a0 : new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12));";
        if (c == javax.swing.JComboBox.class) {
            if (n.equals("addItem") && ps.length == 1) return "__items.add(a0); if (getSelectedItem() == null) setSelectedItem(a0);";
            if (n.equals("removeAllItems") && ps.length == 0) return "__items.clear();";
            if (n.equals("getItemCount") && ps.length == 0) return "return __items.size();";
            if (n.equals("getItemAt") && ps.length == 1) return "return a0 >= 0 && a0 < __items.size() ? __items.get(a0) : null;";
            if (n.equals("getSelectedIndex") && ps.length == 0) return "return __items.indexOf(getSelectedItem());";
        }
        // Skill Calculator casts the editor to DefaultEditor for its text field.
        if (c == javax.swing.JSpinner.class && n.equals("getEditor") && ps.length == 0 && props.containsKey("Editor"))
            return "if (!__EditorSet) { __Editor = new javax.swing.JSpinner.DefaultEditor(); __EditorSet = true; } return (javax.swing.JComponent) __Editor;";
        // Documents hold their text: Notes saves Document.getText(0, getLength()) on focus loss.
        if (javax.swing.text.AbstractDocument.class.isAssignableFrom(c) && generate.contains(javax.swing.text.AbstractDocument.class)) {
            boolean base = c == javax.swing.text.AbstractDocument.class;
            if (n.equals("getLength") && ps.length == 0) return base ? "return __content.length();" : "return super.getLength();";
            if (n.equals("getText") && ps.length == 2 && ps[0] == int.class)
                return base ? "int s = Math.max(0, Math.min(a0, __content.length())); return __content.substring(s, Math.max(s, Math.min(s + a1, __content.length())));" : "return super.getText(a0, a1);";
            if (n.equals("insertString") && ps.length == 3)
                return base ? "if (a1 != null) __content.insert(Math.max(0, Math.min(a0, __content.length())), a1);" : "super.insertString(a0, a1, a2);";
            if (n.equals("remove") && ps.length == 2 && ps[0] == int.class)
                return base ? "int s = Math.max(0, Math.min(a0, __content.length())); __content.delete(s, Math.min(s + Math.max(0, a1), __content.length()));" : "super.remove(a0, a1);";
        }
        // A text pane's document is a styled one, shared by getDocument and getStyledDocument.
        if (generate.contains(javax.swing.text.DefaultStyledDocument.class)) {
            if (c == javax.swing.text.JTextComponent.class && n.equals("getDocument") && ps.length == 0 && props.containsKey("Document"))
                return "if (!__DocumentSet) { __Document = this instanceof javax.swing.JTextPane ? new javax.swing.text.DefaultStyledDocument() : new javax.swing.text.PlainDocument(); __DocumentSet = true; } return __Document;";
            if (c == javax.swing.JTextPane.class && n.equals("getStyledDocument") && ps.length == 0)
                return "javax.swing.text.Document d = getDocument(); return d instanceof javax.swing.text.StyledDocument ? (javax.swing.text.StyledDocument) d : new javax.swing.text.DefaultStyledDocument();";
        }
        if (javax.swing.text.JTextComponent.class.isAssignableFrom(c) && generate.contains(javax.swing.text.AbstractDocument.class)
                && x.getDeclaringClass() == c) {
            boolean base = c == javax.swing.text.JTextComponent.class;
            String doc = "javax.swing.text.Document d = getDocument(); ";
            if (n.equals("getText") && ps.length == 0)
                return base ? doc + "if (d instanceof javax.swing.text.AbstractDocument) return ((javax.swing.text.AbstractDocument) d).__content.toString(); return __TextSet ? __Text : \"\";" : "return super.getText();";
            if (n.equals("setText") && ps.length == 1)
                return base ? "__Text = a0; __TextSet = true; " + doc + "if (d instanceof javax.swing.text.AbstractDocument) { java.lang.StringBuilder b = ((javax.swing.text.AbstractDocument) d).__content; b.setLength(0); if (a0 != null) b.append(a0); }" : "super.setText(a0);";
            if (n.equals("getText") && ps.length == 2 && ps[0] == int.class) return base ? doc + "return d.getText(a0, a1);" : "return super.getText(a0, a1);";
            // Swing text components start editable (QH's JGenerator turns it off for its labels).
            if (base && n.equals("isEditable") && ps.length == 0) return "return __EditableSet ? __Editable : true;";
        }
        // The sidebar draws icon-only tabs (Skill Calculator) from ImageIcon.getImage().
        if (c == javax.swing.ImageIcon.class && props.containsKey("Image") && ps.length == 0) {
            if (n.equals("getIconWidth")) return "return __Image != null ? __Image.getWidth(null) : 0;";
            if (n.equals("getIconHeight")) return "return __Image != null ? __Image.getHeight(null) : 0;";
        }
        if (c == java.awt.event.ItemEvent.class) {
            if (n.equals("getItem") && ps.length == 0) return "return __item;";
            if (n.equals("getStateChange") && ps.length == 0) return "return __state;";
        }
        if (c == java.awt.Container.class) {
            if (n.equals("add") && ps.length >= 1 && ps[0] == java.awt.Component.class) {
                String idx = ps.length == 2 && ps[1] == int.class ? "a1" : ps.length == 3 ? "a2" : "-1";
                String card = ps.length == 2 && ps[1] == Object.class && generate.contains(java.awt.CardLayout.class)
                        ? " if (getLayout() instanceof java.awt.CardLayout && a1 instanceof String) ((java.awt.CardLayout) getLayout()).__cards.put(a1, a0);" : "";
                return "if (a0 != null) { " + REPARENT0 + "if (" + idx + " >= 0 && " + idx + " <= __children.size()) __children.add(" + idx + ", a0); else __children.add(a0); a0.__parent = this; }"
                        + card + (x.getReturnType() == void.class ? "" : " return a0;");
            }
            if (n.equals("add") && ps.length == 2 && ps[0] == String.class && ps[1] == java.awt.Component.class)
                return "if (a1 != null) { " + REPARENT1 + "__children.add(a1); a1.__parent = this; }"
                        + (generate.contains(java.awt.CardLayout.class) ? " if (getLayout() instanceof java.awt.CardLayout) ((java.awt.CardLayout) getLayout()).__cards.put(a0, a1);" : "")
                        + " return a1;";
            if (n.equals("remove") && ps.length == 1 && ps[0] == java.awt.Component.class) return "__children.remove(a0); if (a0 != null) a0.__parent = null;";
            if (n.equals("remove") && ps.length == 1 && ps[0] == int.class) return "if (a0 >= 0 && a0 < __children.size()) ((java.awt.Component) __children.remove(a0)).__parent = null;";
            if (n.equals("removeAll") && ps.length == 0) return "for (Object o : __children) ((java.awt.Component) o).__parent = null; __children.clear();";
            if (n.equals("getComponents") && ps.length == 0) return "return (java.awt.Component[]) __children.toArray(new java.awt.Component[0]);";
            if (n.equals("getComponentCount") && ps.length == 0) return "return __children.size();";
            if (n.equals("getComponent") && ps.length == 1 && ps[0] == int.class) return "return a0 >= 0 && a0 < __children.size() ? (java.awt.Component) __children.get(a0) : null;";
            if (n.equals("isAncestorOf") && ps.length == 1) return "for (java.awt.Container p = a0 == null ? null : a0.__parent; p != null; p = p.__parent) if (p == this) return true; return false;";
        }
        if (c == java.awt.Component.class && n.equals("getParent") && ps.length == 0) return "return __parent;";
        if (x.getDeclaringClass() != c) return null;
        if (!Modifier.isStatic(x.getModifiers())) {
            Class<?> r = x.getReturnType();
            // JPopupMenu.add(JMenuItem), JMenu.add(JMenuItem): real children (getComponent(i) finds them).
            if (n.equals("add") && ps.length == 1 && java.awt.Container.class.isAssignableFrom(c) && c != java.awt.Container.class
                    && java.awt.Component.class.isAssignableFrom(ps[0]) && r == ps[0])
                return "super.add((java.awt.Component) a0); return a0;";
            if (n.equals("addComponent") && ps.length >= 1 && ps[0] == java.awt.Component.class
                    && javax.swing.GroupLayout.Group.class.isAssignableFrom(c) && r.isAssignableFrom(c))
                return "if (a0 != null && __layout != null && __layout.__host != null && a0.getParent() != __layout.__host) __layout.__host.add(a0); return this;";
            // Builders (GroupLayout groups): add*/set*/link* returning their own type hand back this.
            if ((n.startsWith("add") || n.startsWith("link")) && r != void.class && !r.isPrimitive() && r != Object.class
                    && r.isAssignableFrom(c) && !(ps.length >= 1 && r.isAssignableFrom(ps[0])))
                return "return this;";
            // Factories of a class's own inner classes (GroupLayout.createParallelGroup).
            if (n.startsWith("create") && r.getEnclosingClass() == c && generate.contains(r) && !r.isInterface()
                    && !Modifier.isAbstract(r.getModifiers()))
                return c == javax.swing.GroupLayout.class && javax.swing.GroupLayout.Group.class.isAssignableFrom(r)
                        ? typeName(r) + " g = new " + typeName(r) + "(); g.__layout = this; return g;"
                        : "return new " + typeName(r) + "();";
        }
        String l = listenerKind(x);
        if (l != null && listenerKinds.contains(l)) {
            if (n.startsWith("add")) return "if (a0 != null) __" + l + "s.add(a0);";
            if (n.startsWith("remove")) return "__" + l + "s.remove(a0);";
            return "return (" + typeName(x.getReturnType()) + ") __" + l + "s.toArray(new " + typeName(x.getReturnType().getComponentType()) + "[0]);";
        }
        String p = prop(x);
        if (p != null && partners(c, x).isEmpty() && superAccessor(c, x) != null)
            return (x.getReturnType() == void.class ? "super." : "return super.") + n + "(" + (ps.length == 1 ? "a0" : "") + ");";
        if (p != null && props.containsKey(p)) {
            Class<?> t = props.get(p);
            if (n.startsWith("set")) {
                // Overloads whose parameter doesn't fit the property's type (JList.setListData(Vector)
                // next to setListData(Object[])) store nothing rather than not compiling.
                Class<?> a = ps[0];
                boolean fits = a == t || (!a.isPrimitive() && !t.isPrimitive() && t.isAssignableFrom(a));
                return fits ? "__" + p + " = a0; __" + p + "Set = true;" : null;
            }
            if (!x.getReturnType().isAssignableFrom(t) && !(t.isPrimitive() && x.getReturnType() == t)) return null;
            String dflt = returnValue(x.getReturnType());
            // Swing components start visible and enabled (only top-level windows start hidden).
            if (c == java.awt.Component.class && x.getReturnType() == boolean.class
                    && (p.equals("Visible") || p.equals("Enabled") || p.equals("Focusable"))) dflt = "true";
            if (dflt.startsWith("new ") && x.getReturnType() == t) {
                // Objects handed out by a getter persist, as in Swing (listeners added to them stick).
                return "if (!__" + p + "Set) { __" + p + " = " + dflt + "; __" + p + "Set = true; } return __" + p + ";";
            }
            return "return __" + p + "Set ? (" + typeName(x.getReturnType()) + ") __" + p + " : " + dflt + ";";
        }
        return null;
    }

    static boolean hasNoArgCtor(Class<?> c) {
        if (c.isInterface()) return true;
        for (Member m : members.getOrDefault(c, Set.of())) if (m instanceof Constructor && ((Constructor<?>) m).getParameterCount() == 0) return true;
        return members.getOrDefault(c, Set.of()).stream().noneMatch(m -> m instanceof Constructor);
    }

    static String superArgs(Class<?> s) {
        for (Member m : members.getOrDefault(s, Set.of())) {
            if (m instanceof Constructor) {
                return Arrays.stream(((Constructor<?>) m).getParameterTypes()).map(t -> {
                    String d = defaultValue(t);
                    return t.isPrimitive() ? d : "(" + typeName(t) + ") " + d;
                }).collect(Collectors.joining(", "));
            }
        }
        return "";
    }

    static String params(Class<?>[] ps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.length; i++) sb.append(i > 0 ? ", " : "").append(typeName(ps[i])).append(" a").append(i);
        return sb.toString();
    }

    /**
     * The JDK's initial value of a public instance field, for plain value classes (not components)
     * with a public no-arg constructor: desktop code relies on them (Emote Clue Items writes
     * {@code new GridBagConstraints().insets.right}). Null when unknown or the default (0/null).
     */
    static String instanceDefault(Class<?> c, Field f) {
        if (java.awt.Component.class.isAssignableFrom(c) || c.isInterface() || Modifier.isAbstract(c.getModifiers())) return null;
        Object v;
        try {
            Object o = c.getConstructor().newInstance();
            f.setAccessible(true);
            v = f.get(o);
        } catch (Throwable e) {
            return null;
        }
        if (v == null) return null;
        Class<?> t = f.getType();
        if (t.isPrimitive() || t == String.class) {
            if (v instanceof Number && ((Number) v).doubleValue() == 0) return null;
            if (Boolean.FALSE.equals(v)) return null;
            return literal(v, t);
        }
        if (v instanceof java.awt.Insets) {
            java.awt.Insets i = (java.awt.Insets) v;
            return "new java.awt.Insets(" + i.top + ", " + i.left + ", " + i.bottom + ", " + i.right + ")";
        }
        return null;
    }

    static String literal(Object v, Class<?> t) {
        if (v instanceof String) return "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (t == long.class) return v + "L";
        if (t == float.class) {
            float f = (Float) v;
            if (Float.isNaN(f)) return "Float.NaN";
            if (Float.isInfinite(f)) return f > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
            return v + "f";
        }
        if (t == double.class) {
            double d = (Double) v;
            if (Double.isNaN(d)) return "Double.NaN";
            if (Double.isInfinite(d)) return d > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
            return v + "d";
        }
        if (t == char.class) return "(char) " + (int) (Character) v;
        if (t == byte.class) return "(byte) " + v;
        if (t == short.class) return "(short) " + v;
        return v.toString();
    }
}
