package dev.jnic.cache;

import dev.jnic.util.StringPair;
import dev.jnic.util.StringPairSet;
import java.util.HashMap;
import java.util.Map;

public class MethodRegistry {
    private final Map<String, StringPairSet> entries = new HashMap<>();

    public void add(String owner, String name, String descriptor) {
        StringPairSet pairSet = this.entries.computeIfAbsent(owner, k -> new StringPairSet());
        pairSet.add(new StringPair(name, descriptor));
    }

    public boolean contains(String owner, String name, String descriptor) {
        StringPairSet pairSet = this.entries.get(owner);
        return pairSet != null && pairSet.contains(new StringPair(name, descriptor));
    }

    public boolean containsOwner(String owner) {
        return this.entries.containsKey(owner);
    }
}
