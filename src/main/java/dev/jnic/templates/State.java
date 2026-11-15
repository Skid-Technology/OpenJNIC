package dev.jnic.templates;

import dev.jnic.cache.IndyRegistry;
import dev.jnic.cache.JniCacheRegistry;
import dev.jnic.cache.MemberKey;
import dev.jnic.cache.StringPoolEmitter;
import dev.jnic.crypto.ChaCha20Stream;
import dev.jnic.pipeline.Options;
import dev.jnic.type.JniTypeUtils;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

public class State {
    private final AtomicInteger methodCounter = new AtomicInteger();
    private final Map<MemberKey, String> methodSymbols = new HashMap<>();
    private final SecureRandom random;
    private final JniCacheRegistry cacheRegistry;
    private final IndyRegistry indyRegistry;
    private final StringPoolEmitter stringPool;
    private final Options options;
    private final String moduleName;
    private final String target;
    private String currentOwner = "";
    private String currentReturnJniType = "void";
    private int currentMaxTempCount = 0;
    private int currentExtraStackSlots = 0;

    public State(SecureRandom random, String moduleName, String target, Options options) {
        this.random = random != null ? random : new SecureRandom();
        ChaCha20Stream stream = ChaCha20Stream.create("expand 32-byte k", this.random);
        this.cacheRegistry = new JniCacheRegistry(stream);
        this.indyRegistry = new IndyRegistry();
        this.stringPool = new StringPoolEmitter(stream);
        this.moduleName = moduleName != null ? moduleName : "";
        this.target = target != null ? target : "";
        this.options = options != null ? options : Options.INSTANCE;
    }

    public String getModuleName() {
        return this.moduleName;
    }

    public String getTarget() {
        return this.target;
    }

    public Options getOptions() {
        return this.options;
    }

    public String registerMethodSymbol(MemberKey methodKey) {
        return this.methodSymbols.computeIfAbsent(methodKey, k -> "jnic_" + this.methodCounter.getAndIncrement());
    }

    public SecureRandom getRandom() {
        return this.random;
    }

    public JniCacheRegistry getCacheRegistry() {
        return this.cacheRegistry;
    }

    public IndyRegistry getIndyRegistry() {
        return this.indyRegistry;
    }

    public StringPoolEmitter getStringPool() {
        return this.stringPool;
    }

    public String cacheClass(String internalName) {
        return this.cacheRegistry.registerClass(internalName);
    }

    public String cacheField(String owner, String name, String desc, boolean isStatic) {
        return this.cacheRegistry.registerField(owner, name, desc, isStatic);
    }

    public String cacheMethod(String owner, String name, String desc, boolean isStatic) {
        return this.cacheRegistry.registerMethod(owner, name, desc, isStatic);
    }

    public String registerLookup(String internalName) {
        return this.cacheRegistry.registerLookup(internalName);
    }

    public String registerIndyHelper(InvokeDynamicInsnNode indy) {
        return this.indyRegistry.register(indy, this.currentOwner);
    }

    public void beginMethod(String ownerName, String returnJniType) {
        this.currentOwner = ownerName != null ? ownerName : "";
        this.currentReturnJniType = returnJniType != null ? returnJniType : "void";
        this.currentMaxTempCount = 0;
        this.currentExtraStackSlots = 0;
    }

    public String ownerName() {
        return this.currentOwner;
    }

    public String returnJniType() {
        return this.currentReturnJniType;
    }

    public void ensureTempCount(int count) {
        if (count > this.currentMaxTempCount) {
            this.currentMaxTempCount = count;
        }
    }

    public int maxTempCount() {
        return this.currentMaxTempCount;
    }

    public void ensureExtraStackSlots(int extra) {
        if (extra > this.currentExtraStackSlots) {
            this.currentExtraStackSlots = extra;
        }
    }

    public int extraStackSlots() {
        return this.currentExtraStackSlots;
    }

    public static String jniCallType(Type type) {
        return JniTypeUtils.jniCallType(type);
    }

    public String callStaticMethod(String typeName, String owner, String name, String desc, String... args) {
        String cVar = this.cacheClass(owner);
        String mVar = this.cacheMethod(owner, name, desc != null ? desc : "", true);
        StringBuilder sb = new StringBuilder();
        sb.append("(*env)->CallStatic").append(typeName).append("Method(env, ")
                .append(cVar).append("(env), ").append(mVar).append("(env)");
        if (args != null) {
            for (String arg : args) {
                sb.append(", ").append(arg);
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public String callVirtualMethod(String typeName, String objExpr, String owner, String name, String desc, String... args) {
        String mVar = this.cacheMethod(owner, name, desc != null ? desc : "", false);
        StringBuilder sb = new StringBuilder();
        sb.append("(*env)->Call").append(typeName).append("Method(env, ")
                .append(objExpr).append(", ").append(mVar).append("(env)");
        if (args != null) {
            for (String arg : args) {
                sb.append(", ").append(arg);
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public String getStaticField(String typeName, String owner, String name, String desc) {
        String cVar = this.cacheClass(owner);
        String fVar = this.cacheField(owner, name, desc, true);
        return "(*env)->GetStatic" + typeName + "Field(env, " + cVar + "(env), " + fVar + "(env))";
    }

    public String getField(String typeName, String objExpr, String owner, String name, String desc) {
        String fVar = this.cacheField(owner, name, desc, false);
        return "(*env)->Get" + typeName + "Field(env, " + objExpr + ", " + fVar + "(env))";
    }

    public String setStaticField(String typeName, String owner, String name, String desc, String valExpr) {
        String cVar = this.cacheClass(owner);
        String fVar = this.cacheField(owner, name, desc != null ? desc : "", true);
        return "(*env)->SetStatic" + typeName + "Field(env, " + cVar + "(env), " + fVar + "(env), " + valExpr + ")";
    }

    public String isInstanceOf(String owner, Object valExpr) {
        String cVar = this.cacheClass(owner);
        return "(*env)->IsInstanceOf(env, " + valExpr + ", " + cVar + "(env))";
    }

    public String newObject(String owner, String... args) {
        String cVar = this.cacheClass(owner);
        String mVar = this.cacheMethod(owner, "<init>", "()V", false);
        StringBuilder sb = new StringBuilder();
        sb.append("(*env)->NewObject(env, ").append(cVar).append("(env), ").append(mVar).append("(env)");
        if (args != null) {
            for (String arg : args) {
                sb.append(", ").append(arg);
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
