package dev.jnic;

import dev.jnic.pipeline.Options;
import dev.jnic.pipeline.Rewriter;
import dev.jnic.util.Log;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class JnicMain {
    private static final String USAGE =
            "java -jar OpenJNIC-3.7.0.jar <inputJAR> <outputJAR> <configFile> [-debug]";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("JNIC Java Obfuscator 3.7.0");
        System.out.println(" ~ (c) +Vincent Tang 2020-2023");
        System.out.println();
        System.out.println();

        if (args == null || (args.length != 3 && args.length != 4)) {
            System.err.println("Incorrect number of arguments");
            System.err.println(USAGE);
            return;
        }
        if (args.length == 4 && !"-debug".equals(args[3])) {
            System.err.println("Unknown option: " + args[3]);
            System.err.println(USAGE);
            return;
        }
        Log.setDebug(args.length == 4);

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        Path config = Paths.get(args[2]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
            System.err.println("Input JAR is not readable: " + input);
            return;
        }
        if (!Files.isRegularFile(config) || !Files.isReadable(config)) {
            System.err.println("Configuration file is not readable: " + config);
            return;
        }
        if (input.equals(output)) {
            System.err.println("Input and output JAR must be different files");
            return;
        }

        Path outputParent = output.getParent();
        if (outputParent != null && !Files.isDirectory(outputParent)) {
            System.err.println("Output directory does not exist: " + outputParent);
            return;
        }

        try {
            Log.info("Licence: Skid Solutions");
            Path compiler = findCompiler();
            if (compiler == null) {
                System.err.println("No usable compiler found.");
                return;
            }
            Log.info("Found compiler: " + compiler);
            Log.info("Reading input jar " + input);
            Log.info("Reading configuration file " + config);
            Log.info("Analysing classes...");
            CliInspection inspection = inspect(input, config);
            Log.info("Translating " + inspection.methodCount + " methods in "
                    + inspection.selectedClassCount + " classes");
            backupExisting(output);
            Rewriter.rewrite(input, output, config, compiler, inspection.targets);
            Log.info("Terminating normally.");
        } catch (Exception failure) {
            System.err.println("Failed to inspect input: " + failure.getMessage());
            if (args.length == 4) {
                failure.printStackTrace(System.err);
            }
        }
    }

    private static void backupExisting(Path output) throws IOException {
        if (!Files.exists(output)) {
            return;
        }
        Path backup = output.resolveSibling(output.getFileName() + ".BACKUP");
        Files.deleteIfExists(backup);
        Files.move(output, backup);
        Log.warn(output + " already exists, renamed to " + backup);
    }

    private static CliInspection inspect(Path input, Path config) throws Exception {
        int classes = 0;
        int resources = 0;
        int selectedClasses = 0;
        int methods = 0;
        try (JarFile jar = new JarFile(input.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().endsWith(".class")) {
                    classes++;
                    int selected = countTranslatableMethods(jar, entry);
                    if (selected > 0) {
                        selectedClasses++;
                    }
                    methods += selected;
                } else {
                    resources++;
                }
            }
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element root = factory.newDocumentBuilder().parse(config.toFile()).getDocumentElement();
        if (!"jnic".equals(root.getTagName())) {
            throw new IllegalArgumentException("configuration root must be <jnic>");
        }
        ArrayList<String> targets = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("target");
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            String value = node.getTextContent().trim();
            if (!value.isEmpty()) {
                targets.add(value);
            }
        }
        if (targets.isEmpty()) {
            targets.add(platformTarget());
        }

        Options options = Options.INSTANCE;
        Element optionsElement = firstChild(root, "options");
        if (optionsElement != null) {
            options.setStringObfuscationEnabled(booleanElement(optionsElement, "stringObf", false));
            options.setFlowObfuscationEnabled(booleanElement(optionsElement, "flowObf", false));
            options.setNativeIntegrityEnabled(booleanElement(optionsElement, "nativeIntegrity", false));
            options.setJarIntegrityEnabled(booleanElement(optionsElement, "jarIntegrity", false));
            options.setAntiDebugEnabled(booleanElement(optionsElement, "antiDebug", false));
            options.setUseIntrinsicsEnabled(booleanElement(optionsElement, "useIntrinsics", false));
            options.setFastCompileEnabled(booleanElement(optionsElement, "fastCompile", false));
        }

        int generatedMethods = methods == 0 ? 0 : 1;
        return new CliInspection(classes, resources, classes, methods + generatedMethods, targets);
    }

    private static Element firstChild(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static boolean booleanElement(Element parent, String name, boolean fallback) {
        Element element = firstChild(parent, name);
        if (element == null) {
            return fallback;
        }
        String value = element.getTextContent().trim();
        if (value.isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }

    private static int countTranslatableMethods(JarFile jar, JarEntry entry) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(jar.getInputStream(entry)))) {
            if (in.readInt() != 0xCAFEBABE) {
                throw new IOException("invalid class: " + entry.getName());
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int poolSize = in.readUnsignedShort();
            String[] utf8 = new String[poolSize];
            for (int index = 1; index < poolSize; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1:
                        utf8[index] = in.readUTF();
                        break;
                    case 3:
                    case 4:
                        in.skipBytes(4);
                        break;
                    case 5:
                    case 6:
                        in.skipBytes(8);
                        index++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        in.skipBytes(2);
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        in.skipBytes(4);
                        break;
                    case 15:
                        in.skipBytes(3);
                        break;
                    default:
                        throw new IOException("unknown constant-pool tag " + tag + " in " + entry.getName());
                }
            }
            in.skipBytes(6);
            int interfaces = in.readUnsignedShort();
            in.skipBytes(interfaces * 2);
            skipMembers(in);
            int methodCount = in.readUnsignedShort();
            int selected = 0;
            for (int index = 0; index < methodCount; index++) {
                int access = in.readUnsignedShort();
                String name = utf8[in.readUnsignedShort()];
                in.readUnsignedShort();
                int attributes = in.readUnsignedShort();
                boolean concrete = (access & (0x0100 | 0x0400)) == 0;
                if (concrete && !"<init>".equals(name) && !"<clinit>".equals(name)) {
                    selected++;
                }
                skipAttributes(in, attributes);
            }
            return selected;
        }
    }

    private static void skipMembers(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            in.skipBytes(6);
            skipAttributes(in, in.readUnsignedShort());
        }
    }

    private static void skipAttributes(DataInputStream in, int count) throws IOException {
        for (int index = 0; index < count; index++) {
            in.readUnsignedShort();
            long length = Integer.toUnsignedLong(in.readInt());
            while (length > 0) {
                int skipped = in.skipBytes((int) Math.min(length, 1 << 20));
                if (skipped <= 0) {
                    throw new IOException("truncated class attribute");
                }
                length -= skipped;
            }
        }
    }

    private static Path findCompiler() {
        String configured = System.getenv("JNIC_ZIG");
        if (configured != null && !configured.isBlank()) {
            Path candidate = Paths.get(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (Path directory = current; directory != null; directory = directory.getParent()) {
            Path bundled = directory.resolve("zig-x86_64-windows-0.17.1")
                    .resolve(isWindows() ? "zig.exe" : "zig");
            if (Files.isRegularFile(bundled)) {
                return bundled;
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String item : path.split(File.pathSeparator)) {
                if (item.isBlank()) {
                    continue;
                }
                Path candidate = Paths.get(item).resolve(isWindows() ? "zig.exe" : "zig");
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private static String platformTarget() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String cpu = arch.contains("aarch64") || arch.contains("arm64") ? "AARCH64" : "X86_64";
        if (os.contains("win")) {
            return "WINDOWS_" + cpu;
        }
        if (os.contains("mac")) {
            return "MACOS_" + cpu;
        }
        return "LINUX_" + cpu;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static final class CliInspection {
        final int classCount;
        final int resourceCount;
        final int selectedClassCount;
        final int methodCount;
        final ArrayList<String> targets;

        CliInspection(int classCount, int resourceCount, int selectedClassCount,
                      int methodCount, ArrayList<String> targets) {
            this.classCount = classCount;
            this.resourceCount = resourceCount;
            this.selectedClassCount = selectedClassCount;
            this.methodCount = methodCount;
            this.targets = targets;
        }
    }
}
