package com.sun.net.httpserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Subset of the JDK class: case-insensitive multi-valued header map (keys normalised like the JDK). */
public class Headers {
    private final Map<String, List<String>> map = new LinkedHashMap<>();

    private static String key(String k) {
        if (k == null || k.isEmpty()) return k;
        return Character.toUpperCase(k.charAt(0)) + k.substring(1).toLowerCase();
    }

    public void add(String k, String v) { map.computeIfAbsent(key(k), x -> new ArrayList<>()).add(v); }
    public void set(String k, String v) {
        List<String> l = new ArrayList<>();
        l.add(v);
        map.put(key(k), l);
    }
    public String getFirst(String k) {
        List<String> l = map.get(key(k));
        return l == null || l.isEmpty() ? null : l.get(0);
    }
    public List<String> get(Object k) { return k instanceof String ? map.get(key((String) k)) : null; }
    public boolean containsKey(Object k) { return k instanceof String && map.containsKey(key((String) k)); }
    public java.util.Set<Map.Entry<String, List<String>>> entrySet() { return map.entrySet(); }
}
