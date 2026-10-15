package net.runelite.client.eventbus;

import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Replaces RuneLite's EventBus (same name and API). RuneLite's builds lambdas with
 * MethodHandles.privateLookupIn, which Android 9 lacks; this one invokes @Subscribe methods
 * reflectively. Dispatch is by exact event class, highest priority first.
 */
public class EventBus {
    public static final class Subscriber {
        private final Object object;
        private final Method method;
        private final float priority;
        private final Consumer<Object> lambda;

        public Subscriber(Object object, Method method, float priority, Consumer<Object> lambda) {
            this.object = object;
            this.method = method;
            this.priority = priority;
            this.lambda = lambda;
        }

        public Object getObject() { return object; }
        public Method getMethod() { return method; }
        public float getPriority() { return priority; }
        public Consumer<Object> getLambda() { return lambda; }

        void invoke(Object event) throws Exception {
            if (lambda != null) {
                lambda.accept(event);
            } else {
                method.invoke(object, event);
            }
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Subscriber)) return false;
            Subscriber s = (Subscriber) o;
            return object == s.object && Objects.equals(method, s.method) && lambda == s.lambda;
        }
        @Override public int hashCode() { return System.identityHashCode(object) * 31 + Objects.hashCode(method); }
        @Override public String toString() { return "Subscriber[" + (method != null ? method : lambda) + "]"; }
    }

    private final Consumer<Throwable> exceptionHandler;
    // Copy-on-write snapshot: posting never takes the lock.
    private volatile Map<Class<?>, List<Subscriber>> subscribers = Collections.emptyMap();

    public EventBus() {
        this(t -> Log.e("osrsrl-host", "uncaught exception in event subscriber", t));
    }

    public EventBus(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public synchronized void register(Object object) {
        Map<Class<?>, List<Subscriber>> next = copy();
        for (Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                Subscribe sub = m.getAnnotation(Subscribe.class);
                if (sub == null) continue;
                if (m.getParameterTypes().length != 1) {
                    throw new IllegalArgumentException("@Subscribe method must take exactly one argument: " + m);
                }
                m.setAccessible(true);
                next.computeIfAbsent(m.getParameterTypes()[0], k -> new ArrayList<>())
                        .add(new Subscriber(object, m, sub.priority(), null));
            }
        }
        publish(next);
    }

    public synchronized <T> Subscriber register(Class<T> clazz, Consumer<T> consumer, float priority) {
        Map<Class<?>, List<Subscriber>> next = copy();
        @SuppressWarnings("unchecked")
        Subscriber s = new Subscriber(consumer, null, priority, (Consumer<Object>) consumer);
        next.computeIfAbsent(clazz, k -> new ArrayList<>()).add(s);
        publish(next);
        return s;
    }

    public synchronized void unregister(Object object) {
        if (object == null) return;
        Map<Class<?>, List<Subscriber>> next = copy();
        for (List<Subscriber> list : next.values()) list.removeIf(s -> s.object == object);
        publish(next);
    }

    public synchronized void unregister(Subscriber subscriber) {
        if (subscriber == null) return;
        Map<Class<?>, List<Subscriber>> next = copy();
        for (List<Subscriber> list : next.values()) list.remove(subscriber);
        publish(next);
    }

    public void post(Object event) {
        List<Subscriber> list = subscribers.get(event.getClass());
        if (list == null) return;
        for (Subscriber s : list) {
            try {
                s.invoke(event);
            } catch (InvocationTargetException e) {
                exceptionHandler.accept(e.getCause());
            } catch (Throwable t) {
                exceptionHandler.accept(t);
            }
        }
    }

    private Map<Class<?>, List<Subscriber>> copy() {
        Map<Class<?>, List<Subscriber>> next = new HashMap<>();
        for (Map.Entry<Class<?>, List<Subscriber>> e : subscribers.entrySet()) next.put(e.getKey(), new ArrayList<>(e.getValue()));
        return next;
    }

    private void publish(Map<Class<?>, List<Subscriber>> next) {
        for (List<Subscriber> list : next.values()) list.sort(Comparator.comparingDouble((Subscriber s) -> -s.priority));
        next.values().removeIf(List::isEmpty);
        subscribers = next;
    }
}
