package dev.jnic.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassCacheEntry {
    private static final String ATTR = "__attribute__((const))static ";

    private final AtomicInteger memberCounter = new AtomicInteger();
    private final int classIndex;
    private final String symbolName;
    private final String internalName;
    private final boolean hasLookup;
    private final Map<MemberDescriptor, String> fields = new LinkedHashMap<>();
    private final Map<MemberDescriptor, String> methods = new LinkedHashMap<>();

    ClassCacheEntry(int classIndex, String symbolName, String internalName, boolean hasLookup) {
        this.classIndex = classIndex;
        this.symbolName = symbolName;
        this.internalName = internalName;
        this.hasLookup = hasLookup;
    }

    String getFieldSymbol(MemberDescriptor descriptor) {
        return this.fields.get(descriptor);
    }

    String getSymbolName() {
        return this.symbolName;
    }

    String registerField(MemberDescriptor descriptor) {
        return this.fields.computeIfAbsent(descriptor, k -> this.symbolName + "id_" + this.memberCounter.getAndIncrement());
    }

    String registerMethod(MemberDescriptor descriptor) {
        return this.methods.computeIfAbsent(descriptor, k -> this.symbolName + "id_" + this.memberCounter.getAndIncrement());
    }

    public String emitDeclaration() {
        StringBuilder out = new StringBuilder();
        out.append(ATTR).append("jclass ").append(this.symbolName).append("(JNIEnv *env) {\n")
                .append("    static _Atomic jclass clazz;\n")
                .append("    jclass cached = atomic_load_explicit(&clazz, memory_order_relaxed);\n")
                .append("    if (__builtin_expect(cached != 0, 1)) return cached;\n")
                .append("    jclass loaded = (*env)->FindClass(env, \"").append(this.internalName).append("\");\n")
                .append("    loaded = (*env)->NewGlobalRef(env, loaded);\n")
                .append("    if (atomic_compare_exchange_strong_explicit(&clazz, &cached, loaded, memory_order_relaxed, memory_order_relaxed))\n")
                .append("        return loaded;\n")
                .append("    (*env)->DeleteGlobalRef(env, loaded);\n")
                .append("    return cached;\n")
                .append("}\n");
        if (this.hasLookup) {
            out.append(ATTR).append("jobject *").append(this.symbolName).append("l() {\n")
                    .append("    static jobject lookup; return &lookup;\n")
                    .append("}\n");
        }
        Map<Integer, Map.Entry<MemberDescriptor, Boolean>> ordered = new TreeMap<>();
        for (Map.Entry<MemberDescriptor, String> entry : this.fields.entrySet()) {
            ordered.put(parseSuffixIndex(entry.getValue()), Map.entry(entry.getKey(), true));
        }
        for (Map.Entry<MemberDescriptor, String> entry : this.methods.entrySet()) {
            ordered.put(parseSuffixIndex(entry.getValue()), Map.entry(entry.getKey(), false));
        }
        for (Map.Entry<Integer, Map.Entry<MemberDescriptor, Boolean>> item : ordered.entrySet()) {
            int idx = item.getKey();
            MemberDescriptor member = item.getValue().getKey();
            boolean isField = item.getValue().getValue();
            boolean isStatic = member.isStatic();
            String idType = isField ? "jfieldID" : "jmethodID";
            String api = isField
                    ? (isStatic ? "GetStaticFieldID" : "GetFieldID")
                    : (isStatic ? "GetStaticMethodID" : "GetMethodID");
            String fn = this.symbolName + "id_" + idx;
            out.append(ATTR).append(idType).append(" ").append(fn).append("(JNIEnv *env) {\n")
                    .append("    static _Atomic ").append(idType).append(" id;\n")
                    .append("    ").append(idType).append(" cached = atomic_load_explicit(&id, memory_order_relaxed);\n")
                    .append("    if (__builtin_expect(cached != 0, 1)) return cached;\n")
                    .append("    jclass clazz = ").append(this.symbolName).append("(env);\n")
                    .append("    cached = (*env)->").append(api).append("(env, clazz, \"")
                    .append(member.getName()).append("\", \"").append(member.getDescriptor()).append("\");\n")
                    .append("    atomic_store_explicit(&id, cached, memory_order_relaxed);\n")
                    .append("    return cached;\n")
                    .append("}\n");
        }
        return out.toString();
    }

    private static int parseSuffixIndex(String fn) {
        int pos = fn.lastIndexOf('_');
        if (pos < 0) return 0;
        try {
            return Integer.parseInt(fn.substring(pos + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
