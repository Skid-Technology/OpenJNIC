package dev.jnic.cache;

import java.util.Objects;

public class MemberDescriptor {
    private final String name;
    private final String descriptor;
    private final boolean isStatic;

    MemberDescriptor(String name, String descriptor, boolean isStatic) {
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
    }

    public String getName() {
        return this.name;
    }

    public String getDescriptor() {
        return this.descriptor;
    }

    public boolean isStatic() {
        return this.isStatic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberDescriptor)) return false;
        MemberDescriptor that = (MemberDescriptor) o;
        return this.isStatic == that.isStatic
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.descriptor, this.isStatic);
    }

    @Override
    public String toString() {
        return this.name + ":" + this.descriptor + ":" + this.isStatic;
    }
}
