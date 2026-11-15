package dev.jnic.templates;

import java.util.Objects;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Definition implements Fragment {
    private final ClassNode owner;
    private final MethodNode method;
    private final String recoveredSourceFragment;

    public Definition(ClassNode owner, MethodNode method, String recoveredSourceFragment) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.method = Objects.requireNonNull(method, "method");
        this.recoveredSourceFragment = recoveredSourceFragment;
    }

    @Override
    public String render(State context) {
        if (this.owner.name == null) {
            throw new IllegalStateException("Class model has no internal name");
        }
        return Transpiler.safeDefinition(this.owner.name, this.method, this.recoveredSourceFragment);
    }

    public static String formatIdentifier(String prefix, String value, int index, String suffix) {
        StringBuilder out = new StringBuilder();
        if (prefix != null) {
            out.append(prefix);
        }
        if (value != null) {
            out.append(value);
        }
        if (index >= 0) {
            out.append(index);
        }
        if (suffix != null) {
            out.append(suffix);
        }
        return out.toString();
    }
}
