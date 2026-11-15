package dev.jnic.pipeline;

import dev.jnic.translate.CType;
import dev.jnic.translate.ClassNativizer;
import dev.jnic.translate.LoaderResources;
import dev.jnic.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.tukaani.xz.ArrayCache;
import org.tukaani.xz.FinishableOutputStream;
import org.tukaani.xz.FinishableWrapperOutputStream;
import org.tukaani.xz.LZMA2Options;

public final class Rewriter {
    private static final String PAYLOAD_PATH = "dev/jnic/lib/3273ada3-3474-4e4e-9623-319038d7e4bb.dat";
    private static final String LAYOUT_PATH = "dev/jnic/lib/layout.txt";
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    private Rewriter() {
    }

    public static void rewrite(Path input, Path output, Path config, Path compiler, List<String> targets) throws IOException, InterruptedException {
        Path sourcesDir = output.resolveSibling("jnic-sources");
        Log.debug("Temporary files will be stored in " + sourcesDir);
        Manifest manifest;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<ClassUnit> units = new ArrayList<>();
        int classIndex = 0;
        try (JarFile jar = new JarFile(input.toFile())) {
            manifest = jar.getManifest();
            for (JarEntry entry : Collections.list(jar.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || MANIFEST_PATH.equalsIgnoreCase(name)) {
                    continue;
                }
                byte[] bytes = readAll(jar.getInputStream(entry));
                if (name.endsWith(".class")) {
                    ClassNativizer.Result result = ClassNativizer.nativize(name, bytes, classIndex++);
                    entries.put(name, result.bytes);
                    if (!result.nativized() || result.internalName == null) {
                        continue;
                    }
                    Log.debug("Processing class <" + result.internalName + ">");
                    for (ClassNativizer.Registration registration : result.registrations) {
                        Log.debug(" -> " + displayName(registration) + registration.descriptor);
                    }
                    units.add(new ClassUnit(result.internalName, result.registrations));
                    continue;
                }
                entries.put(name, bytes);
            }
        }
        entries.putAll(LoaderResources.load());
        String source = emitSource(units);
        Files.createDirectories(sourcesDir);
        Path cFile = sourcesDir.resolve("jnic.c");
        Files.write(cFile, source.getBytes(StandardCharsets.UTF_8));
        Log.info("Saving translated sources");
        Log.info("Compiling sources");
        List<byte[]> libraries = new ArrayList<>();
        StringBuilder layout = new StringBuilder();
        long offset = 0L;
        long total = 0L;
        for (String target : targets) {
            Log.debug(" -> " + target);
            byte[] library = compile(compiler, cFile, sourcesDir, target);
            layout.append(target).append(' ').append(offset).append(' ').append(offset + library.length).append('\n');
            offset += library.length;
            total += library.length;
            libraries.add(library);
        }
        Log.info("Finished compilation");
        Log.info("Compressing native libraries (total size: " + kib(total) + ") (preset: 6)");
        byte[] payload = compress(concat(libraries));
        Log.info("Compressed size: " + kib(payload.length) + " (ratio: " + ((float) payload.length / (float) total) + ")");
        Log.info("Preparing output JAR file");
        for (ClassUnit unit : units) {
            for (ClassNativizer.Registration registration : unit.registrations) {
                Log.debug("Deleted " + unit.internalName + "." + displayName(registration) + registration.descriptor);
            }
        }
        Log.info("Writing to file " + output);
        repackage(output, entries, payload, layout.toString(), manifest);
    }

    private static String displayName(ClassNativizer.Registration registration) {
        return "$jnicClinit".equals(registration.name) ? "<clinit>" : registration.name;
    }

    private static String kib(long bytes) {
        return String.format(Locale.US, "%.1f KiB", (double) bytes / 1024.0);
    }

