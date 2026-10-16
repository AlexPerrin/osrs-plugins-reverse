package net.runelite.host;

import java.lang.reflect.Proxy;
import java.util.Collections;

/** Interface instances that do nothing: false/0/null, empty iterators and arrays. */
public final class Inert {
    private Inert() {}

    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> iface) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, (proxy, m, args) -> {
            Class<?> r = m.getReturnType();
            switch (m.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "Inert " + iface.getSimpleName();
                case "iterator": return Collections.emptyIterator();
            }
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            if (r == float.class) return 0f;
            if (r == double.class) return 0d;
            if (r == short.class) return (short) 0;
            if (r == byte.class) return (byte) 0;
            if (r == char.class) return (char) 0;
            if (r.isArray()) return java.lang.reflect.Array.newInstance(r.getComponentType(), 0);
            return null;
        });
    }
}
