package dev.jnic.cache;

import dev.jnic.templates.State;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

public class IndyEntry {
    private final InvokeDynamicInsnNode insn;
    private final String ownerName;
    private String id;
    private String symbolName;

    public IndyEntry(InvokeDynamicInsnNode insn, String ownerName) {
        this.insn = insn;
        this.ownerName = ownerName;
    }

    public InvokeDynamicInsnNode getInsn() {
        return this.insn;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public String getId() {
        return this.id != null ? this.id : "0";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbolName() {
        return this.symbolName != null ? this.symbolName : "";
    }

    public void setSymbolName(String symbolName) {
        this.symbolName = symbolName;
    }

    public String emitHelper(State ctx) {
        String idValue = getId();
        String fnName = "indy_" + idValue;
        StringBuilder sb = new StringBuilder();
        sb.append("static jobject ").append(fnName).append("(JNIEnv *env) {\n");
        sb.append("    static _Atomic jobject callSite;\n");
        sb.append("    jobject cached = atomic_load_explicit(&callSite, memory_order_relaxed);\n");
        sb.append("    if (__builtin_expect(cached != 0, 1)) return cached;\n");
        sb.append("    return nullptr;\n");
        sb.append("}\n");
        return sb.toString();
    }
}
