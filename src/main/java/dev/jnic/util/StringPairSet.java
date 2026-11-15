package dev.jnic.util;

import java.util.HashSet;
import java.util.Set;

public class StringPairSet {
    private final Set<StringPair> pairs = new HashSet<>();

    public void add(StringPair pair) {
        this.pairs.add(pair);
    }

    public boolean contains(StringPair pair) {
        return this.pairs.contains(pair);
    }

    public int size() {
        return this.pairs.size();
    }
}
