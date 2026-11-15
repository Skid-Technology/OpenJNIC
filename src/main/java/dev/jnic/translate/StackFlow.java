package dev.jnic.translate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public final class StackFlow {
    public static final class State {
        final int[] categories;

        State(int[] categories) {
            this.categories = categories;
        }

        public int height() {
            return this.categories.length;
        }

        public int category(int offsetFromTop) {
            int index = this.categories.length - 1 - offsetFromTop;
            return index >= 0 && index < this.categories.length ? this.categories[index] : 1;
        }

        State with(int[] next) {
            return new State(next);
        }
    }

    private StackFlow() {
    }

    public static State[] analyze(MethodNode method) {
        int size = method.instructions.size();
        State[] states = new State[size];
        if (size == 0) {
            return states;
        }

        Map<AbstractInsnNode, Integer> indexes = new IdentityHashMap<>();
        for (int index = 0; index < size; index++) {
            indexes.put(method.instructions.get(index), index);
        }

        Deque<Integer> pending = new ArrayDeque<>();
        seed(states, pending, 0, new State(new int[0]));
        if (method.tryCatchBlocks != null) {
            for (TryCatchBlockNode block : method.tryCatchBlocks) {
                if (block.handler == null) {
                    continue;
                }
                Integer index = indexes.get(block.handler);
                if (index != null) {
                    seed(states, pending, index, new State(new int[]{1}));
                }
            }
        }

        while (!pending.isEmpty()) {
            int index = pending.removeFirst();
            State input = states[index];
            if (input == null) {
                continue;
            }
            AbstractInsnNode node = method.instructions.get(index);
            if (node.getOpcode() < 0) {
                merge(states, pending, index + 1, input);
                continue;
            }
            State output = step(node, input);
            for (int next : successors(method, node, index, size)) {
                merge(states, pending, next, output);
            }
        }
        return states;
    }

    private static void seed(State[] states, Deque<Integer> pending, int index, State state) {
        if (index < 0 || index >= states.length) {
            return;
        }
        State existing = states[index];
        State merged = existing == null ? state : mergeStates(existing, state);
        if (existing == null || merged != existing) {
            states[index] = merged;
            pending.addLast(index);
        }
    }

    private static void merge(State[] states, Deque<Integer> pending, int index, State incoming) {
        if (index < 0 || index >= states.length) {
            return;
        }
        State existing = states[index];
        if (existing == null) {
            states[index] = incoming;
            pending.addLast(index);
            return;
        }
        State merged = mergeStates(existing, incoming);
        if (merged != existing) {
            states[index] = merged;
            pending.addLast(index);
        }
    }

    private static State mergeStates(State left, State right) {
        if (left.categories.length != right.categories.length) {
            return left;
        }
        int[] merged = null;
        for (int index = 0; index < left.categories.length; index++) {
            int a = left.categories[index];
            int b = right.categories[index];
            int value = (a == 2 || b == 2) ? 2 : 1;
            if (value != left.categories[index]) {
                if (merged == null) {
                    merged = left.categories.clone();
                }
                merged[index] = value;
            }
        }
        return merged == null ? left : new State(merged);
    }

    private static List<Integer> successors(MethodNode method, AbstractInsnNode node, int index, int size) {
        List<Integer> out = new ArrayList<>(2);
        int op = node.getOpcode();
        switch (op) {
            case Opcodes.GOTO:
            case Opcodes.JSR:
                out.add(indexOf(method, ((JumpInsnNode) node).label));
                return out;
            case Opcodes.TABLESWITCH: {
                TableSwitchInsnNode table = (TableSwitchInsnNode) node;
                out.add(indexOf(method, table.dflt));
                for (LabelNode label : table.labels) {
                    out.add(indexOf(method, label));
                }
                return out;
            }
            case Opcodes.LOOKUPSWITCH: {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) node;
                out.add(indexOf(method, lookup.dflt));
                for (LabelNode label : lookup.labels) {
                    out.add(indexOf(method, label));
                }
                return out;
            }
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ATHROW:
            case Opcodes.RET:
                return out;
            default:
                break;
        }
        if (isConditionalBranch(op)) {
            out.add(indexOf(method, ((JumpInsnNode) node).label));
        }
        if (index + 1 < size) {
            out.add(index + 1);
        }
        return out;
    }

    private static int indexOf(MethodNode method, AbstractInsnNode node) {
        int size = method.instructions.size();
        for (int index = 0; index < size; index++) {
            if (method.instructions.get(index) == node) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isConditionalBranch(int op) {
        return (op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE)
                || op == Opcodes.IFNULL
                || op == Opcodes.IFNONNULL;
    }

    public static State step(AbstractInsnNode node, State input) {
        int op = node.getOpcode();
        List<Integer> stack = new ArrayList<>();
        for (int category : input.categories) {
            stack.add(category);
        }

        switch (op) {
            case Opcodes.NOP:
                break;
            case Opcodes.ACONST_NULL:
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                stack.add(1);
                break;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                stack.add(2);
                break;
            case Opcodes.LDC: {
                Object constant = ((LdcInsnNode) node).cst;
                stack.add(category(constant instanceof Long || constant instanceof Double ? "J" : "I"));
                break;
            }
            case Opcodes.ILOAD:
            case Opcodes.FLOAD:
            case Opcodes.ALOAD:
                stack.add(1);
                break;
            case Opcodes.LLOAD:
            case Opcodes.DLOAD:
                stack.add(2);
                break;
            case Opcodes.ISTORE:
            case Opcodes.FSTORE:
            case Opcodes.ASTORE:
            case Opcodes.LSTORE:
            case Opcodes.DSTORE:
                pop(stack, 1);
                break;
            case Opcodes.IALOAD:
            case Opcodes.FALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD:
                pop(stack, 2);
                stack.add(1);
                break;
            case Opcodes.LALOAD:
            case Opcodes.DALOAD:
                pop(stack, 2);
                stack.add(2);
                break;
            case Opcodes.IASTORE:
            case Opcodes.LASTORE:
            case Opcodes.FASTORE:
            case Opcodes.DASTORE:
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.SASTORE:
                pop(stack, 3);
                break;
            case Opcodes.POP:
                pop(stack, 1);
                break;
            case Opcodes.POP2:
                if (stack.isEmpty()) {
                    break;
                }
                if (stack.get(stack.size() - 1) == 2) {
                    pop(stack, 1);
                } else {
                    pop(stack, 2);
                }
                break;
            case Opcodes.DUP:
                stack.add(top(stack));
                break;
            case Opcodes.DUP_X1: {
                int v1 = popValue(stack);
                int v2 = popValue(stack);
                stack.add(v1);
                stack.add(v2);
                stack.add(v1);
                break;
            }
            case Opcodes.DUP_X2: {
                int v1 = popValue(stack);
                if (top(stack) == 2) {
                    int v2 = popValue(stack);
                    stack.add(v1);
                    stack.add(v2);
                    stack.add(v1);
                } else {
                    int v2 = popValue(stack);
                    int v3 = popValue(stack);
                    stack.add(v1);
                    stack.add(v3);
                    stack.add(v2);
                    stack.add(v1);
                }
                break;
            }
            case Opcodes.DUP2: {
                if (top(stack) == 2) {
                    int v1 = popValue(stack);
                    stack.add(v1);
                    stack.add(v1);
                } else {
                    int v1 = popValue(stack);
                    int v2 = popValue(stack);
                    stack.add(v2);
                    stack.add(v1);
                    stack.add(v2);
                    stack.add(v1);
                }
                break;
            }
            case Opcodes.DUP2_X1: {
                if (top(stack) == 2) {
                    int v1 = popValue(stack);
                    int v2 = popValue(stack);
                    stack.add(v1);
                    stack.add(v2);
                    stack.add(v1);
                } else {
                    int v1 = popValue(stack);
                    int v2 = popValue(stack);
                    int v3 = popValue(stack);
                    stack.add(v2);
                    stack.add(v1);
                    stack.add(v3);
                    stack.add(v2);
                    stack.add(v1);
                }
                break;
            }
            case Opcodes.DUP2_X2: {
                int v1 = popValue(stack);
                if (v1 == 2) {
                    if (top(stack) == 2) {
                        int v2 = popValue(stack);
                        stack.add(v1);
                        stack.add(v2);
                        stack.add(v1);
                    } else {
                        int v2 = popValue(stack);
                        int v3 = popValue(stack);
                        stack.add(v1);
                        stack.add(v3);
                        stack.add(v2);
                        stack.add(v1);
                    }
                } else {
                    int v2 = popValue(stack);
                    if (top(stack) == 2) {
                        int v3 = popValue(stack);
                        stack.add(v2);
                        stack.add(v1);
                        stack.add(v3);
                        stack.add(v2);
                        stack.add(v1);
                    } else {
                        int v3 = popValue(stack);
                        int v4 = popValue(stack);
                        stack.add(v2);
                        stack.add(v1);
                        stack.add(v4);
                        stack.add(v3);
                        stack.add(v2);
                        stack.add(v1);
                    }
                }
                break;
            }
            case Opcodes.SWAP: {
                int v1 = popValue(stack);
                int v2 = popValue(stack);
                stack.add(v1);
                stack.add(v2);
                break;
            }
            case Opcodes.IADD:
            case Opcodes.ISUB:
            case Opcodes.IMUL:
            case Opcodes.IDIV:
            case Opcodes.IREM:
            case Opcodes.ISHL:
            case Opcodes.ISHR:
            case Opcodes.IUSHR:
            case Opcodes.IAND:
            case Opcodes.IOR:
            case Opcodes.IXOR:
            case Opcodes.FADD:
            case Opcodes.FSUB:
            case Opcodes.FMUL:
            case Opcodes.FDIV:
            case Opcodes.FREM:
            case Opcodes.LCMP:
            case Opcodes.FCMPL:
            case Opcodes.FCMPG:
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:
                pop(stack, 2);
                stack.add(1);
                break;
            case Opcodes.LADD:
            case Opcodes.LSUB:
            case Opcodes.LMUL:
            case Opcodes.LDIV:
            case Opcodes.LREM:
            case Opcodes.LSHL:
            case Opcodes.LSHR:
            case Opcodes.LUSHR:
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
            case Opcodes.DADD:
            case Opcodes.DSUB:
            case Opcodes.DMUL:
            case Opcodes.DDIV:
            case Opcodes.DREM:
                pop(stack, 2);
                stack.add(2);
                break;
            case Opcodes.INEG:
            case Opcodes.FNEG:
                pop(stack, 1);
                stack.add(1);
                break;
            case Opcodes.LNEG:
            case Opcodes.DNEG:
                pop(stack, 1);
                stack.add(2);
                break;
            case Opcodes.IINC:
                break;
            case Opcodes.I2L:
            case Opcodes.I2D:
            case Opcodes.F2L:
            case Opcodes.F2D:
            case Opcodes.D2L:
            case Opcodes.L2D:
                pop(stack, 1);
                stack.add(2);
                break;
            case Opcodes.I2F:
            case Opcodes.L2I:
            case Opcodes.L2F:
            case Opcodes.F2I:
            case Opcodes.D2I:
            case Opcodes.D2F:
            case Opcodes.I2B:
            case Opcodes.I2C:
            case Opcodes.I2S:
                pop(stack, 1);
                stack.add(1);
                break;
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                pop(stack, 1);
                break;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
                pop(stack, 2);
                break;
            case Opcodes.GOTO:
                break;
            case Opcodes.JSR:
                stack.add(1);
                break;
            case Opcodes.RET:
                break;
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
                pop(stack, 1);
                break;
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
                pop(stack, 1);
                break;
            case Opcodes.RETURN:
                break;
            case Opcodes.GETSTATIC:
                stack.add(category(((org.objectweb.asm.tree.FieldInsnNode) node).desc));
                break;
            case Opcodes.PUTSTATIC:
                pop(stack, 1);
                break;
            case Opcodes.GETFIELD:
                pop(stack, 1);
                stack.add(category(((org.objectweb.asm.tree.FieldInsnNode) node).desc));
                break;
            case Opcodes.PUTFIELD:
                pop(stack, 2);
                break;
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEINTERFACE:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEDYNAMIC: {
                String descriptor = op == Opcodes.INVOKEDYNAMIC
                        ? ((InvokeDynamicInsnNode) node).desc
                        : ((MethodInsnNode) node).desc;
                int arguments = Type.getArgumentTypes(descriptor).length;
                pop(stack, arguments + (op == Opcodes.INVOKESTATIC || op == Opcodes.INVOKEDYNAMIC ? 0 : 1));
                String result = Type.getReturnType(descriptor).getDescriptor();
                if (!"V".equals(result)) {
                    stack.add(category(result));
                }
                break;
            }
            case Opcodes.NEW:
                stack.add(1);
                break;
            case Opcodes.NEWARRAY:
            case Opcodes.ANEWARRAY:
                pop(stack, 1);
                stack.add(1);
                break;
            case Opcodes.ARRAYLENGTH:
                pop(stack, 1);
                stack.add(1);
                break;
            case Opcodes.CHECKCAST:
            case Opcodes.INSTANCEOF:
                break;
            case Opcodes.ATHROW:
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                pop(stack, 1);
                break;
            case Opcodes.MULTIANEWARRAY:
                pop(stack, ((MultiANewArrayInsnNode) node).dims);
                stack.add(1);
                break;
            default:
                throw new IllegalStateException("Unsupported opcode " + op + " in stack analysis");
        }

        int[] categories = new int[stack.size()];
        for (int index = 0; index < categories.length; index++) {
            categories[index] = stack.get(index);
        }
        return new State(categories);
    }

    static int category(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return 1;
        }
        char first = descriptor.charAt(0);
        return first == 'J' || first == 'D' ? 2 : 1;
    }

    private static int top(List<Integer> stack) {
        return stack.isEmpty() ? 1 : stack.get(stack.size() - 1);
    }

    private static int popValue(List<Integer> stack) {
        return stack.isEmpty() ? 1 : stack.remove(stack.size() - 1);
    }

    private static void pop(List<Integer> stack, int count) {
        for (int index = 0; index < count && !stack.isEmpty(); index++) {
            stack.remove(stack.size() - 1);
        }
    }
}
