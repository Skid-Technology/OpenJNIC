package dev.jnic.translate;

public final class CType {
    private CType() {
    }

    public static char slot(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
            case 'I':
                return 'i';
            case 'J':
                return 'j';
            case 'F':
                return 'f';
            case 'D':
                return 'd';
            default:
                return 'l';
        }
    }

    public static String declare(String descriptor) {
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
                throw new IllegalArgumentException("Bad descriptor: " + descriptor);
        }
    }

    public static String accessor(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'V':
                return "Void";
            case 'Z':
                return "Boolean";
            case 'B':
                return "Byte";
            case 'C':
                return "Char";
            case 'S':
                return "Short";
            case 'I':
                return "Int";
            case 'J':
                return "Long";
            case 'F':
                return "Float";
            case 'D':
                return "Double";
            default:
                return "Object";
        }
    }

    public static String arrayType(String descriptor) {
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

    public static String internalName(String descriptor) {
        if (descriptor == null) {
            return null;
        }
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        if (descriptor.startsWith("[")) {
            return descriptor;
        }
        return null;
    }

    public static String mangle(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
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

    public static String loaderSymbol(String internalName) {
        return "Java_" + mangle(internalName) + "__00024jnicLoader";
    }

    public static String defaultReturn(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'V':
                return "return;";
            case 'Z':
                return "return JNI_FALSE;";
            case 'B':
            case 'C':
            case 'S':
            case 'I':
            case 'J':
                return "return 0;";
            case 'F':
                return "return 0.0f;";
            case 'D':
                return "return 0.0;";
            default:
                return "return NULL;";
        }
    }
}
