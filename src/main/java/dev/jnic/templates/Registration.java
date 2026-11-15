package dev.jnic.templates;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Registration implements Fragment {
    private final Map<ClassNode, List<MethodNode>> classes;
    private final int options;

    public Registration(Map<ClassNode, List<MethodNode>> classes, int options) {
        this.classes = Objects.requireNonNull(classes, "classes");
        this.options = options;
    }

    @Override
    public String render(State context) {
        StringBuilder out = new StringBuilder(4096);
        int nativeIndex = 0;
        for (Map.Entry<ClassNode, List<MethodNode>> entry : this.classes.entrySet()) {
            ClassNode type = entry.getKey();
            List<MethodNode> methods = entry.getValue();
            if (type == null || type.name == null || methods == null || methods.isEmpty()) {
                continue;
            }
            out.append("JNIEXPORT void JNICALL ")
                    .append(Transpiler.loaderSymbol(type.name))
                    .append("(JNIEnv *env, jclass clazz) {\n")
                    .append("    JNINativeMethod table[] = {\n");
            for (MethodNode method : methods) {
                if (method == null || method.name == null || method.desc == null) {
                    continue;
                }
                out.append(Transpiler.registrationEntry(method, nativeIndex++)).append(",\n");
            }
            out.append("    };\n")
                    .append("    (*env)->RegisterNatives(env, clazz, table, ")
                    .append(emittedCount(methods))
                    .append(");\n")
                    .append("}\n\n");
        }
        return out.toString();
    }

    private static int emittedCount(List<MethodNode> methods) {
        int count = 0;
        for (MethodNode method : methods) {
            if (method != null && method.name != null && method.desc != null) {
                ++count;
            }
        }
        return count;
    }
}