    private static String emitSource(List<ClassUnit> units) {
        StringBuilder out = new StringBuilder(65536);
        out.append("/* Auto-generated JNIC implementation file */\n")
                .append("#include <jni.h>\n")
                .append("#include <stdint.h>\n")
                .append("#include <stdlib.h>\n")
                .append("#include <string.h>\n")
                .append("#include <math.h>\n\n")
                .append("static jfloat jnic_f(uint32_t bits) { jfloat v; memcpy(&v, &bits, sizeof(v)); return v; }\n")
                .append("static jdouble jnic_d(uint64_t bits) { jdouble v; memcpy(&v, &bits, sizeof(v)); return v; }\n\n");
        for (ClassUnit unit : units) {
            for (ClassNativizer.Registration registration : unit.registrations) {
                out.append(registration.cSource).append('\n');
            }
        }
        for (ClassUnit unit : units) {
            out.append("JNIEXPORT void JNICALL ")
                    .append(CType.loaderSymbol(unit.internalName))
                    .append("(JNIEnv *env, jclass clazz) {\n")
                    .append("    static JNINativeMethod table[] = {\n");
            for (ClassNativizer.Registration registration : unit.registrations) {
                out.append("        {\"")
                        .append(escape(registration.name))
                        .append("\", \"")
                        .append(escape(registration.descriptor))
                        .append("\", (void *) &")
                        .append(registration.functionName)
                        .append("},\n");
            }
            out.append("    };\n")
                    .append("    (*env)->RegisterNatives(env, (*env)->FindClass(env, \"")
                    .append(escape(unit.internalName))
                    .append("\"), table, ")
                    .append(unit.registrations.size())
                    .append(");\n")
                    .append("}\n\n");
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static byte[] compile(Path compiler, Path cFile, Path sourcesDir, String target) throws IOException, InterruptedException {
        ZigTarget zig = ZigTarget.of(target);
        Path library = sourcesDir.resolve(target.toLowerCase(Locale.ROOT) + zig.extension);
        List<String> command = new ArrayList<>();
        command.add(compiler.toString());
        command.add("cc");
        command.add("-shared");
        command.add("-target");
        command.add(zig.triple);
        command.add(Options.INSTANCE.isFastCompileEnabled() ? "-Oz" : "-O2");
        command.add("-Wl,-s");
        command.add("-fPIC");
        for (String include : jniIncludes()) {
            command.add("-I");
            command.add(include);
        }
        command.add(cFile.toString());
        command.add("-o");
        command.add(library.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException("zig cc failed for " + target + ":\n" + log);
        }
        return Files.readAllBytes(library);
    }

    private static List<String> jniIncludes() {
        List<String> includes = new ArrayList<>();
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getProperty("java.home");
        }
        if (javaHome != null && !javaHome.isBlank()) {
            Path base = Path.of(javaHome, "include");
            includes.add(base.toString());
            includes.add(base.resolve("win32").toString());
            includes.add(base.resolve("linux").toString());
            includes.add(base.resolve("darwin").toString());
        }
        return includes;
    }

    private static byte[] concat(List<byte[]> parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int cursor = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, cursor, part.length);
            cursor += part.length;
        }
        return out;
    }

    private static byte[] compress(byte[] raw) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream(raw.length / 2 + 64);
        LZMA2Options options = new LZMA2Options(6);
        FinishableOutputStream out = options.getOutputStream(
                new FinishableWrapperOutputStream(sink),
                ArrayCache.getDefaultCache());
        out.write(raw);
        out.finish();
        return sink.toByteArray();
    }

    private static void repackage(Path output, Map<String, byte[]> entries, byte[] payload, String layout, Manifest manifest) throws IOException {
        try (OutputStream file = Files.newOutputStream(output);
             JarOutputStream jar = manifest != null ? new JarOutputStream(file, manifest) : new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (entry.getKey().equals(PAYLOAD_PATH) || entry.getKey().equals(LAYOUT_PATH)) {
                    continue;
                }
                writeEntry(jar, entry.getKey(), entry.getValue());
            }
            writeEntry(jar, PAYLOAD_PATH, payload);
            writeEntry(jar, LAYOUT_PATH, layout.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeEntry(JarOutputStream jar, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        jar.putNextEntry(entry);
        jar.write(bytes);
        jar.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            return stream.readAllBytes();
        }
    }

    private static final class ClassUnit {
        final String internalName;
        final List<ClassNativizer.Registration> registrations;

        ClassUnit(String internalName, List<ClassNativizer.Registration> registrations) {
            this.internalName = internalName;
            this.registrations = registrations;
        }
    }

    private enum ZigTarget {
        WINDOWS_X86_64("x86_64-windows-gnu", ".dll"),
        WINDOWS_AARCH64("aarch64-windows-gnu", ".dll"),
        MACOS_X86_64("x86_64-macos", ".dylib"),
        MACOS_AARCH64("aarch64-macos", ".dylib"),
        LINUX_X86_64("x86_64-linux-gnu", ".so"),
        LINUX_AARCH64("aarch64-linux-gnu", ".so");

        final String triple;
        final String extension;

        ZigTarget(String triple, String extension) {
            this.triple = triple;
            this.extension = extension;
        }

        static ZigTarget of(String target) {
            try {
                return ZigTarget.valueOf(target.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Unsupported target: " + target);
            }
        }
    }
}
