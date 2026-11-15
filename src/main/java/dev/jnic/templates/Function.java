package dev.jnic.templates;

import java.util.Objects;
import org.objectweb.asm.tree.MethodNode;

public class Function implements Fragment {
    private final String nativeFunctionName;
    private final MethodNode method;

    public Function(String nativeFunctionName, MethodNode method) {
        this.nativeFunctionName = Objects.requireNonNull(nativeFunctionName, "nativeFunctionName");
        this.method = Objects.requireNonNull(method, "method");
    }

    @Override
    public String render(State context) {
        return Transpiler.translatedFunction(this.nativeFunctionName, this.method);
    }

    public static String mangle(String value) {
        return Transpiler.mangle(value);
    }
}
