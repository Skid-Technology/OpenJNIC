package dev.jnic.type;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;

class StackTypeInterpreter extends Interpreter<StackValueType> {
    public static final StackTypeInterpreter INSTANCE = new StackTypeInterpreter();

    private StackTypeInterpreter() {
        super(Opcodes.ASM9);
    }

    @Override
    public StackValueType newValue(Type type) {
        return StackValueType.fromType(type);
    }

    @Override
    public StackValueType newParameterValue(boolean isInstanceMethod, int local, Type type) {
        return StackValueType.fromType(type);
    }

    @Override
    public StackValueType newOperation(AbstractInsnNode insn) throws AnalyzerException {
        if (insn == null) {
            return StackValueType.REFERENCE;
        }
        switch (insn.getOpcode()) {
            case Opcodes.ACONST_NULL:
                return StackValueType.NULL;
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return StackValueType.PRIMITIVE;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                return StackValueType.WIDE;
            case Opcodes.LDC: {
                if (insn instanceof LdcInsnNode) {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof Long || cst instanceof Double) {
                        return StackValueType.WIDE;
                    }
                    if (cst instanceof Integer || cst instanceof Float) {
                        return StackValueType.PRIMITIVE;
                    }
                    return StackValueType.NON_NULL;
                }
                return StackValueType.REFERENCE;
            }
            case Opcodes.GETSTATIC:
                if (insn instanceof FieldInsnNode) {
                    return StackValueType.fromType(Type.getType(((FieldInsnNode) insn).desc));
                }
                return StackValueType.REFERENCE;
            case Opcodes.NEW:
                return StackValueType.NON_NULL;
            default:
                return StackValueType.REFERENCE;
        }
    }

    @Override
    public StackValueType copyOperation(AbstractInsnNode insn, StackValueType value) throws AnalyzerException {
        return value != null ? value : StackValueType.REFERENCE;
    }

    @Override
    public StackValueType unaryOperation(AbstractInsnNode insn, StackValueType value) throws AnalyzerException {
        if (insn == null) {
            return value;
        }
        switch (insn.getOpcode()) {
            case Opcodes.I2L:
            case Opcodes.I2D:
            case Opcodes.F2L:
            case Opcodes.F2D:
            case Opcodes.D2L:
                return StackValueType.WIDE;
            case Opcodes.L2I:
            case Opcodes.L2F:
            case Opcodes.F2I:
            case Opcodes.D2I:
            case Opcodes.D2F:
            case Opcodes.ARRAYLENGTH:
            case Opcodes.INSTANCEOF:
                return StackValueType.PRIMITIVE;
            case Opcodes.NEWARRAY:
            case Opcodes.ANEWARRAY:
                return StackValueType.NON_NULL;
            case Opcodes.CHECKCAST:
                return value != null ? value : StackValueType.REFERENCE;
            case Opcodes.PUTSTATIC:
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.ATHROW:
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                return null;
            case Opcodes.GETFIELD:
                if (insn instanceof FieldInsnNode) {
                    return StackValueType.fromType(Type.getType(((FieldInsnNode) insn).desc));
                }
                return StackValueType.REFERENCE;
            default:
                return value != null ? value : StackValueType.REFERENCE;
        }
    }

    @Override
    public StackValueType binaryOperation(AbstractInsnNode insn, StackValueType value1, StackValueType value2) throws AnalyzerException {
        if (insn == null) {
            return StackValueType.REFERENCE;
        }
        switch (insn.getOpcode()) {
            case Opcodes.LALOAD:
            case Opcodes.DALOAD:
            case Opcodes.LADD:
            case Opcodes.DADD:
            case Opcodes.LSUB:
            case Opcodes.DSUB:
            case Opcodes.LMUL:
            case Opcodes.DMUL:
            case Opcodes.LDIV:
            case Opcodes.DDIV:
            case Opcodes.LREM:
            case Opcodes.DREM:
            case Opcodes.LSHL:
            case Opcodes.LSHR:
            case Opcodes.LUSHR:
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
                return StackValueType.WIDE;
            case Opcodes.AALOAD:
                return StackValueType.REFERENCE;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
            case Opcodes.PUTFIELD:
                return null;
            default:
                return StackValueType.PRIMITIVE;
        }
    }

    @Override
    public StackValueType ternaryOperation(AbstractInsnNode insn, StackValueType value1, StackValueType value2, StackValueType value3) throws AnalyzerException {
        return null;
    }

    @Override
    public StackValueType naryOperation(AbstractInsnNode insn, List<? extends StackValueType> values) throws AnalyzerException {
        if (insn == null) {
            return StackValueType.REFERENCE;
        }
        int op = insn.getOpcode();
        if (op == Opcodes.MULTIANEWARRAY) {
            return StackValueType.NON_NULL;
        }
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            if ("<init>".equals(methodInsn.name) && op == Opcodes.INVOKESPECIAL) {
                return null;
            }
            return StackValueType.fromType(Type.getReturnType(methodInsn.desc));
        }
        if (insn instanceof InvokeDynamicInsnNode) {
            return StackValueType.fromType(Type.getReturnType(((InvokeDynamicInsnNode) insn).desc));
        }
        return StackValueType.REFERENCE;
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, StackValueType value, StackValueType expected) throws AnalyzerException {
    }

    @Override
    public StackValueType merge(StackValueType value1, StackValueType value2) {
        if (value1 == null) {
            return value2;
        }
        return value1.merge(value2);
    }
}
