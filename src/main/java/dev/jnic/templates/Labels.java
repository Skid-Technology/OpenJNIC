package dev.jnic.templates;

import org.objectweb.asm.tree.LabelNode;

@FunctionalInterface
public interface Labels {
    String resolve(String prefix, LabelNode label);

    default String resolve(LabelNode label) {
        return this.resolve("1", label);
    }
}
