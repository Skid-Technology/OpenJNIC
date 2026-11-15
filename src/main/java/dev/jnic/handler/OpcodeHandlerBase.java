package dev.jnic.handler;

import java.util.BitSet;
import org.objectweb.asm.tree.AbstractInsnNode;

public abstract class OpcodeHandlerBase implements OpcodeHandler {
    private final boolean intrinsic;
    private final BitSet opcodes = new BitSet();

    public OpcodeHandlerBase(boolean intrinsic, int... opcodes) {
        this.intrinsic = intrinsic;
        if (opcodes != null) {
            for (int opcode : opcodes) {
                this.opcodes.set(opcode);
            }
        }
    }

    @Override
    public boolean matches(AbstractInsnNode insn) {
        return insn != null && insn.getOpcode() >= 0 && this.opcodes.get(insn.getOpcode());
    }

    @Override
    public boolean isIntrinsic() {
        return this.intrinsic;
    }
}
