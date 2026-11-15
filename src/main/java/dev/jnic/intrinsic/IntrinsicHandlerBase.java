package dev.jnic.intrinsic;

import dev.jnic.handler.OpcodeHandler;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public abstract class IntrinsicHandlerBase implements OpcodeHandler {
    private final int opcode;
    private final String owner;
    private final String name;
    private final String descriptor;

    protected IntrinsicHandlerBase(int opcode, String owner, String name, String descriptor) {
        this.opcode = opcode;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    @Override
    public boolean matches(AbstractInsnNode insn) {
        if (insn == null || insn.getOpcode() != this.opcode) {
            return false;
        }
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            return (this.owner == null || this.owner.equals(methodInsn.owner))
                    && (this.name == null || this.name.equals(methodInsn.name))
                    && (this.descriptor == null || this.descriptor.equals(methodInsn.desc));
        }
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode fieldInsn = (FieldInsnNode) insn;
            return (this.owner == null || this.owner.equals(fieldInsn.owner))
                    && (this.name == null || this.name.equals(fieldInsn.name))
                    && (this.descriptor == null || this.descriptor.equals(fieldInsn.desc));
        }
        return false;
    }

    @Override
    public boolean isIntrinsic() {
        return true;
    }
}
