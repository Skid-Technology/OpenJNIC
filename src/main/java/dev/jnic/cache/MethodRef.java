package dev.jnic.cache;

public class MethodRef {
    private final String owner;
    private final String name;
    private final String descriptor;

    public MethodRef(String owner, String name, String descriptor) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public static MethodRef of(String owner, String name, String descriptor) {
        return new MethodRef(owner, name, descriptor);
    }

    public String getOwner() {
        return this.owner;
    }

    public String getName() {
        return this.name;
    }

    public String getDescriptor() {
        return this.descriptor;
    }
}
