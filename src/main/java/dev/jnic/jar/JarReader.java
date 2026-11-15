package dev.jnic.jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;

public class JarReader {
    private final Collection<ClassReader> classReaders = new ArrayDeque<>();
    private final Map<String, byte[]> classBytes = new HashMap<>();

    public JarReader(String path) throws IOException {
        try (JarFile jarFile = new JarFile(new File(path))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (InputStream in = jarFile.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    this.readEntry(entry, bytes);
                }
            }
        }
    }

    private void readEntry(JarEntry entry, byte[] bytes) {
        String name = entry.getName();
        if (name.endsWith(".class")) {
            this.classBytes.put(name, bytes);
            this.classReaders.add(new ClassReader(bytes));
        }
    }

    public Collection<ClassReader> getClassReaders() {
        return this.classReaders;
    }

    public Map<String, byte[]> getClassBytes() {
        return this.classBytes;
    }
}
