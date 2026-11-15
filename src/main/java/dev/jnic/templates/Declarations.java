package dev.jnic.templates;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Declarations implements Fragment {
    private final Map<ClassNode, List<MethodNode>> classes;
    private final int options;

    public Declarations(Map<ClassNode, List<MethodNode>> classes, int options) {
        this.classes = Objects.requireNonNull(classes, "classes");
        this.options = options;
    }

    @Override
    public String render(State context) {
        StringBuilder out = new StringBuilder(4096);
        out.append("/* \n")
                .append(" * Auto-generated JNIC implementation file\n")
                .append(" * \n * \n * \n * \n * \n * \n")
                .append(" */\n\n")
                .append("#include <jni.h>\n")
                .append("#include <stdatomic.h>\n")
                .append("#include <stdlib.h>\n")
                .append("#include <string.h>\n")
                .append("#include <stdarg.h>\n")
                .append("#include <math.h>\n\n");
        boolean first = true;
        for (Map.Entry<ClassNode, List<MethodNode>> entry : this.classes.entrySet()) {
            String owner = entry.getKey().name;
            if (owner == null || entry.getValue() == null) {
                continue;
            }
            for (MethodNode method : entry.getValue()) {
                if (method == null || method.name == null || method.desc == null) {
                    continue;
                }
                if (!first) {
                    out.append('\n');
                }
                out.append(Transpiler.declaration(owner, method, true));
                first = false;
            }
        }
        out.append("\n/* End JNIC implementation file */");
        return out.toString();
    }
}
