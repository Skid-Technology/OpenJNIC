package dev.jnic.handler;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

public final class JvmOpcodeHandlers {
    private JvmOpcodeHandlers() {
    }

    public static String emit(AbstractInsnNode instruction, int pc) {
        int op = instruction.getOpcode();
        switch (op) {
            case Opcodes.NOP:
                return "    /* nop */\n";
            case Opcodes.ACONST_NULL:
                return "    stack0.l = nullptr;\n";
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                return "    stack0.i = " + (op - Opcodes.ICONST_0) + ";\n";
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
                return "    stack0.j = " + (op - Opcodes.LCONST_0) + "LL;\n";
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                return "    stack0.f = " + (op - Opcodes.FCONST_0) + ".0f;\n";
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                return "    stack0.d = " + (op - Opcodes.DCONST_0) + ".0;\n";
            case Opcodes.IADD:
                return "    stack1.i += stack0.i;\n";
            case Opcodes.ISUB:
                return "    stack1.i -= stack0.i;\n";
            case Opcodes.IMUL:
                return "    stack1.i *= stack0.i;\n";
            case Opcodes.IDIV:
                return "    stack1.i /= stack0.i;\n";
            case Opcodes.IREM:
                return "    stack1.i %= stack0.i;\n";
            case Opcodes.INEG:
                return "    stack0.i = -stack0.i;\n";
            case Opcodes.IAND:
                return "    stack1.i &= stack0.i;\n";
            case Opcodes.IOR:
                return "    stack1.i |= stack0.i;\n";
            case Opcodes.IXOR:
                return "    stack1.i ^= stack0.i;\n";
            case Opcodes.IRETURN:
                return "    return stack0.i;\n";
            case Opcodes.LRETURN:
                return "    return stack0.j;\n";
            case Opcodes.FRETURN:
                return "    return stack0.f;\n";
            case Opcodes.DRETURN:
                return "    return stack0.d;\n";
            case Opcodes.ARETURN:
                return "    return stack0.l;\n";
            case Opcodes.RETURN:
                return "    return;\n";
            default:
                return null;
        }
    }
}
