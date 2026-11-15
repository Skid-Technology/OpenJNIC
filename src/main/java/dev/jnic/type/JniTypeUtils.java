package dev.jnic.type;

import dev.jnic.templates.State;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

public class JniTypeUtils {
    public static char slotChar(Type type) {
        if (type == null) {
            return 'l';
        }
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return 'z';
            case Type.CHAR:
                return 'c';
            case Type.BYTE:
                return 'b';
            case Type.SHORT:
                return 's';
            case Type.INT:
                return 'i';
            case Type.FLOAT:
                return 'f';
            case Type.LONG:
                return 'j';
            case Type.DOUBLE:
                return 'd';
            default:
                return 'l';
        }
    }

    public static String jniCallType(Type type) {
        if (type == null) {
            return "Object";
        }
        switch (type.getSort()) {
            case Type.VOID:
                return "Void";
            case Type.BOOLEAN:
                return "Boolean";
            case Type.CHAR:
                return "Char";
            case Type.BYTE:
                return "Byte";
            case Type.SHORT:
                return "Short";
            case Type.INT:
                return "Int";
            case Type.FLOAT:
                return "Float";
            case Type.LONG:
                return "Long";
            case Type.DOUBLE:
                return "Double";
            default:
                return "Object";
        }
    }

    public static String jniArrayType(int atype) {
        switch (atype) {
            case 4:
                return "jbooleanArray";
            case 5:
                return "jcharArray";
            case 6:
                return "jfloatArray";
            case 7:
                return "jdoubleArray";
            case 8:
                return "jbyteArray";
            case 9:
                return "jshortArray";
            case 10:
                return "jintArray";
            case 11:
                return "jlongArray";
            default:
                return "jarray";
        }
    }

    public static String newArrayPrimitiveName(int atype) {
        switch (atype) {
            case 4:
                return "Boolean";
            case 5:
                return "Char";
            case 6:
                return "Float";
            case 7:
                return "Double";
            case 8:
                return "Byte";
            case 9:
                return "Short";
            case 10:
                return "Int";
            case 11:
                return "Long";
            default:
                throw new IllegalArgumentException("Unknown array type: " + atype);
        }
    }

    public static String jniType(Type type) {
        if (type == null) {
            return "jobject";
        }
        switch (type.getSort()) {
            case Type.VOID:
                return "void";
            case Type.BOOLEAN:
                return "jboolean";
            case Type.CHAR:
                return "jchar";
            case Type.BYTE:
                return "jbyte";
            case Type.SHORT:
                return "jshort";
            case Type.INT:
                return "jint";
            case Type.FLOAT:
                return "jfloat";
            case Type.LONG:
                return "jlong";
            case Type.DOUBLE:
                return "jdouble";
            case Type.ARRAY: {
                Type elem = type.getElementType();
                if (elem != null && type.getDimensions() == 1) {
                    switch (elem.getSort()) {
                        case Type.BOOLEAN:
                            return "jbooleanArray";
                        case Type.CHAR:
                            return "jcharArray";
                        case Type.BYTE:
                            return "jbyteArray";
                        case Type.SHORT:
                            return "jshortArray";
                        case Type.INT:
                            return "jintArray";
                        case Type.FLOAT:
                            return "jfloatArray";
                        case Type.LONG:
                            return "jlongArray";
                        case Type.DOUBLE:
                            return "jdoubleArray";
                        default:
                            return "jobjectArray";
                    }
                }
                return "jobjectArray";
            }
            case Type.OBJECT: {
                String internal = type.getInternalName();
                if ("java/lang/String".equals(internal)) {
                    return "jstring";
                }
                if ("java/lang/Class".equals(internal)) {
                    return "jclass";
                }
                if ("java/lang/Throwable".equals(internal)) {
                    return "jthrowable";
                }
                return "jobject";
            }
            default:
                return "jobject";
        }
    }

    public static String emitStringLiteral(int stackIdx, String str) {
        if (str == null) {
            return "    stack" + stackIdx + ".l = nullptr;\n";
        }
        int len = str.length();
        StringBuilder sb = new StringBuilder();
        sb.append("    stack").append(stackIdx).append(".l = (*env)->NewString(env, (unsigned short[]) {");
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append((int) str.charAt(i));
        }
        if (len > 0) {
            sb.append(", ");
        }
        sb.append("0}, ").append(len).append(");\n");
        return sb.toString();
    }

    public static String emitLdcConstant(State ctx, Object cst, int stackIdx, int line, String ownerName) {
        if (cst == null) {
            return "    stack" + stackIdx + ".l = nullptr;\n";
        }
        if (cst instanceof Integer) {
            int val = (Integer) cst;
            if (val == Integer.MIN_VALUE) {
                return "    stack" + stackIdx + ".i = (jint)0x80000000;\n";
            }
            return "    stack" + stackIdx + ".i = " + val + ";\n";
        }
        if (cst instanceof Float) {
            float val = (Float) cst;
            if (Float.isNaN(val)) {
                return "    stack" + stackIdx + ".f = 0.0f / 0.0f;\n";
            }
            if (Float.isInfinite(val)) {
                return "    stack" + stackIdx + ".f = " + (val > 0 ? "1.0f / 0.0f" : "-1.0f / 0.0f") + ";\n";
            }
            return "    stack" + stackIdx + ".f = " + val + "f;\n";
        }
        if (cst instanceof Long) {
            long val = (Long) cst;
            if (val == Long.MIN_VALUE) {
                return "    stack" + stackIdx + ".j = (jlong)0x8000000000000000ULL;\n";
            }
            return "    stack" + stackIdx + ".j = " + val + "LL;\n";
        }
        if (cst instanceof Double) {
            double val = (Double) cst;
            if (Double.isNaN(val)) {
                return "    stack" + stackIdx + ".d = 0.0 / 0.0;\n";
            }
            if (Double.isInfinite(val)) {
                return "    stack" + stackIdx + ".d = " + (val > 0 ? "1.0 / 0.0" : "-1.0 / 0.0") + ";\n";
            }
            return "    stack" + stackIdx + ".d = " + val + ";\n";
        }
        if (cst instanceof String) {
            return emitStringLiteral(stackIdx, (String) cst);
        }
        if (cst instanceof Type) {
            Type t = (Type) cst;
            int sort = t.getSort();
            if (sort == Type.OBJECT || sort == Type.ARRAY) {
                String clsName = t.getInternalName();
                String cVar = ctx != null ? ctx.cacheClass(clsName) : "nullptr";
                return "    stack" + stackIdx + ".l = " + cVar + "(env);\n";
            }
            if (sort == Type.METHOD) {
                String desc = t.getDescriptor();
                String mtCls = ctx != null ? ctx.cacheClass("java/lang/invoke/MethodType") : "c_mt";
                String fromDesc = ctx != null
                        ? ctx.cacheMethod("java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", true)
                        : "c_mt_id";
                StringBuilder sb = new StringBuilder();
                sb.append(emitStringLiteral(stackIdx, desc));
                sb.append("    stack").append(stackIdx).append(".l = (*env)->CallStaticObjectMethod(env, ")
                        .append(mtCls).append("(env), ").append(fromDesc).append("(env), stack").append(stackIdx).append(".l, nullptr);\n");
                return sb.toString();
            }
        }
        if (cst instanceof Handle || cst instanceof ConstantDynamic) {
            return "    stack" + stackIdx + ".l = nullptr;\n";
        }
        return "    stack" + stackIdx + ".l = nullptr;\n";
    }

    public static Type constantType(Object value) {
        if (value instanceof Integer) return Type.INT_TYPE;
        if (value instanceof Float) return Type.FLOAT_TYPE;
        if (value instanceof Long) return Type.LONG_TYPE;
        if (value instanceof Double) return Type.DOUBLE_TYPE;
        if (value instanceof String) return Type.getType("Ljava/lang/String;");
        if (value instanceof Type) {
            Type t = (Type) value;
            int s = t.getSort();
            if (s == Type.OBJECT || s == Type.ARRAY) return Type.getType("Ljava/lang/Class;");
            if (s == Type.METHOD) return Type.getType("Ljava/lang/invoke/MethodType;");
        }
        if (value instanceof Handle) return Type.getType("Ljava/lang/invoke/MethodHandle;");
        if (value instanceof ConstantDynamic) return Type.getType(((ConstantDynamic) value).getDescriptor());
        return Type.getType("Ljava/lang/Object;");
    }
}
