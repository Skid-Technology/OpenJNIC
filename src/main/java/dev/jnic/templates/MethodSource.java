package dev.jnic.templates;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class MethodSource implements Fragment {
    private final ClassNode owner;
    private final MethodNode method;
    private final String recoveredSourceFragment;

    public MethodSource(ClassNode owner, MethodNode method, String recoveredSourceFragment) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.method = Objects.requireNonNull(method, "method");
        this.recoveredSourceFragment = recoveredSourceFragment;
    }

    @Override
    public String render(State context) {
        if (this.owner.name == null) {
            throw new IllegalStateException("Class model has no internal name");
        }
        String definition = Transpiler.safeDefinition(this.owner.name, this.method, this.recoveredSourceFragment);
        Map<AbstractInsnNode, Integer> instructionIndexes = this.indexInstructions();
        Map<LabelNode, String> labels = this.collectLabels();
        StringBuilder metadata = new StringBuilder();
        metadata.append("// recovered method model: ")
                .append(instructionIndexes.size())
                .append(" instructions, ")
                .append(labels.size())
                .append(" labels\n");
        for (Map.Entry<LabelNode, String> entry : labels.entrySet()) {
            metadata.append("// ")
                    .append(entry.getValue())
                    .append(" -> instruction ")
                    .append(instructionIndexes.getOrDefault(entry.getKey(), -1))
                    .append('\n');
        }
        return metadata.append(definition).toString();
    }

    private Map<AbstractInsnNode, Integer> indexInstructions() {
        Map<AbstractInsnNode, Integer> indexes = new IdentityHashMap<>();
        if (this.method.instructions == null) {
            return indexes;
        }
        int index = 0;
        for (AbstractInsnNode instruction : this.method.instructions) {
            indexes.put(instruction, index++);
        }
        return indexes;
    }

    private Map<LabelNode, String> collectLabels() {
        Map<LabelNode, String> labels = new LinkedHashMap<>();
        if (this.method.instructions == null) {
            return labels;
        }
        int index = 0;
        for (AbstractInsnNode instruction : this.method.instructions) {
            if (instruction instanceof LabelNode) {
                labels.put((LabelNode) instruction, "L" + index);
            }
            ++index;
        }
        if (this.method.tryCatchBlocks != null) {
            addTryCatchLabels(labels, this.method.tryCatchBlocks);
        }
        return labels;
    }

    private static void addTryCatchLabels(Map<LabelNode, String> labels, List<TryCatchBlockNode> blocks) {
        for (TryCatchBlockNode block : blocks) {
            putLabel(labels, block.start);
            putLabel(labels, block.end);
            putLabel(labels, block.handler);
        }
    }

    private static void putLabel(Map<LabelNode, String> labels, LabelNode label) {
        if (label != null && !labels.containsKey(label)) {
            labels.put(label, "L" + labels.size());
        }
    }
}
