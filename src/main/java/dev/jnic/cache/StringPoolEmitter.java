package dev.jnic.cache;

import dev.jnic.crypto.ChaCha20Stream;
import dev.jnic.pipeline.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StringPoolEmitter {
    private final AtomicInteger counter = new AtomicInteger();
    private final List<Entry> entries = new ArrayList<>();
    private final ChaCha20Stream stream;

    public StringPoolEmitter(ChaCha20Stream stream) {
        this.stream = stream;
    }

    public String register(byte[] data) {
        int id = this.counter.getAndIncrement();
        String name = "s_" + id;
        this.entries.add(new Entry(name, "", data));
        return name;
    }

    public String emitAll() {
        return "";
    }
}
