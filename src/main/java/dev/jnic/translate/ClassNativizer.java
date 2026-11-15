package dev.jnic.translate;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ClassNativizer {
    private static final int ACC_PRIVATE = Opcodes.ACC_PRIVATE;
    private static final int ACC_STATIC = Opcodes.ACC_STATIC;
    private static final int ACC_NATIVE = Opcodes.ACC_NATIVE;
    private static final int ACC_ABSTRACT = Opcodes.ACC_ABSTRACT;
    private static final int ACC_INTERFACE = Opcodes.ACC_INTERFACE;
    private static final int ACC_ANNOTATION = Opcodes.ACC_ANNOTATION;
    private static final int ACC_MODULE = Opcodes.ACC_MODULE;

    private static final String LOADER = "dev/jnic/loader/JNICLoader";
    private static final String LOADER_PREFIX = "dev/jnic/loader/";
    private static final String CLINIT = "<clinit>";
    private static final String NATIVE_CLINIT = "$jnicClinit";
    private static final String LOADER_METHOD = "$jnicLoader";

    public static final class Registration {
        public final String name;
        public final String descriptor;
        public final String functionName;
        public final boolean isStatic;
        public final String cSource;

        Registration(String name, String descriptor, String functionName, boolean isStatic, String cSource) {
            this.name = name;
            this.descriptor = descriptor;
            this.functionName = functionName;
            this.isStatic = isStatic;
            this.cSource = cSource;
        }
    }

    public static final class Result {
        public final byte[] bytes;
        public final String internalName;
        public final List<Registration> registrations;

        Result(byte[] bytes, String internalName, List<Registration> registrations) {
            this.bytes = bytes;
            this.internalName = internalName;
            this.registrations = registrations;
        }

        public boolean nativized() {
            return !this.registrations.isEmpty();
        }
    }

    private ClassNativizer() {
    }

    public static Result nativize(String entryName, byte[] bytes, int classIndex) {
        if (entryName.startsWith(LOADER_PREFIX) || entryName.endsWith("module-info.class")
                || entryName.endsWith("package-info.class")) {
            return new Result(bytes, null, new ArrayList<>());
        }
        ClassNode node = new ClassNode();
        try {
            new ClassReader(bytes).accept(node, 0);
        } catch (RuntimeException malformed) {
            return new Result(bytes, null, new ArrayList<>());
        }
        if ((node.access & (ACC_INTERFACE | ACC_ANNOTATION | ACC_MODULE)) != 0) {
            return new Result(bytes, node.name, new ArrayList<>());
        }
        if (node.name == null || node.name.startsWith(LOADER_PREFIX)) {
            return new Result(bytes, node.name, new ArrayList<>());
        }

        List<Registration> registrations = new ArrayList<>();
        int sequence = 0;
        boolean clinitNativized = false;

        for (MethodNode method : node.methods) {
            if ("<init>".equals(method.name)) {
                continue;
            }
            if (!NativeMethodTranslator.canTranslate(method)) {
                continue;
            }
            String functionName = "jnic_" + classIndex + "_" + sequence;
            String cSource;
            try {
                cSource = NativeMethodTranslator.translate(functionName, node.name, method);
            } catch (RuntimeException unsupported) {
                continue;
            }
            sequence++;
            boolean isStatic = (method.access & ACC_STATIC) != 0;
            if (CLINIT.equals(method.name)) {
                method.name = NATIVE_CLINIT;
                clinitNativized = true;
            }
            registrations.add(new Registration(method.name, method.desc, functionName, isStatic, cSource));
            method.access |= ACC_NATIVE;
            method.access &= ~ACC_ABSTRACT;
            method.instructions.clear();
            if (method.tryCatchBlocks != null) {
                method.tryCatchBlocks.clear();
            }
            method.localVariables = null;
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            method.maxStack = 0;
            method.maxLocals = 0;
        }
        if (registrations.isEmpty()) {
            return new Result(bytes, node.name, registrations);
        }

        boolean hasLoader = false;
        for (MethodNode method : node.methods) {
            if (LOADER_METHOD.equals(method.name) && "()V".equals(method.desc)) {
                hasLoader = true;
                break;
            }
        }
        if (!hasLoader) {
            MethodNode loader = new MethodNode(
                    ACC_PRIVATE | ACC_STATIC | ACC_NATIVE, LOADER_METHOD, "()V", null, null);
            node.methods.add(loader);
        }

        if (clinitNativized) {
            MethodNode clinit = new MethodNode(ACC_STATIC, CLINIT, "()V", null, null);
            InsnList body = clinit.instructions;
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LOADER, "init", "()V", false));
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, LOADER_METHOD, "()V", false));
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, NATIVE_CLINIT, "()V", false));
            body.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(clinit);
        } else {
            MethodNode clinit = null;
            for (MethodNode method : node.methods) {
                if (CLINIT.equals(method.name)) {
                    clinit = method;
                    break;
                }
            }
            if (clinit == null) {
                clinit = new MethodNode(ACC_STATIC, CLINIT, "()V", null, null);
                clinit.instructions.add(new InsnNode(Opcodes.RETURN));
                node.methods.add(clinit);
            }
            InsnList prefix = new InsnList();
            prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LOADER, "init", "()V", false));
            prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, LOADER_METHOD, "()V", false));
            clinit.instructions.insert(prefix);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return new Result(writer.toByteArray(), node.name, registrations);
    }
}
