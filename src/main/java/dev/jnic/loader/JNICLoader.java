package dev.jnic.loader;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class JNICLoader extends InputStream {
    public static ByteBuffer z;
    private String version = "3.7.0";
    private DataInputStream in;
    private LzDictionary lz;
    private ByteBufferRangeSource rc;
    private LzmaDecoder lzma;
    private int uncompressedSize = 0;
    private boolean isLZMAChunk = false;
    private boolean needDictReset = true;
    private boolean needProps = true;
    private boolean endReached = false;
    private IOException exception = null;
    private final byte[] tempBuf = new byte[1];

    public JNICLoader(InputStream in) {
        this(in, 0x1000000, null);
    }

    public JNICLoader(InputStream in, int dictSize, byte[] presetDict) {
        if (in == null) {
            throw new NullPointerException();
        }
        if (dictSize < 4096 || dictSize > 0x7FFFFFF0) {
            throw new IllegalArgumentException();
        }
        this.in = new DataInputStream(in);
        this.rc = new ByteBufferRangeSource();
        this.lz = new LzDictionary((dictSize + 15) & 0xFFFFFFF0, presetDict);
        if (presetDict != null && presetDict.length > 0) {
            this.needDictReset = false;
        }
    }

    @Override
    public int read() throws IOException {
        if (this.read(this.tempBuf, 0, 1) == -1) {
            return -1;
        }
        return this.tempBuf[0] & 0xFF;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        if (this.in == null) {
            throw new IOException();
        }
        if (this.exception != null) {
            throw this.exception;
        }
        if (this.endReached) {
            return -1;
        }
        try {
            int size = 0;
            while (len > 0) {
                if (this.uncompressedSize == 0) {
                    this.decodeChunkHeader();
                    if (this.endReached) {
                        return size == 0 ? -1 : size;
                    }
                }
                int copySizeMax = Math.min(this.uncompressedSize, len);
                if (!this.isLZMAChunk) {
                    int n = Math.min(this.lz.size - this.lz.pos, copySizeMax);
                    this.in.readFully(this.lz.buf, this.lz.pos, n);
                    this.lz.pos += n;
                    if (this.lz.full < this.lz.pos) {
                        this.lz.full = this.lz.pos;
                    }
                } else {
                    if (this.lz.size - this.lz.pos <= copySizeMax) {
                        this.lz.limit = this.lz.size;
                    } else {
                        this.lz.limit = this.lz.pos + copySizeMax;
                    }
                    this.lzma.decode();
                }
                int copied = this.lz.pos - this.lz.start;
                if (this.lz.pos == this.lz.size) {
                    this.lz.pos = 0;
                }
                System.arraycopy(this.lz.buf, this.lz.start, buf, off, copied);
                this.lz.start = this.lz.pos;
                off += copied;
                len -= copied;
                size += copied;
                this.uncompressedSize -= copied;
                if (this.uncompressedSize == 0) {
                    if (this.rc.pos != this.rc.buf.length || this.rc.code != 0 || this.lz.pendingLen > 0) {
                        throw new IOException();
                    }
                }
            }
            return size;
        } catch (IOException e) {
            this.exception = e;
            throw e;
        }
    }

    private void decodeChunkHeader() throws IOException {
        int control = this.in.readUnsignedByte();
        if (control == 0) {
            this.endReached = true;
            this.releaseResources();
            return;
        }
        if (control >= 224 || control == 1) {
            this.needProps = true;
            this.needDictReset = false;
            this.lz.start = 0;
            this.lz.pos = 0;
            this.lz.full = 0;
            this.lz.limit = 0;
            this.lz.buf[this.lz.size - 1] = 0;
        } else if (this.needDictReset) {
            throw new IOException();
        }
        if (control >= 128) {
            this.isLZMAChunk = true;
            this.uncompressedSize = (control & 0x1F) << 16;
            this.uncompressedSize += this.in.readUnsignedShort() + 1;
            int compressedSize = this.in.readUnsignedShort() + 1;
            if (control >= 192) {
                this.needProps = false;
                this.decodeProps();
            } else {
                if (this.needProps) {
                    throw new IOException();
                }
                if (control >= 160) {
                    this.lzma.reset();
                }
            }
            if (compressedSize < 5) {
                throw new IOException();
            }
            if (this.in.readUnsignedByte() != 0) {
                throw new IOException();
            }
            this.rc.code = this.in.readInt();
            this.rc.range = -1;
            int remaining = compressedSize - 5;
            this.rc.pos = this.rc.buf.length - remaining;
            this.in.readFully(this.rc.buf, this.rc.pos, remaining);
        } else {
            if (control > 2) {
                throw new IOException();
            }
            this.isLZMAChunk = false;
            this.uncompressedSize = this.in.readUnsignedShort() + 1;
        }
    }

    private void decodeProps() throws IOException {
        int props = this.in.readUnsignedByte();
        if (props > 224) {
            throw new IOException();
        }
        int pb = props / 45;
        props -= pb * 9 * 5;
        int lp = props / 9;
        int lc = props - lp * 9;
        if (lc + lp > 4) {
            throw new IOException();
        }
        this.lzma = new LzmaDecoder(this.lz, this.rc, lc, lp, pb);
    }

    @Override
    public int available() throws IOException {
        if (this.in == null) {
            throw new IOException("closed");
        }
        if (this.exception != null) {
            throw this.exception;
        }
        return this.isLZMAChunk ? this.uncompressedSize : Math.min(this.uncompressedSize, this.in.available());
    }

    private void releaseResources() {
        if (this.lz != null) {
            this.lz = null;
            this.rc = null;
        }
    }

    @Override
    public void close() throws IOException {
        if (this.in != null) {
            this.releaseResources();
            try {
                this.in.close();
            } finally {
                this.in = null;
            }
        }
    }

    public static void init() {
    }

    private static String platformKey(String os, String arch) {
        boolean x86 = arch.equals("x86_64") || arch.equals("amd64") || arch.equals("x64");
        boolean arm = arch.equals("aarch64") || arch.equals("arm64");
        if (os.contains("win") && x86) return "WINDOWS_X86_64";
        if (os.contains("win") && arm) return "WINDOWS_AARCH64";
        if (os.contains("mac") && x86) return "MACOS_X86_64";
        if (os.contains("mac") && arm) return "MACOS_AARCH64";
        if (os.contains("lin") && x86) return "LINUX_X86_64";
        if (os.contains("lin") && arm) return "LINUX_AARCH64";
        return null;
    }

    private static long[] readLayout(String platform) {
        try (InputStream layout = JNICLoader.class.getResourceAsStream("/dev/jnic/lib/layout.txt")) {
            if (layout == null) return null;
            String text = new String(layout.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 3 && parts[0].equals(platform)) {
                    return new long[]{Long.parseLong(parts[1]), Long.parseLong(parts[2])};
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }

    static {
        Stream<String> stream = ManagementFactory.getRuntimeMXBean().getInputArguments().stream();
        List<String> list = Arrays.asList("-javaagent", "-agentlib");
        Objects.requireNonNull(list);
        if (!stream.anyMatch(list::contains)) {
            z = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();
            long start = 0L;
            long end = 0L;
            String platform = platformKey(os, arch);
            long[] range = platform == null ? null : readLayout(platform);
            if (range != null) {
                start = range[0];
                end = range[1];
            }
            if (range == null && os.contains("lin") && (arch.equals("x86_64") || arch.equals("amd64"))) {
                start = 22947080L;
                end = 32608048L;
                z.putInt(-567463365);
                z.putInt(1401376760);
                z.putInt(997729761);
                z.putInt(1502854057);
                z.putInt(1456950269);
                z.putInt(2068781566);
                z.putInt(-2074230231);
                z.putInt(-877438771);
            }
            if (range == null && os.contains("mac") && arch.equals("aarch64")) {
                start = 16419456L;
                end = 22947080L;
                z.putInt(1014097172);
                z.putInt(-117690684);
                z.putInt(820061173);
                z.putInt(-1688620839);
                z.putInt(-1273992576);
                z.putInt(1914204887);
                z.putInt(-1152264584);
                z.putInt(-36053678);
            }
            if (range == null && os.contains("win") && (arch.equals("x86_64") || arch.equals("amd64"))) {
                start = 0L;
                end = 7558656L;
                z.putInt(-1114421519);
                z.putInt(457382690);
                z.putInt(1129719259);
                z.putInt(1518767796);
                z.putInt(1901767710);
                z.putInt(1310480474);
                z.putInt(1420822585);
                z.putInt(-1793329926);
            }
            if (range == null && os.contains("mac") && (arch.equals("x86_64") || arch.equals("amd64"))) {
                start = 7558656L;
                end = 16419456L;
                z.putInt(1314687260);
                z.putInt(-478689012);
                z.putInt(-2086494599);
                z.putInt(551956295);
                z.putInt(410610689);
                z.putInt(-1224114266);
                z.putInt(-1353435766);
                z.putInt(-1032801772);
            }
            if (start != end) {
                InputStream resourceStream = JNICLoader.class.getResourceAsStream("/dev/jnic/lib/3273ada3-3474-4e4e-9623-319038d7e4bb.dat");
                if (resourceStream != null) {
                    File file;
                    try {
                        file = File.createTempFile("lib", null);
                        file.deleteOnExit();
                        if (!file.exists()) {
                            throw new IOException();
                        }
                    } catch (IOException e) {
                        throw new UnsatisfiedLinkError("Failed to create temp file");
                    }
                    byte[] buffer = new byte[2048];
                    try (JNICLoader decoder = new JNICLoader(resourceStream);
                         FileOutputStream out = new FileOutputStream(file)) {
                        long pos = 0L;
                        while (pos < start) {
                            long skipped = decoder.skip(start - pos);
                            if (skipped <= 0L) {
                                throw new IOException("failed to skip: " + pos);
                            }
                            pos += skipped;
                        }
                        while (pos < end) {
                            int n = decoder.read(buffer, 0, (int) Math.min(buffer.length, end - pos));
                            if (n < 0) break;
                            out.write(buffer, 0, n);
                            pos += n;
                        }
                    } catch (IOException e) {
                        throw new UnsatisfiedLinkError("Failed to extract file: " + e.getMessage());
                    }
                    z.putInt(-1731935906);
                    z.putInt(-851835500);
                    z.putInt(-880934708);
                    System.load(file.getAbsolutePath());
                }
            }
        }
    }
}
