package dev.jnic.templates;

import dev.jnic.handler.JvmOpcodeHandlers;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class Transpiler {
    private Transpiler() {
    }

    static String declaration(String owner, MethodNode method, boolean semicolon) {
        Descriptor descriptor = Descriptor.parse(method.desc);
        StringBuilder out = new StringBuilder(192);
        out.append("JNIEXPORT ")
                .append(jniType(descriptor.result))
                .append(" JNICALL ")
                .append(symbol(owner, method))
                .append("(JNIEnv *env, ")
                .append((method.access & Opcodes.ACC_STATIC) != 0 ? "jclass clazz" : "jobject self");
        for (int index = 0; index < descriptor.parameters.size(); ++index) {
            out.append(", ").append(jniType(descriptor.parameters.get(index))).append(" arg").append(index);
        }
        out.append(')');
        if (semicolon) {
            out.append(';');
        }
        return out.toString();
    }

    static String symbol(String owner, MethodNode method) {
        return "Java_" + mangle(owner) + "_" + mangle(method.name);
    }

    static String registrationEntry(String owner, MethodNode method) {
        return "    {\"" + cString(method.name) + "\", \"" + cString(method.desc)
                + "\", reinterpret_cast<void *>(&" + symbol(owner, method) + ")}";
    }

    static String registrationEntry(MethodNode method, int nativeIndex) {
        return "            {\"" + cString(method.name) + "\", \"" + cString(method.desc)
                + "\", (void *) &jnic_" + nativeIndex + "}";
    }

    static String loaderSymbol(String owner) {
        return "Java_" + mangle(owner) + "__00024jnicLoader";
    }

    static String translatedFunction(String functionName, MethodNode method) {
        Descriptor descriptor = Descriptor.parse(method.desc);
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        StringBuilder out = new StringBuilder(1024);
        out.append(jniType(descriptor.result))
                .append(" JNICALL ")
                .append(functionName)
                .append("(JNIEnv *env, jobject thisObj");
        for (int index = 0; index < descriptor.parameters.size(); ++index) {
            out.append(", ").append(jniType(descriptor.parameters.get(index))).append(" arg").append(index);
        }
        out.append(") {\n");
        int localCount = Math.max(method.maxLocals, initialLocalCount(descriptor, isStatic));
        int stackCount = Math.max(method.maxStack, 0);
        appendValues(out, "local", localCount);
        if (localCount > 0) {
            out.append('\n');
        }
        appendValues(out, "stack", stackCount);
        if (stackCount > 0) {
            out.append('\n');
        }
        appendValues(out, "temp", 4);
        out.append('\n');
        int local = 0;
        if (!isStatic) {
            out.append("    local0.l = thisObj;\n");
            local = 1;
        }
        for (int index = 0; index < descriptor.parameters.size(); ++index) {
            String parameter = descriptor.parameters.get(index);
            out.append("    local").append(local).append('.').append(jvalueMember(parameter))
                    .append(" = arg").append(index).append(";\n");
            local += slotWidth(parameter);
        }
        if (local > 0) {
            out.append('\n');
        }
        if (method.instructions != null) {
            int instructionIndex = 0;
            for (AbstractInsnNode instruction : method.instructions) {
                String emitted = JvmOpcodeHandlers.emit(instruction, instructionIndex++);
                if (emitted == null) {
                    throw new IllegalArgumentException(
                            "Unsupported JVM instruction " + instruction.getClass().getName()
                                    + " (opcode " + instruction.getOpcode() + ")");
                }
                out.append(emitted);
            }
        }
        String fallback = fallbackReturn(descriptor.result);
        if (!fallback.isEmpty()) {
            out.append("    ").append(fallback).append('\n');
        }
        return out.append("}\n").toString();
    }

    private static void appendValues(StringBuilder out, String prefix, int count) {
        for (int index = 0; index < count; ++index) {
            out.append("    jvalue ").append(prefix).append(index)
                    .append("; memset(&").append(prefix).append(index).append(", 0, sizeof(jvalue));\n");
        }
    }

    private static int initialLocalCount(Descriptor descriptor, boolean isStatic) {
        int count = isStatic ? 0 : 1;
        for (String parameter : descriptor.parameters) {
            count += slotWidth(parameter);
        }
        return count;
    }

    private static int slotWidth(String descriptor) {
        char type = descriptor.charAt(0);
        return type == 'J' || type == 'D' ? 2 : 1;
    }

    private static String jvalueMember(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'Z':
                return "z";
            case 'B':
                return "b";
            case 'C':
                return "c";
            case 'S':
                return "s";
            case 'I':
                return "i";
            case 'J':
                return "j";
            case 'F':
                return "f";
            case 'D':
                return "d";
            default:
                return "l";
        }
    }

    static String safeDefinition(String owner, MethodNode method, String recoveredFragment) {
        Descriptor descriptor = Descriptor.parse(method.desc);
        StringBuilder out = new StringBuilder(256);
        out.append(declaration(owner, method, false)).append(" {\n");
        if (looksLikeBody(recoveredFragment)) {
            String body = recoveredFragment.trim();
            if (body.startsWith("{") && body.endsWith("}")) {
                body = body.substring(1, body.length() - 1).trim();
            }
            for (String line : body.split("\\R", -1)) {
                out.append("    ").append(line).append('\n');
            }
        } else {
            out.append("    // Recovered source context: ").append(comment(recoveredFragment)).append("\n");
            String fallback = fallbackReturn(descriptor.result);
            if (!fallback.isEmpty()) {
                out.append("    ").append(fallback).append('\n');
            }
        }
        return out.append('}').toString();
    }

    private static boolean looksLikeBody(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return text.indexOf(';') >= 0 || text.startsWith("{") || text.startsWith("return ");
    }

    private static String comment(String value) {
        if (value == null) {
            return "<none>";
        }
        return value.replace("\\", "\\\\").replace("\r", " ").replace("\n", " ").replace("*/", "* /");
    }

    private static String fallbackReturn(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'V':
                return "";
            case 'Z':
                return "return JNI_FALSE;";
            case 'B':
            case 'C':
            case 'I':
            case 'J':
            case 'S':
                return "return 0;";
            case 'F':
                return "return 0.0f;";
            case 'D':
                return "return 0.0;";
            default:
                return "return NULL;";
        }
    }

    static String identifier(String value) {
        String mangled = mangle(value);
        return mangled.isEmpty() || Character.isDigit(mangled.charAt(0)) ? "_" + mangled : mangled;
    }

    static String mangle(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); ++index) {
            char ch = value.charAt(index);
            if (Character.isLetterOrDigit(ch)) {
                result.append(ch);
            } else if (ch == '_') {
                result.append("_1");
            } else if (ch == ';') {
                result.append("_2");
            } else if (ch == '[') {
                result.append("_3");
            } else if (ch == '/') {
                result.append('_');
            } else {
                result.append("_0").append(String.format("%04x", (int) ch));
            }
        }
        return result.toString();
    }

    private static String cString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jniType(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'V':
                return "void";
            case 'Z':
                return "jboolean";
            case 'B':
                return "jbyte";
            case 'C':
                return "jchar";
            case 'S':
                return "jshort";
            case 'I':
                return "jint";
            case 'J':
                return "jlong";
            case 'F':
                return "jfloat";
            case 'D':
                return "jdouble";
            case 'L':
                return "Ljava/lang/String;".equals(descriptor) ? "jstring" : "jobject";
            case '[':
                return arrayType(descriptor);
            default:
                throw new IllegalArgumentException("Bad JVM descriptor: " + descriptor);
        }
    }

    private static String arrayType(String descriptor) {
        if (descriptor.length() != 2) {
            return "jobjectArray";
        }
        switch (descriptor.charAt(1)) {
            case 'Z':
                return "jbooleanArray";
            case 'B':
                return "jbyteArray";
            case 'C':
                return "jcharArray";
            case 'S':
                return "jshortArray";
            case 'I':
                return "jintArray";
            case 'J':
                return "jlongArray";
            case 'F':
                return "jfloatArray";
            case 'D':
                return "jdoubleArray";
            default:
                return "jobjectArray";
        }
    }

    private static final class Descriptor {
        final List<String> parameters;
        final String result;

        Descriptor(List<String> parameters, String result) {
            this.parameters = parameters;
            this.result = result;
        }

        static Descriptor parse(String value) {
            if (value == null || value.length() < 3 || value.charAt(0) != '(') {
                throw new IllegalArgumentException("Bad method descriptor: " + value);
            }
            List<String> parameters = new ArrayList<>();
            int cursor = 1;
            while (cursor < value.length() && value.charAt(cursor) != ')') {
                int end = end(value, cursor);
                parameters.add(value.substring(cursor, end));
                cursor = end;
            }
            if (cursor >= value.length()) {
                throw new IllegalArgumentException("Bad method descriptor: " + value);
            }
            int resultStart = cursor + 1;
            int resultEnd = end(value, resultStart);
            if (resultEnd != value.length()) {
                throw new IllegalArgumentException("Trailing descriptor data: " + value);
            }
            return new Descriptor(parameters, value.substring(resultStart, resultEnd));
        }

        private static int end(String value, int start) {
            if (start >= value.length()) {
                throw new IllegalArgumentException("Truncated descriptor: " + value);
            }
            int cursor = start;
            while (value.charAt(cursor) == '[') {
                ++cursor;
            }
            if (value.charAt(cursor) == 'L') {
                int semicolon = value.indexOf(';', cursor);
                if (semicolon < 0) {
                    throw new IllegalArgumentException("Unterminated descriptor: " + value);
                }
                return semicolon + 1;
            }
            if ("VZBCSIJFD".indexOf(value.charAt(cursor)) < 0) {
                throw new IllegalArgumentException("Bad type descriptor: " + value);
            }
            return cursor + 1;
        }
    }
}
