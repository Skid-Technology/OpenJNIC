package dev.jnic.translate;

import dev.jnic.pipeline.Options;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class LoaderResources {
    private static final String PREFIX = "dev/jnic/loader/";
    private static final String LOADER_CLASS = "JNICLoader";
    private static final String CLINIT = "<clinit>";
    private static final String STREAM = "java/util/stream/Stream";
    private static final String ANY_MATCH = "anyMatch";

    private static final String[] CLASSES = {
        "BitTreeDecoder",
        "ByteBufferRangeSource",
        "DistanceDecoder",
        "DistanceParams",
        "JNICLoader",
        "LengthDecoder",
        "LengthModel",
        "LiteralModel",
        "LzDictionary",
        "LzmaDecoder",
        "LzmaState",
        "LzmaStateBox",
        "RangeDecoder",
    };

    private LoaderResources() {
    }

    public static Map<String, byte[]> load() throws IOException {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        for (String simple : CLASSES) {
            String path = PREFIX + simple + ".class";
            try (InputStream in = LoaderResources.class.getResourceAsStream("/" + path)) {
                if (in == null) {
                    throw new IOException("loader resource not found on classpath: " + path);
                }
                byte[] bytes = in.readAllBytes();
                if (LOADER_CLASS.equals(simple) && !Options.INSTANCE.isAntiDebugEnabled()) {
                    bytes = stripAntiDebugGuard(bytes);
                }
                resources.put(path, bytes);
            }
        }
        return resources;
    }

    private static byte[] stripAntiDebugGuard(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!CLINIT.equals(method.name)) {
                continue;
            }
            AbstractInsnNode guard = guardBranch(method.instructions);
            if (guard == null) {
                break;
            }
            ArrayList<AbstractInsnNode> preamble = new ArrayList<>();
            for (AbstractInsnNode current = method.instructions.getFirst(); current != null; current = current.getNext()) {
                preamble.add(current);
                if (current == guard) {
                    break;
                }
            }
            for (AbstractInsnNode instruction : preamble) {
                method.instructions.remove(instruction);
            }
            break;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static AbstractInsnNode guardBranch(InsnList instructions) {
        for (AbstractInsnNode current = instructions.getFirst(); current != null; current = current.getNext()) {
            if (current.getOpcode() != Opcodes.IFNE) {
                continue;
            }
            AbstractInsnNode previous = current.getPrevious();
            if (previous instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) previous;
                if (STREAM.equals(call.owner) && ANY_MATCH.equals(call.name)) {
                    return current;
                }
            }
        }
        return null;
    }
}
