package dev.jnic.pipeline;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root(name = "options")
public class Options {
    public static final Options INSTANCE = new Options();

    @Element(name = "stringObf", required = false)
    private boolean stringObf;
    @Element(name = "flowObf", required = false)
    private boolean flowObf;
    @Element(name = "nativeIntegrity", required = false)
    private boolean nativeIntegrity;
    @Element(name = "jarIntegrity", required = false)
    private boolean jarIntegrity;
    @Element(name = "antiDebug", required = false)
    private boolean antiDebug;
    @Element(name = "useIntrinsics", required = false)
    private boolean useIntrinsics;
    @Element(name = "fastCompile", required = false)
    private boolean fastCompile;

    public Options() {
    }

    public Options(Options source) {
        this.stringObf = source.stringObf;
        this.flowObf = source.flowObf;
        this.nativeIntegrity = source.nativeIntegrity;
        this.jarIntegrity = source.jarIntegrity;
        this.antiDebug = source.antiDebug;
        this.useIntrinsics = source.useIntrinsics;
        this.fastCompile = source.fastCompile;
    }

    public boolean isStringObfuscationEnabled() {
        return this.stringObf;
    }

    public boolean isFlowObfuscationEnabled() {
        return this.flowObf;
    }

    public boolean isNativeIntegrityEnabled() {
        return this.nativeIntegrity;
    }

    public boolean isJarIntegrityEnabled() {
        return this.jarIntegrity;
    }

    public boolean isAntiDebugEnabled() {
        return this.antiDebug;
    }

    public boolean isUseIntrinsicsEnabled() {
        return this.useIntrinsics;
    }

    public boolean isFastCompileEnabled() {
        return this.fastCompile;
    }

    public void setStringObfuscationEnabled(boolean value) {
        this.stringObf = value;
    }

    public void setFlowObfuscationEnabled(boolean value) {
        this.flowObf = value;
    }

    public void setNativeIntegrityEnabled(boolean value) {
        this.nativeIntegrity = value;
    }

    public void setJarIntegrityEnabled(boolean value) {
        this.jarIntegrity = value;
    }

    public void setAntiDebugEnabled(boolean value) {
        this.antiDebug = value;
    }

    public void setUseIntrinsicsEnabled(boolean value) {
        this.useIntrinsics = value;
    }

    public void setFastCompileEnabled(boolean value) {
        this.fastCompile = value;
    }

    @Override
    public String toString() {
        return "Options{stringObf=" + this.stringObf
                + ", flowObf=" + this.flowObf
                + ", nativeIntegrity=" + this.nativeIntegrity
                + ", jarIntegrity=" + this.jarIntegrity
                + ", antiDebug=" + this.antiDebug
                + ", useIntrinsics=" + this.useIntrinsics
                + ", fastCompile=" + this.fastCompile + "}";
    }
}
