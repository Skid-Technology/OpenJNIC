package dev.jnic.pipeline;

public class Entry {
    private final String name;
    private final String descriptor;
    private final byte[] data;

    public Entry(String name, String descriptor, byte[] data) {
        this.name = name;
        this.descriptor = descriptor;
        this.data = data;
    }

    public String getName() {
        return this.name;
    }

    public String getDescriptor() {
        return this.descriptor;
    }

    public byte[] getData() {
        return this.data;
    }
}
