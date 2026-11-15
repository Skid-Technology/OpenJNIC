package dev.jnic.translate;

import dev.jnic.pipeline.Options;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class NativeMethodTranslator {
    private static final int ACC_STATIC = Opcodes.ACC_STATIC;

    private final MethodNode method;
    private final StackFlow.State[] states;
    private final Map<AbstractInsnNode, Integer> indexes = new IdentityHashMap<>();
    private final int[] handlerTarget;
    private final int[] handlerWidth;
    private final String owner;

    private final StringBuilder out = new StringBuilder(2048);
    private final Type[] arguments;
    private final Type result;
    private final boolean isStatic;

    private NativeMethodTranslator(String owner, MethodNode method) {
        this.owner = owner;
        this.method = method;
        this.isStatic = (method.access & ACC_STATIC) != 0;
        this.arguments = Type.getArgumentTypes(method.desc);
        this.result = Type.getReturnType(method.desc);
        int size = method.instructions.size();
        this.states = StackFlow.analyze(method);
        this.handlerTarget = new int[size];
        this.handlerWidth = new int[size];
        Arrays.fill(this.handlerTarget, -1);
        Arrays.fill(this.handlerWidth, Integer.MAX_VALUE);
        for (int index = 0; index < size; index++) {
            this.indexes.put(method.instructions.get(index), index);
        }
        this.mapHandlers();
    }

    public static boolean canTranslate(MethodNode method) {
        if (method == null || method.instructions == null) {
            return false;
        }
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
            return false;
        }
        for (AbstractInsnNode node : method.instructions) {
            int opcode = node.getOpcode();
            if (opcode < 0) {
                continue;
            }
            switch (opcode) {
                case Opcodes.JSR:
                case Opcodes.RET:
                case Opcodes.INVOKEDYNAMIC:
                case Opcodes.MULTIANEWARRAY:
                    return false;
                case Opcodes.LDC: {
                    Object constant = ((LdcInsnNode) node).cst;
                    if (!(constant instanceof Integer || constant instanceof Long
                            || constant instanceof Float || constant instanceof Double
                            || constant instanceof String || constant instanceof Type)) {
                        return false;
                    }
                    if (constant instanceof Type && ((Type) constant).getSort() == Type.METHOD) {
                        return false;
                    }
                    break;
                }
                default:
                    break;
            }
        }
        try {
            StackFlow.analyze(method);
            return true;
        } catch (RuntimeException unsupported) {
            return false;
        }
    }

    public static String signature(String functionName, boolean isStatic, String descriptor) {
        StringBuilder signature = new StringBuilder(192);
        signature.append("static ").append(CType.declare(Type.getReturnType(descriptor).getDescriptor()))
                .append(" JNICALL ").append(functionName).append("(JNIEnv *env, ")
                .append(isStatic ? "jclass clazz" : "jobject self");
        Type[] arguments = Type.getArgumentTypes(descriptor);
        for (int index = 0; index < arguments.length; index++) {
            signature.append(", ").append(CType.declare(arguments[index].getDescriptor()))
                    .append(" arg").append(index);
        }
        return signature.append(')').toString();
    }

    public static String translate(String functionName, String owner, MethodNode method) {
        return new NativeMethodTranslator(owner, method).emit(functionName);
    }

    private void mapHandlers() {
        if (this.method.tryCatchBlocks == null) {
            return;
        }
        for (TryCatchBlockNode block : this.method.tryCatchBlocks) {
            Integer start = this.indexes.get(block.start);
            Integer end = this.indexes.get(block.end);
            Integer handler = this.indexes.get(block.handler);
            if (start == null || end == null || handler == null) {
                continue;
            }
            for (int index = start; index < end && index < this.handlerTarget.length; index++) {
                int width = end - index;
                if (width < this.handlerWidth[index]) {
                    this.handlerWidth[index] = width;
                    this.handlerTarget[index] = handler;
                }
            }
        }
    }

    private int inputHeight(int index) {
        StackFlow.State state = this.states[index];
        return state == null ? -1 : state.height();
    }

    private int outputHeight(int index) {
        StackFlow.State state = this.states[index];
        if (state == null) {
            return -1;
        }
        return StackFlow.step(this.method.instructions.get(index), state).height();
    }

    private String handlerLabel(int index) {
        int target = this.handlerTarget[index];
        return target < 0 ? null : "L" + target;
    }

    private String emit(String functionName) {
        int localCount = Math.max(this.method.maxLocals, this.initialLocalCount());
        if (localCount < 1) {
            localCount = 1;
        }
        int stackCount = this.maxStackHeight() + 2;

        this.out.append(signature(functionName, this.isStatic, this.method.desc)).append(" {\n");
        this.out.append("    jvalue local[").append(localCount).append("];\n");
        this.out.append("    jvalue stack[").append(stackCount).append("];\n");
        this.out.append("    memset(local, 0, sizeof(local));\n");
        this.out.append("    memset(stack, 0, sizeof(stack));\n");
        if (Options.INSTANCE.isFlowObfuscationEnabled()) {
            long seed = 0x9E3779B9L;
            for (int index = 0; index < functionName.length(); index++) {
                seed = seed * 0x100000001B3L + functionName.charAt(index);
            }
            this.out.append("    { volatile unsigned _k = ").append(Integer.toUnsignedString((int) seed))
                    .append("u; if (((_k * (_k + 1u)) & 1u) == 1u) { memset(stack, 0, 1); } }\n");
        }

        int local = 0;
        if (!this.isStatic) {
            this.out.append("    local[0].l = self;\n");
            local = 1;
        }
        for (int index = 0; index < this.arguments.length; index++) {
            String descriptor = this.arguments[index].getDescriptor();
            this.out.append("    local[").append(local).append("].").append(CType.slot(descriptor))
                    .append(" = arg").append(index).append(";\n");
            local += this.arguments[index].getSize();
        }

        int size = this.method.instructions.size();
        for (int index = 0; index < size; index++) {
            AbstractInsnNode node = this.method.instructions.get(index);
            if (node instanceof LabelNode) {
                this.out.append("  L").append(index).append(":;\n");
                continue;
            }
            int opcode = node.getOpcode();
            if (opcode < 0) {
                continue;
            }
            int before = this.inputHeight(index);
            if (before < 0) {
                continue;
            }
            int after = this.outputHeight(index);
            this.emitInstruction(node, opcode, before, after, index);
            if (mayThrow(opcode) && fallsThrough(opcode)) {
                String handler = this.handlerLabel(index);
                if (handler != null) {
                    this.out.append("    if ((*env)->ExceptionCheck(env)) { stack[0].l = (*env)->ExceptionOccurred(env); (*env)->ExceptionClear(env); goto ")
                            .append(handler).append("; }\n");
                } else {
                    this.out.append("    if ((*env)->ExceptionCheck(env)) ")
                            .append(CType.defaultReturn(this.result.getDescriptor())).append('\n');
                }
            }
        }

        String fallback = CType.defaultReturn(this.result.getDescriptor());
        if (!fallback.isEmpty()) {
            this.out.append("    ").append(fallback).append('\n');
        }
        return this.out.append("}\n").toString();
    }

    private int maxStackHeight() {
        int max = 0;
        for (StackFlow.State state : this.states) {
            if (state != null && state.height() > max) {
                max = state.height();
            }
        }
        return max;
    }

    private int initialLocalCount() {
        int count = this.isStatic ? 0 : 1;
        for (Type argument : this.arguments) {
            count += argument.getSize();
        }
        return count;
    }

    private static boolean fallsThrough(int opcode) {
        switch (opcode) {
            case Opcodes.GOTO:
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ATHROW:
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
                return false;
            default:
                return true;
        }
    }

    private static boolean mayThrow(int opcode) {
        switch (opcode) {
            case Opcodes.GETSTATIC:
            case Opcodes.PUTSTATIC:
            case Opcodes.GETFIELD:
            case Opcodes.PUTFIELD:
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEINTERFACE:
            case Opcodes.NEW:
            case Opcodes.NEWARRAY:
            case Opcodes.ANEWARRAY:
            case Opcodes.ARRAYLENGTH:
            case Opcodes.IALOAD:
            case Opcodes.LALOAD:
            case Opcodes.FALOAD:
            case Opcodes.DALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD:
            case Opcodes.IASTORE:
            case Opcodes.LASTORE:
            case Opcodes.FASTORE:
            case Opcodes.DASTORE:
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.SASTORE:
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
            case Opcodes.CHECKCAST:
            case Opcodes.INSTANCEOF:
                return true;
            default:
                return false;
        }
    }

    private void line(String statement) {
        this.out.append("    ").append(statement).append('\n');
    }

    private static String findClass(String internalName) {
        return "(*env)->FindClass(env, \"" + escape(internalName) + "\")";
    }

    private void emitInstruction(AbstractInsnNode node, int opcode, int before, int after, int index) {
        switch (opcode) {
            case Opcodes.NOP:
                return;
            case Opcodes.ACONST_NULL:
                this.line("stack[" + (after - 1) + "].l = NULL;");
                return;
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                this.line("stack[" + (after - 1) + "].i = " + (opcode - Opcodes.ICONST_0) + ";");
                return;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
                this.line("stack[" + (after - 1) + "].j = " + (opcode - Opcodes.LCONST_0) + "LL;");
                return;
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                this.line("stack[" + (after - 1) + "].f = " + (opcode - Opcodes.FCONST_0) + ".0f;");
                return;
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                this.line("stack[" + (after - 1) + "].d = " + (opcode - Opcodes.DCONST_0) + ".0;");
                return;
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                this.line("stack[" + (after - 1) + "].i = " + ((IntInsnNode) node).operand + ";");
                return;
            case Opcodes.LDC:
                this.emitLdc((LdcInsnNode) node, after - 1);
                return;
            case Opcodes.ILOAD:
            case Opcodes.FLOAD:
            case Opcodes.ALOAD:
            case Opcodes.LLOAD:
            case Opcodes.DLOAD: {
                String member = memberOf(opcode);
                this.line("stack[" + (after - 1) + "]." + member + " = local["
                        + ((VarInsnNode) node).var + "]." + member + ";");
                return;
            }
            case Opcodes.ISTORE:
            case Opcodes.FSTORE:
            case Opcodes.ASTORE:
            case Opcodes.LSTORE:
            case Opcodes.DSTORE: {
                String member = memberOf(loadOf(opcode));
                this.line("local[" + ((VarInsnNode) node).var + "]." + member + " = stack["
                        + (before - 1) + "]." + member + ";");
                return;
            }
            case Opcodes.ARRAYLENGTH:
                this.emitNullCheck(before - 1, index);
                this.line("stack[" + (before - 1) + "].i = (*env)->GetArrayLength(env, stack[" + (before - 1) + "].l);");
                return;
            case Opcodes.IALOAD:
                this.emitArrayLoad(before, index, "Int", "jint", 'i');
                return;
            case Opcodes.LALOAD:
                this.emitArrayLoad(before, index, "Long", "jlong", 'j');
                return;
            case Opcodes.FALOAD:
                this.emitArrayLoad(before, index, "Float", "jfloat", 'f');
                return;
            case Opcodes.DALOAD:
                this.emitArrayLoad(before, index, "Double", "jdouble", 'd');
                return;
            case Opcodes.BALOAD:
                this.emitArrayLoad(before, index, "Byte", "jbyte", 'i');
                return;
            case Opcodes.CALOAD:
                this.emitArrayLoad(before, index, "Char", "jchar", 'i');
                return;
            case Opcodes.SALOAD:
                this.emitArrayLoad(before, index, "Short", "jshort", 'i');
                return;
            case Opcodes.AALOAD:
                this.emitNullCheck(before - 2, index);
                this.line("stack[" + (before - 2) + "].l = (*env)->GetObjectArrayElement(env, stack["
                        + (before - 2) + "].l, stack[" + (before - 1) + "].i);");
                return;
            case Opcodes.IASTORE:
                this.emitArrayStore(before, index, "Int", "jint", 'i');
                return;
            case Opcodes.LASTORE:
                this.emitArrayStore(before, index, "Long", "jlong", 'j');
                return;
            case Opcodes.FASTORE:
                this.emitArrayStore(before, index, "Float", "jfloat", 'f');
                return;
            case Opcodes.DASTORE:
                this.emitArrayStore(before, index, "Double", "jdouble", 'd');
                return;
            case Opcodes.BASTORE:
                this.emitArrayStore(before, index, "Byte", "jbyte", 'i');
                return;
            case Opcodes.CASTORE:
                this.emitArrayStore(before, index, "Char", "jchar", 'i');
                return;
            case Opcodes.SASTORE:
                this.emitArrayStore(before, index, "Short", "jshort", 'i');
                return;
            case Opcodes.AASTORE:
                this.emitNullCheck(before - 3, index);
                this.line("(*env)->SetObjectArrayElement(env, stack[" + (before - 3) + "].l, stack["
                        + (before - 2) + "].i, stack[" + (before - 1) + "].l);");
                return;
            case Opcodes.POP:
            case Opcodes.POP2:
                return;
            case Opcodes.DUP:
            case Opcodes.DUP_X1:
            case Opcodes.DUP_X2:
            case Opcodes.DUP2:
            case Opcodes.DUP2_X1:
            case Opcodes.DUP2_X2:
            case Opcodes.SWAP:
                this.emitStackManip(opcode, before, this.states[index]);
                return;
            case Opcodes.IADD:
                this.binary(before, 'i', "+");
                return;
            case Opcodes.ISUB:
                this.binary(before, 'i', "-");
                return;
            case Opcodes.IMUL:
                this.binary(before, 'i', "*");
                return;
            case Opcodes.IDIV:
                this.emitDiv(before, 'i', "/", "0", index);
                return;
            case Opcodes.IAND:
                this.binary(before, 'i', "&");
                return;
            case Opcodes.IOR:
                this.binary(before, 'i', "|");
                return;
            case Opcodes.IXOR:
                this.binary(before, 'i', "^");
                return;
            case Opcodes.LADD:
                this.binary(before, 'j', "+");
                return;
            case Opcodes.LSUB:
                this.binary(before, 'j', "-");
                return;
            case Opcodes.LMUL:
                this.binary(before, 'j', "*");
                return;
            case Opcodes.LDIV:
                this.emitDiv(before, 'j', "/", "0", index);
                return;
            case Opcodes.LAND:
                this.binary(before, 'j', "&");
                return;
            case Opcodes.LOR:
                this.binary(before, 'j', "|");
                return;
            case Opcodes.LXOR:
                this.binary(before, 'j', "^");
                return;
            case Opcodes.FADD:
                this.binary(before, 'f', "+");
                return;
            case Opcodes.FSUB:
                this.binary(before, 'f', "-");
                return;
            case Opcodes.FMUL:
                this.binary(before, 'f', "*");
                return;
            case Opcodes.FDIV:
                this.binary(before, 'f', "/");
                return;
            case Opcodes.DADD:
                this.binary(before, 'd', "+");
                return;
            case Opcodes.DSUB:
                this.binary(before, 'd', "-");
                return;
            case Opcodes.DMUL:
                this.binary(before, 'd', "*");
                return;
            case Opcodes.DDIV:
                this.binary(before, 'd', "/");
                return;
            case Opcodes.IREM:
                this.emitDiv(before, 'i', "%", "0", index);
                return;
            case Opcodes.LREM:
                this.emitDiv(before, 'j', "%", "0", index);
                return;
            case Opcodes.FREM:
                this.line("stack[" + (before - 2) + "].f = fmodf(stack[" + (before - 2) + "].f, stack[" + (before - 1) + "].f);");
                return;
            case Opcodes.DREM:
                this.line("stack[" + (before - 2) + "].d = fmod(stack[" + (before - 2) + "].d, stack[" + (before - 1) + "].d);");
                return;
            case Opcodes.ISHL:
                this.shift(before, 'i', "<<", 31, false);
                return;
            case Opcodes.ISHR:
                this.shift(before, 'i', ">>", 31, false);
                return;
            case Opcodes.IUSHR:
                this.shift(before, 'i', ">>", 31, true);
                return;
            case Opcodes.LSHL:
                this.shift(before, 'j', "<<", 63, false);
                return;
            case Opcodes.LSHR:
                this.shift(before, 'j', ">>", 63, false);
                return;
            case Opcodes.LUSHR:
                this.shift(before, 'j', ">>", 63, true);
                return;
            case Opcodes.INEG:
                this.line("stack[" + (before - 1) + "].i = -stack[" + (before - 1) + "].i;");
                return;
            case Opcodes.LNEG:
                this.line("stack[" + (before - 1) + "].j = -stack[" + (before - 1) + "].j;");
                return;
            case Opcodes.FNEG:
                this.line("stack[" + (before - 1) + "].f = -stack[" + (before - 1) + "].f;");
                return;
            case Opcodes.DNEG:
                this.line("stack[" + (before - 1) + "].d = -stack[" + (before - 1) + "].d;");
                return;
            case Opcodes.IINC:
                this.line("local[" + ((IincInsnNode) node).var + "].i += " + ((IincInsnNode) node).incr + ";");
                return;
            case Opcodes.I2L:
                this.convert(before, 'j', "jlong", ".i");
                return;
            case Opcodes.I2F:
                this.convert(before, 'f', "jfloat", ".i");
                return;
            case Opcodes.I2D:
                this.convert(before, 'd', "jdouble", ".i");
                return;
            case Opcodes.L2I:
                this.convert(before, 'i', "jint", ".j");
                return;
            case Opcodes.L2F:
                this.convert(before, 'f', "jfloat", ".j");
                return;
            case Opcodes.L2D:
                this.convert(before, 'd', "jdouble", ".j");
                return;
            case Opcodes.F2I:
                this.convert(before, 'i', "jint", ".f");
                return;
            case Opcodes.F2L:
                this.convert(before, 'j', "jlong", ".f");
                return;
            case Opcodes.F2D:
                this.convert(before, 'd', "jdouble", ".f");
                return;
            case Opcodes.D2I:
                this.convert(before, 'i', "jint", ".d");
                return;
            case Opcodes.D2L:
                this.convert(before, 'j', "jlong", ".d");
                return;
            case Opcodes.D2F:
                this.convert(before, 'f', "jfloat", ".d");
                return;
            case Opcodes.I2B:
                this.convert(before, 'i', "jbyte", ".i");
                return;
            case Opcodes.I2C:
                this.convert(before, 'i', "jchar", ".i");
                return;
            case Opcodes.I2S:
                this.convert(before, 'i', "jshort", ".i");
                return;
            case Opcodes.LCMP:
                this.line("stack[" + (before - 2) + "].i = (stack[" + (before - 2) + "].j > stack[" + (before - 1)
                        + "].j) ? 1 : ((stack[" + (before - 2) + "].j < stack[" + (before - 1) + "].j) ? -1 : 0);");
                return;
            case Opcodes.FCMPL:
                this.cmp(before, 'f', false);
                return;
            case Opcodes.FCMPG:
                this.cmp(before, 'f', true);
                return;
            case Opcodes.DCMPL:
                this.cmp(before, 'd', false);
                return;
            case Opcodes.DCMPG:
                this.cmp(before, 'd', true);
                return;
            case Opcodes.IFEQ:
                this.branch("stack[" + (before - 1) + "].i == 0", node);
                return;
            case Opcodes.IFNE:
                this.branch("stack[" + (before - 1) + "].i != 0", node);
                return;
            case Opcodes.IFLT:
                this.branch("stack[" + (before - 1) + "].i < 0", node);
                return;
            case Opcodes.IFGE:
                this.branch("stack[" + (before - 1) + "].i >= 0", node);
                return;
            case Opcodes.IFGT:
                this.branch("stack[" + (before - 1) + "].i > 0", node);
                return;
            case Opcodes.IFLE:
                this.branch("stack[" + (before - 1) + "].i <= 0", node);
                return;
            case Opcodes.IFNULL:
                this.branch("stack[" + (before - 1) + "].l == NULL", node);
                return;
            case Opcodes.IFNONNULL:
                this.branch("stack[" + (before - 1) + "].l != NULL", node);
                return;
            case Opcodes.IF_ICMPEQ:
                this.branch(this.compare(before, "=="), node);
                return;
            case Opcodes.IF_ICMPNE:
                this.branch(this.compare(before, "!="), node);
                return;
            case Opcodes.IF_ICMPLT:
                this.branch(this.compare(before, "<"), node);
                return;
            case Opcodes.IF_ICMPGE:
                this.branch(this.compare(before, ">="), node);
                return;
            case Opcodes.IF_ICMPGT:
                this.branch(this.compare(before, ">"), node);
                return;
            case Opcodes.IF_ICMPLE:
                this.branch(this.compare(before, "<="), node);
                return;
            case Opcodes.IF_ACMPEQ:
                this.branch("stack[" + (before - 2) + "].l == stack[" + (before - 1) + "].l", node);
                return;
            case Opcodes.IF_ACMPNE:
                this.branch("stack[" + (before - 2) + "].l != stack[" + (before - 1) + "].l", node);
                return;
            case Opcodes.GOTO:
                this.line("goto L" + this.indexes.get(((JumpInsnNode) node).label) + ";");
                return;
            case Opcodes.TABLESWITCH:
                this.emitTableSwitch((TableSwitchInsnNode) node, before);
                return;
            case Opcodes.LOOKUPSWITCH:
                this.emitLookupSwitch((LookupSwitchInsnNode) node, before);
                return;
            case Opcodes.IRETURN:
            case Opcodes.FRETURN:
                this.line("return (" + CType.declare(this.result.getDescriptor()) + ") stack[" + (before - 1) + "]."
                        + CType.slot(this.result.getDescriptor()) + ";");
                return;
            case Opcodes.LRETURN:
            case Opcodes.DRETURN:
                this.line("return stack[" + (before - 1) + "]." + CType.slot(this.result.getDescriptor()) + ";");
                return;
            case Opcodes.ARETURN:
                this.line("return (" + CType.declare(this.result.getDescriptor()) + ") stack[" + (before - 1) + "].l;");
                return;
            case Opcodes.RETURN:
                this.line("return;");
                return;
            case Opcodes.GETSTATIC:
            case Opcodes.PUTSTATIC:
            case Opcodes.GETFIELD:
            case Opcodes.PUTFIELD:
                this.emitField((FieldInsnNode) node, opcode, before, after, index);
                return;
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEINTERFACE:
                this.emitInvoke((MethodInsnNode) node, opcode, before, after, index);
                return;
            case Opcodes.NEW:
                this.line("stack[" + (after - 1) + "].l = (*env)->AllocObject(env, " + findClass(((TypeInsnNode) node).desc) + ");");
                return;
            case Opcodes.NEWARRAY:
                this.line("stack[" + (after - 1) + "].l = (*env)->New" + newArraySuffix(((IntInsnNode) node).operand)
                        + "Array(env, stack[" + (before - 1) + "].i);");
                return;
            case Opcodes.ANEWARRAY:
                this.line("stack[" + (after - 1) + "].l = (*env)->NewObjectArray(env, stack[" + (before - 1)
                        + "].i, " + findClass(((TypeInsnNode) node).desc) + ", NULL);");
                return;
            case Opcodes.CHECKCAST:
                this.emitCheckCast((TypeInsnNode) node, before, index);
                return;
            case Opcodes.INSTANCEOF:
                this.line("stack[" + (after - 1) + "].i = (*env)->IsInstanceOf(env, stack[" + (before - 1)
                        + "].l, " + findClass(((TypeInsnNode) node).desc) + ") ? 1 : 0;");
                return;
            case Opcodes.MONITORENTER:
                this.emitNullCheck(before - 1, index);
                this.line("(*env)->MonitorEnter(env, stack[" + (before - 1) + "].l);");
                return;
            case Opcodes.MONITOREXIT:
                this.line("(*env)->MonitorExit(env, stack[" + (before - 1) + "].l);");
                return;
            case Opcodes.ATHROW:
                this.emitThrow(before, index);
                return;
            default:
                throw new IllegalStateException("Unsupported opcode " + opcode + " in " + this.owner + "." + this.method.name);
        }
    }

    private String two(int before, char slot, String operator) {
        return "stack[" + (before - 2) + "]." + slot + " = stack[" + (before - 2) + "]." + slot + " " + operator
                + " stack[" + (before - 1) + "]." + slot + ";";
    }

    private void binary(int before, char slot, String operator) {
        this.line(this.two(before, slot, operator));
    }

    private void emitDiv(int before, char slot, String operator, String zero, int index) {
        String handler = this.handlerLabel(index);
        this.line("if (stack[" + (before - 1) + "]." + slot + " == " + zero + ") { (*env)->ThrowNew(env, "
                + findClass("java/lang/ArithmeticException") + ", \"/ by zero\"); "
                + this.propagate(handler) + " }");
        this.line(this.two(before, slot, operator));
    }

    private void convert(int before, char slot, String cast, String source) {
        this.line("stack[" + (before - 1) + "]." + slot + " = (" + cast + ") stack[" + (before - 1) + "]" + source + ";");
    }

    private void emitThrow(int before, int index) {
        String handler = this.handlerLabel(index);
        if (handler != null) {
            this.out.append("    { stack[0].l = stack[").append(before - 1).append("].l; goto ").append(handler).append("; }\n");
        } else {
            this.line("(*env)->Throw(env, (jthrowable) stack[" + (before - 1) + "].l);");
            this.line(CType.defaultReturn(this.result.getDescriptor()));
        }
    }

    private void emitCheckCast(TypeInsnNode node, int before, int index) {
        if ("java/lang/Object".equals(node.desc)) {
            return;
        }
        String handler = this.handlerLabel(index);
        String cls = findClass(node.desc);
        this.line("if (stack[" + (before - 1) + "].l != NULL && !(*env)->IsInstanceOf(env, stack[" + (before - 1)
                + "].l, " + cls + ")) { (*env)->ThrowNew(env, " + findClass("java/lang/ClassCastException")
                + ", \"" + escape(node.desc) + "\"); " + this.propagate(handler) + " }");
    }

    private void emitNullCheck(int slot, int index) {
        String handler = this.handlerLabel(index);
        this.line("if (stack[" + slot + "].l == NULL) { (*env)->ThrowNew(env, "
                + findClass("java/lang/NullPointerException") + ", \"\"); " + this.propagate(handler) + " }");
    }

    private String propagate(String handler) {
        if (handler != null) {
            return "stack[0].l = (*env)->ExceptionOccurred(env); (*env)->ExceptionClear(env); goto " + handler + ";";
        }
        return CType.defaultReturn(this.result.getDescriptor());
    }

    private void emitField(FieldInsnNode node, int opcode, int before, int after, int index) {
        String accessor = CType.accessor(node.desc);
        char slot = CType.slot(node.desc);
        String classId = findClass(node.owner);
        boolean isStatic = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC;
        String id = "(*env)->" + (isStatic ? "GetStaticFieldID" : "GetFieldID") + "(env, " + classId + ", \""
                + escape(node.name) + "\", \"" + escape(node.desc) + "\")";
        switch (opcode) {
            case Opcodes.GETSTATIC:
                this.line("stack[" + (after - 1) + "]." + slot + " = (*env)->GetStatic" + accessor
                        + "Field(env, " + classId + ", " + id + ");");
                return;
            case Opcodes.PUTSTATIC:
                this.line("(*env)->SetStatic" + accessor + "Field(env, " + classId + ", " + id + ", stack["
                        + (before - 1) + "]." + slot + ");");
                return;
            case Opcodes.GETFIELD:
                this.emitNullCheck(before - 1, index);
                this.line("stack[" + (after - 1) + "]." + slot + " = (*env)->Get" + accessor
                        + "Field(env, stack[" + (before - 1) + "].l, " + id + ");");
                return;
            case Opcodes.PUTFIELD:
                this.emitNullCheck(before - 2, index);
                this.line("(*env)->Set" + accessor + "Field(env, stack[" + (before - 2) + "].l, " + id + ", stack["
                        + (before - 1) + "]." + slot + ");");
                return;
            default:
                return;
        }
    }

    private void emitInvoke(MethodInsnNode node, int opcode, int before, int after, int index) {
        Type[] argumentTypes = Type.getArgumentTypes(node.desc);
        Type returnType = Type.getReturnType(node.desc);
        boolean isStatic = opcode == Opcodes.INVOKESTATIC;
        int base = before - argumentTypes.length - (isStatic ? 0 : 1);
        if (!isStatic) {
            this.emitNullCheck(base, index);
        }
        String accessor = CType.accessor(returnType.getDescriptor());
        String classId = findClass(node.owner);
        StringBuilder call = new StringBuilder(256);
        if (!"V".equals(returnType.getDescriptor())) {
            call.append("stack[").append(after - 1).append("].").append(CType.slot(returnType.getDescriptor())).append(" = ");
        }
        if (opcode == Opcodes.INVOKESTATIC) {
            String id = "(*env)->GetStaticMethodID(env, " + classId + ", \"" + escape(node.name) + "\", \""
                    + escape(node.desc) + "\")";
            call.append("(*env)->CallStatic").append(accessor).append("Method(env, ").append(classId)
                    .append(", ").append(id);
            this.appendArguments(call, argumentTypes, base);
        } else {
            String id = "(*env)->GetMethodID(env, " + classId + ", \"" + escape(node.name) + "\", \""
                    + escape(node.desc) + "\")";
            if (opcode == Opcodes.INVOKESPECIAL) {
                call.append("(*env)->CallNonvirtual").append(accessor).append("Method(env, stack[").append(base)
                        .append("].l, ").append(classId).append(", ").append(id);
            } else {
                call.append("(*env)->Call").append(accessor).append("Method(env, stack[").append(base)
                        .append("].l, ").append(id);
            }
            this.appendArguments(call, argumentTypes, base + 1);
        }
        call.append(");");
        this.line(call.toString());
    }

    private void appendArguments(StringBuilder call, Type[] argumentTypes, int first) {
        for (int index = 0; index < argumentTypes.length; index++) {
            call.append(", stack[").append(first + index).append("].")
                    .append(CType.slot(argumentTypes[index].getDescriptor()));
        }
    }

    private void emitLdc(LdcInsnNode node, int target) {
        Object constant = node.cst;
        if (constant instanceof Integer) {
            this.line("stack[" + target + "].i = " + constant + ";");
        } else if (constant instanceof Long) {
            this.line("stack[" + target + "].j = " + constant + "LL;");
        } else if (constant instanceof Float) {
            this.line("stack[" + target + "].f = jnic_f(0x"
                    + Integer.toHexString(Float.floatToRawIntBits((Float) constant)) + "u);");
        } else if (constant instanceof Double) {
            this.line("stack[" + target + "].d = jnic_d(0x"
                    + Long.toHexString(Double.doubleToRawLongBits((Double) constant)) + "ull);");
        } else if (constant instanceof String) {
            this.emitString((String) constant, target);
        } else if (constant instanceof Type) {
            this.emitClassLiteral((Type) constant, target);
        } else {
            throw new IllegalStateException("Unsupported LDC constant " + constant);
        }
    }

    private void emitString(String value, int target) {
        this.out.append("    { static const jchar _s[] = {");
        if (value.isEmpty()) {
            this.out.append('0');
        } else if (Options.INSTANCE.isStringObfuscationEnabled()) {
            int key = stringKey(value);
            for (int index = 0; index < value.length(); index++) {
                if (index > 0) {
                    this.out.append(',');
                }
                this.out.append((value.charAt(index) ^ key) & 0xFFFF);
            }
        } else {
            for (int index = 0; index < value.length(); index++) {
                if (index > 0) {
                    this.out.append(',');
                }
                this.out.append((int) value.charAt(index));
            }
        }
        this.out.append("};\n");
        if (!value.isEmpty() && Options.INSTANCE.isStringObfuscationEnabled()) {
            int key = stringKey(value);
            this.out.append("      jchar _d[").append(value.length()).append("];\n");
            this.out.append("      for (int _i = 0; _i < ").append(value.length())
                    .append("; _i++) _d[_i] = (jchar) (_s[_i] ^ ").append(key).append(");\n");
            this.out.append("      stack[").append(target).append("].l = (*env)->NewString(env, _d, ")
                    .append(value.length()).append("); }\n");
            return;
        }
        this.out.append("      stack[").append(target).append("].l = (*env)->NewString(env, _s, ")
                .append(value.length()).append("); }\n");
    }

    private static int stringKey(String value) {
        int key = 0x2A5C;
        for (int index = 0; index < value.length(); index++) {
            key = key * 31 + value.charAt(index);
        }
        key &= 0xFFFF;
        return key == 0 ? 1 : key;
    }

    private void emitClassLiteral(Type type, int target) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
            case Type.LONG:
            case Type.FLOAT:
            case Type.DOUBLE: {
                String wrapper = wrapperClass(type.getSort());
                this.line("stack[" + target + "].l = (*env)->GetStaticObjectField(env, " + findClass(wrapper)
                        + ", (*env)->GetStaticFieldID(env, " + findClass(wrapper) + ", \"TYPE\", \"Ljava/lang/Class;\"));");
                return;
            }
            case Type.VOID:
                this.line("stack[" + target + "].l = (*env)->GetStaticObjectField(env, " + findClass("java/lang/Void")
                        + ", (*env)->GetStaticFieldID(env, " + findClass("java/lang/Void") + ", \"TYPE\", \"Ljava/lang/Class;\"));");
                return;
            case Type.ARRAY:
                this.line("stack[" + target + "].l = (*env)->FindClass(env, \"" + escape(type.getDescriptor()) + "\");");
                return;
            default:
                this.line("stack[" + target + "].l = (*env)->FindClass(env, \"" + escape(type.getInternalName()) + "\");");
                return;
        }
    }

    private static String wrapperClass(int sort) {
        switch (sort) {
            case Type.BOOLEAN:
                return "java/lang/Boolean";
            case Type.BYTE:
                return "java/lang/Byte";
            case Type.CHAR:
                return "java/lang/Character";
            case Type.SHORT:
                return "java/lang/Short";
            case Type.INT:
                return "java/lang/Integer";
            case Type.LONG:
                return "java/lang/Long";
            case Type.FLOAT:
                return "java/lang/Float";
            default:
                return "java/lang/Double";
        }
    }

    private void emitArrayLoad(int before, int index, String suffix, String ctype, char member) {
        this.emitNullCheck(before - 2, index);
        this.line("if ((*env)->ExceptionCheck(env) == JNI_FALSE) { " + ctype + " _v; (*env)->Get" + suffix
                + "ArrayRegion(env, stack[" + (before - 2) + "].l, stack[" + (before - 1) + "].i, 1, &_v); stack["
                + (before - 2) + "]." + member + " = _v; }");
    }

    private void emitArrayStore(int before, int index, String suffix, String ctype, char member) {
        this.emitNullCheck(before - 3, index);
        this.line("if ((*env)->ExceptionCheck(env) == JNI_FALSE) { " + ctype + " _v = stack[" + (before - 1) + "]."
                + member + "; (*env)->Set" + suffix + "ArrayRegion(env, stack[" + (before - 3) + "].l, stack["
                + (before - 2) + "].i, 1, &_v); }");
    }

    private void shift(int before, char slot, String operator, int mask, boolean unsigned) {
        String type = slot == 'j' ? "jlong" : "jint";
        if (unsigned) {
            String cast = slot == 'j' ? "(unsigned long long)" : "(unsigned int)";
            this.line("stack[" + (before - 2) + "]." + slot + " = (" + type + ")(" + cast + "stack[" + (before - 2) + "]."
                    + slot + " " + operator + " (stack[" + (before - 1) + "].i & " + mask + "));");
        } else {
            this.line("stack[" + (before - 2) + "]." + slot + " = stack[" + (before - 2) + "]." + slot + " " + operator
                    + " (stack[" + (before - 1) + "].i & " + mask + ");");
        }
    }

    private void cmp(int before, char slot, boolean nanHigh) {
        String a = "stack[" + (before - 2) + "]." + slot;
        String b = "stack[" + (before - 1) + "]." + slot;
        String nan = nanHigh ? "1" : "-1";
        this.line("stack[" + (before - 2) + "].i = (" + a + " > " + b + ") ? 1 : ((" + a + " < " + b
                + ") ? -1 : ((" + a + " == " + b + ") ? 0 : " + nan + "));");
    }

    private void branch(String condition, AbstractInsnNode node) {
        this.line("if (" + condition + ") goto L" + this.indexes.get(((JumpInsnNode) node).label) + ";");
    }

    private String compare(int before, String operator) {
        return "stack[" + (before - 2) + "].i " + operator + " stack[" + (before - 1) + "].i";
    }

    private void emitTableSwitch(TableSwitchInsnNode node, int before) {
        this.out.append("    switch (stack[").append(before - 1).append("].i) {\n");
        for (int index = 0; index < node.labels.size(); index++) {
            this.out.append("        case ").append(node.min + index).append(": goto L")
                    .append(this.indexes.get(node.labels.get(index))).append(";\n");
        }
        this.out.append("        default: goto L").append(this.indexes.get(node.dflt)).append(";\n    }\n");
    }

    private void emitLookupSwitch(LookupSwitchInsnNode node, int before) {
        this.out.append("    switch (stack[").append(before - 1).append("].i) {\n");
        for (int index = 0; index < node.keys.size(); index++) {
            this.out.append("        case ").append(node.keys.get(index)).append(": goto L")
                    .append(this.indexes.get(node.labels.get(index))).append(";\n");
        }
        this.out.append("        default: goto L").append(this.indexes.get(node.dflt)).append(";\n    }\n");
    }

    private void emitStackManip(int opcode, int before, StackFlow.State state) {
        int consumed;
        int[] perm;
        int cat0 = state.category(0);
        switch (opcode) {
            case Opcodes.DUP:
                consumed = 1;
                perm = new int[]{0, 0};
                break;
            case Opcodes.DUP_X1:
                consumed = 2;
                perm = new int[]{1, 0, 1};
                break;
            case Opcodes.DUP_X2:
                if (state.category(1) == 2) {
                    consumed = 2;
                    perm = new int[]{1, 0, 1};
                } else {
                    consumed = 3;
                    perm = new int[]{2, 0, 1, 2};
                }
                break;
            case Opcodes.DUP2:
                if (cat0 == 2) {
                    consumed = 1;
                    perm = new int[]{0, 0};
                } else {
                    consumed = 2;
                    perm = new int[]{0, 1, 0, 1};
                }
                break;
            case Opcodes.DUP2_X1:
                if (cat0 == 2) {
                    consumed = 2;
                    perm = new int[]{1, 0, 1};
                } else {
                    consumed = 3;
                    perm = new int[]{1, 2, 0, 1, 2};
                }
                break;
            case Opcodes.DUP2_X2:
                if (cat0 == 2) {
                    if (state.category(1) == 2) {
                        consumed = 2;
                        perm = new int[]{1, 0, 1};
                    } else {
                        consumed = 3;
                        perm = new int[]{2, 0, 1, 2};
                    }
                } else if (state.category(2) == 2) {
                    consumed = 3;
                    perm = new int[]{1, 2, 0, 1, 2};
                } else {
                    consumed = 4;
                    perm = new int[]{2, 3, 0, 1, 2, 3};
                }
                break;
            default:
                consumed = 2;
                perm = new int[]{1, 0};
                break;
        }
        int base = before - consumed;
        StringBuilder block = new StringBuilder(128);
        block.append("    { jvalue _t[").append(consumed).append("];\n");
        for (int index = 0; index < consumed; index++) {
            block.append("      _t[").append(index).append("] = stack[").append(base + index).append("];\n");
        }
        for (int index = 0; index < perm.length; index++) {
            block.append("      stack[").append(base + index).append("] = _t[").append(perm[index]).append("];\n");
        }
        block.append("    }\n");
        this.out.append(block);
    }

    private static String memberOf(int opcode) {
        switch (opcode) {
            case Opcodes.ILOAD:
            case Opcodes.ISTORE:
                return "i";
            case Opcodes.LLOAD:
            case Opcodes.LSTORE:
                return "j";
            case Opcodes.FLOAD:
            case Opcodes.FSTORE:
                return "f";
            case Opcodes.DLOAD:
            case Opcodes.DSTORE:
                return "d";
            default:
                return "l";
        }
    }

    private static int loadOf(int storeOpcode) {
        switch (storeOpcode) {
            case Opcodes.ISTORE:
                return Opcodes.ILOAD;
            case Opcodes.LSTORE:
                return Opcodes.LLOAD;
            case Opcodes.FSTORE:
                return Opcodes.FLOAD;
            case Opcodes.DSTORE:
                return Opcodes.DLOAD;
            default:
                return Opcodes.ALOAD;
        }
    }

    private static String newArraySuffix(int operand) {
        switch (operand) {
            case Opcodes.T_BOOLEAN:
                return "Boolean";
            case Opcodes.T_CHAR:
                return "Char";
            case Opcodes.T_FLOAT:
                return "Float";
            case Opcodes.T_DOUBLE:
                return "Double";
            case Opcodes.T_BYTE:
                return "Byte";
            case Opcodes.T_SHORT:
                return "Short";
            case Opcodes.T_INT:
                return "Int";
            case Opcodes.T_LONG:
                return "Long";
            default:
                throw new IllegalArgumentException("Bad array operand " + operand);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
