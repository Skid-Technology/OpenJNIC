package dev.jnic.cache;

import dev.jnic.crypto.ChaCha20Stream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class JniCacheRegistry {
    private final AtomicInteger classCounter = new AtomicInteger();
    private final Map<String, ClassCacheEntry> entries = new LinkedHashMap<>();
    private final ChaCha20Stream stream;

    public JniCacheRegistry(ChaCha20Stream stream) {
        this.stream = stream;
    }

    public String registerClass(String internalName) {
        return getOrCreate(internalName, false).getSymbolName();
    }

    public String registerField(String owner, String name, String desc, boolean isStatic) {
        ClassCacheEntry entry = getOrCreate(owner, false);
        return entry.registerField(new MemberDescriptor(name, desc, isStatic));
    }

    public String registerMethod(String owner, String name, String desc, boolean isStatic) {
        ClassCacheEntry entry = getOrCreate(owner, false);
        return entry.registerMethod(new MemberDescriptor(name, desc, isStatic));
    }

    public String registerLookup(String internalName) {
        ClassCacheEntry entry = getOrCreate(internalName, true);
        return entry.getSymbolName() + "l";
    }

    private ClassCacheEntry getOrCreate(String internalName, boolean withLookup) {
        String normalized = internalName == null ? "java/lang/Object" : internalName;
        ClassCacheEntry existing = this.entries.get(normalized);
        if (existing != null) {
            return existing;
        }
        int id = this.classCounter.getAndIncrement();
        ClassCacheEntry created = new ClassCacheEntry(id, "c_" + id, normalized, withLookup);
        this.entries.put(normalized, created);
        return created;
    }

    public String emitAllDeclarations() {
        StringBuilder sb = new StringBuilder();
        for (ClassCacheEntry entry : this.entries.values()) {
            sb.append(entry.emitDeclaration());
        }
        return sb.toString();
    }
}
