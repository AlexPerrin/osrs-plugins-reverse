package net.runelite.host;

import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.client.eventbus.Subscribe;

/**
 * Dispatches events to methods annotated with RuneLite's {@link Subscribe}, by exact
 * parameter type and descending priority. RuneLite's own EventBus needs
 * MethodHandles.privateLookupIn, which Android 9 lacks, so this replaces it.
 */
public final class HostEventBus {
    private static final class Subscriber {
        final Object target;
        final Method method;
        final float priority;

        Subscriber(Object target, Method method, float priority) {
            this.target = target;
            this.method = method;
            this.priority = priority;
        }
    }

    private final Map<Class<?>, List<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public void register(Object target) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                Subscribe sub = m.getAnnotation(Subscribe.class);
                if (sub == null || m.getParameterTypes().length != 1) continue;
                m.setAccessible(true);
                List<Subscriber> list = subscribers.computeIfAbsent(m.getParameterTypes()[0],
                        k -> new CopyOnWriteArrayList<>());
                List<Subscriber> sorted = new ArrayList<>(list);
                sorted.add(new Subscriber(target, m, sub.priority()));
                sorted.sort(Comparator.comparingDouble((Subscriber s) -> -s.priority));
                list.clear();
                list.addAll(sorted);
            }
        }
    }

    public void unregister(Object target) {
        for (List<Subscriber> list : subscribers.values()) list.removeIf(s -> s.target == target);
    }

    public void post(Object event) {
        List<Subscriber> list = subscribers.get(event.getClass());
        if (list == null) return;
        for (Subscriber s : list) {
            try {
                s.method.invoke(s.target, event);
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                Log.e(Host.TAG, "subscriber " + s.method + " threw", cause);
            }
        }
    }
}
