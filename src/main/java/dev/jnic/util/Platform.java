package dev.jnic.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum Platform {
    WINDOWS_X86_64("windows", "x86_64", "amd64", "x86_64"),
    WINDOWS_AARCH64("windows", "aarch64", "aarch64", "arm64"),
    MACOS_X86_64("macos", "x86_64", "amd64", "x86_64"),
    MACOS_AARCH64("macos", "aarch64", "aarch64", "arm64"),
    LINUX_X86_64("linux", "x86_64", "amd64", "x86_64"),
    LINUX_AARCH64("linux", "aarch64", "aarch64", "arm64");

    private final String os;
    private final String arch;
    private final Set<String> aliases;

    Platform(String os, String arch, String... aliases) {
        this.os = os;
        this.arch = arch;
        this.aliases = new HashSet<>(Arrays.asList(aliases));
    }

    public String targetName() {
        return this.os + "-" + this.arch;
    }

    public Set<String> getAliases() {
        return this.aliases;
    }

    public String getOs() {
        return this.os;
    }

    public String getArch() {
        return this.arch;
    }
}
