package dev.jnic.cache;

import dev.jnic.templates.State;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

public class IndyRegistry {
    private final AtomicInteger counter = new AtomicInteger();
    private final Map<IndyEntry, String> entries = new LinkedHashMap<>();

    public String register(InvokeDynamicInsnNode indy, String ownerName) {
        IndyEntry entry = new IndyEntry(indy, ownerName);
        int id = this.counter.getAndIncrement();
        String idStr = String.valueOf(id);
        entry.setId(idStr);
        this.entries.put(entry, idStr);
        return "indy_" + idStr;
    }

    public String getOrRegisterId(IndyEntry entry) {
        if (entry == null) {
            return "0";
        }
        return this.entries.computeIfAbsent(entry, k -> {
            String id = String.valueOf(this.counter.getAndIncrement());
            k.setId(id);
            return id;
        });
    }

    public String emitAllHelpers(State ctx) {
        StringBuilder sb = new StringBuilder();
        for (IndyEntry entry : this.entries.keySet()) {
            sb.append(entry.emitHelper(ctx));
        }
        return sb.toString();
    }
}
