package dev.jnic.cache;

import java.util.Objects;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MemberKey {
    private final String owner;
    private final String name;
    private final String descriptor;

    public MemberKey(String owner, String name, String descriptor) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public static MemberKey of(ClassNode owner, MethodNode method) {
        return new MemberKey(
                owner != null ? owner.name : "",
                method != null ? method.name : "",
                method != null ? method.desc : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberKey)) return false;
        MemberKey that = (MemberKey) o;
        return Objects.equals(this.owner, that.owner)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.owner, this.name, this.descriptor);
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
