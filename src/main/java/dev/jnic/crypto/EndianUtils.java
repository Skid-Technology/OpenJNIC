package dev.jnic.crypto;

public abstract class EndianUtils {
    public static short readLittleEndianShort(byte[] src, int offset) {
        return (short) ((src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8));
    }

    public static int readLittleEndianInt(byte[] src, int offset) {
        return (src[offset] & 0xFF)
                | ((src[offset + 1] & 0xFF) << 8)
                | ((src[offset + 2] & 0xFF) << 16)
                | ((src[offset + 3] & 0xFF) << 24);
    }

    public static void readLittleEndianInts(byte[] src, int offset, int[] dst) {
        for (int i = 0; i < dst.length; ++i) {
            dst[i] = readLittleEndianInt(src, offset + i * 4);
        }
    }

    public static void readBigEndianInts(byte[] src, int srcOff, int[] dst, int dstOff, int count) {
        for (int i = 0; i < count; ++i) {
            int pos = srcOff + i * 4;
            dst[dstOff + i] = ((src[pos] & 0xFF) << 24)
                    | ((src[pos + 1] & 0xFF) << 16)
                    | ((src[pos + 2] & 0xFF) << 8)
                    | (src[pos + 3] & 0xFF);
        }
    }

    public static byte[] toLittleEndianBytes(int value) {
        byte[] dst = new byte[4];
        writeLittleEndianInt(value, dst, 0);
        return dst;
    }

    public static void writeLittleEndianInt(int value, byte[] dst, int offset) {
        dst[offset] = (byte) value;
        dst[offset + 1] = (byte) (value >>> 8);
        dst[offset + 2] = (byte) (value >>> 16);
        dst[offset + 3] = (byte) (value >>> 24);
    }

    public static byte[] toLittleEndianBytes(int[] values) {
        byte[] dst = new byte[values.length * 4];
        writeLittleEndianInts(values, dst, 0);
        return dst;
    }

    public static void writeLittleEndianInts(int[] src, byte[] dst, int offset) {
        for (int i = 0; i < src.length; ++i) {
            writeLittleEndianInt(src[i], dst, offset + i * 4);
        }
    }

    public static void writeBigEndianInts(int[] src, int srcOff, int count, byte[] dst, int dstOff) {
        for (int i = 0; i < count; ++i) {
            int pos = dstOff + i * 4;
            int value = src[srcOff + i];
            dst[pos] = (byte) (value >>> 24);
            dst[pos + 1] = (byte) (value >>> 16);
            dst[pos + 2] = (byte) (value >>> 8);
            dst[pos + 3] = (byte) value;
        }
    }

    public static long readLittleEndianLong(byte[] src, int offset) {
        return ((long) src[offset] & 0xFFL)
                | (((long) src[offset + 1] & 0xFFL) << 8)
                | (((long) src[offset + 2] & 0xFFL) << 16)
                | (((long) src[offset + 3] & 0xFFL) << 24)
                | (((long) src[offset + 4] & 0xFFL) << 32)
                | (((long) src[offset + 5] & 0xFFL) << 40)
                | (((long) src[offset + 6] & 0xFFL) << 48)
                | (((long) src[offset + 7] & 0xFFL) << 56);
    }

    public static void readBigEndianLongs(byte[] src, int offset, long[] dst) {
        for (int i = 0; i < dst.length; ++i) {
            int pos = offset + i * 8;
            dst[i] = (((long) src[pos] & 0xFFL) << 56)
                    | (((long) src[pos + 1] & 0xFFL) << 48)
                    | (((long) src[pos + 2] & 0xFFL) << 40)
                    | (((long) src[pos + 3] & 0xFFL) << 32)
                    | (((long) src[pos + 4] & 0xFFL) << 24)
                    | (((long) src[pos + 5] & 0xFFL) << 16)
                    | (((long) src[pos + 6] & 0xFFL) << 8)
                    | ((long) src[pos + 7] & 0xFFL);
        }
    }

    public static void readLittleEndianLongs(byte[] src, int srcOff, long[] dst, int dstOff, int count) {
        for (int i = 0; i < count; ++i) {
            dst[dstOff + i] = readLittleEndianLong(src, srcOff + i * 8);
        }
    }

    public static byte[] toBigEndianBytes(long value) {
        byte[] dst = new byte[8];
        writeBigEndianLong(value, dst, 0);
        return dst;
    }

    public static void writeBigEndianLong(long value, byte[] dst, int offset) {
        dst[offset] = (byte) (value >>> 56);
        dst[offset + 1] = (byte) (value >>> 48);
        dst[offset + 2] = (byte) (value >>> 40);
        dst[offset + 3] = (byte) (value >>> 32);
        dst[offset + 4] = (byte) (value >>> 24);
        dst[offset + 5] = (byte) (value >>> 16);
        dst[offset + 6] = (byte) (value >>> 8);
        dst[offset + 7] = (byte) value;
    }

    public static byte[] toBigEndianBytes(long[] values) {
        byte[] dst = new byte[values.length * 8];
        writeBigEndianLongs(values, dst, 0);
        return dst;
    }

    public static void writeLittleEndianLongs(long[] src, byte[] dst, int offset) {
        for (int i = 0; i < src.length; ++i) {
            writeLittleEndianLong(src[i], dst, offset + i * 8);
        }
    }

    public static void writeLittleEndianLongs(long[] src, int srcOff, int count, byte[] dst, int dstOff) {
        for (int i = 0; i < count; ++i) {
            writeLittleEndianLong(src[srcOff + i], dst, dstOff + i * 8);
        }
    }

    public static void writeLittleEndianLong(long value, byte[] dst, int offset, int byteCount) {
        for (int i = 0; i < byteCount; ++i) {
            dst[offset + i] = (byte) (value >>> (i * 8));
        }
    }

    public static short readBigEndianShort(byte[] src, int offset) {
        return (short) (((src[offset] & 0xFF) << 8) | (src[offset + 1] & 0xFF));
    }

    public static int readBigEndianInt(byte[] src, int offset) {
        return ((src[offset] & 0xFF) << 24)
                | ((src[offset + 1] & 0xFF) << 16)
                | ((src[offset + 2] & 0xFF) << 8)
                | (src[offset + 3] & 0xFF);
    }

    public static void readBigEndianInts(byte[] src, int offset, int[] dst) {
        for (int i = 0; i < dst.length; ++i) {
            dst[i] = readBigEndianInt(src, offset + i * 4);
        }
    }

    public static void readLittleEndianInts(byte[] src, int srcOff, int[] dst, int dstOff, int count) {
        for (int i = 0; i < count; ++i) {
            dst[dstOff + i] = readLittleEndianInt(src, srcOff + i * 4);
        }
    }

    public static int[] readLittleEndianInts(byte[] src, int offset, int byteLength) {
        int[] dst = new int[byteLength / 4];
        for (int i = 0; i < dst.length; ++i) {
            dst[i] = readLittleEndianInt(src, offset + i * 4);
        }
        return dst;
    }

    public static byte[] toLittleEndianBytes(short value) {
        return new byte[]{(byte) value, (byte) (value >>> 8)};
    }

    public static void writeLittleEndianShort(short value, byte[] dst, int offset) {
        dst[offset] = (byte) value;
        dst[offset + 1] = (byte) (value >>> 8);
    }

    public static byte[] toBigEndianBytes(short value) {
        return new byte[]{(byte) (value >>> 8), (byte) value};
    }

    public static void writeBigEndianShort(short value, byte[] dst, int offset) {
        dst[offset] = (byte) (value >>> 8);
        dst[offset + 1] = (byte) value;
    }

    public static byte[] toBigEndianBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    public static void writeBigEndianInt(int value, byte[] dst, int offset) {
        dst[offset] = (byte) (value >>> 24);
        dst[offset + 1] = (byte) (value >>> 16);
        dst[offset + 2] = (byte) (value >>> 8);
        dst[offset + 3] = (byte) value;
    }

    public static byte[] toBigEndianBytes(int[] values) {
        byte[] dst = new byte[values.length * 4];
        writeBigEndianInts(values, dst, 0);
        return dst;
    }

    public static void writeBigEndianInts(int[] src, byte[] dst, int offset) {
        writeBigEndianInts(src, 0, src.length, dst, offset);
    }

    public static void writeLittleEndianInts(int[] src, int srcOff, int count, byte[] dst, int dstOff) {
        for (int i = 0; i < count; ++i) {
            writeLittleEndianInt(src[srcOff + i], dst, dstOff + i * 4);
        }
    }

    public static long readBigEndianLong(byte[] src, int offset) {
        return (((long) src[offset] & 0xFFL) << 56)
                | (((long) src[offset + 1] & 0xFFL) << 48)
                | (((long) src[offset + 2] & 0xFFL) << 40)
                | (((long) src[offset + 3] & 0xFFL) << 32)
                | (((long) src[offset + 4] & 0xFFL) << 24)
                | (((long) src[offset + 5] & 0xFFL) << 16)
                | (((long) src[offset + 6] & 0xFFL) << 8)
                | ((long) src[offset + 7] & 0xFFL);
    }

    public static void readLittleEndianLongs(byte[] src, int offset, long[] dst) {
        for (int i = 0; i < dst.length; ++i) {
            dst[i] = readLittleEndianLong(src, offset + i * 8);
        }
    }

    public static void readBigEndianLongs(byte[] src, int srcOff, long[] dst, int dstOff, int count) {
        for (int i = 0; i < count; ++i) {
            dst[dstOff + i] = readBigEndianLong(src, srcOff + i * 8);
        }
    }

    public static byte[] toLittleEndianBytes(long value) {
        byte[] dst = new byte[8];
        writeLittleEndianLong(value, dst, 0);
        return dst;
    }

    public static void writeLittleEndianLong(long value, byte[] dst, int offset) {
        dst[offset] = (byte) value;
        dst[offset + 1] = (byte) (value >>> 8);
        dst[offset + 2] = (byte) (value >>> 16);
        dst[offset + 3] = (byte) (value >>> 24);
        dst[offset + 4] = (byte) (value >>> 32);
        dst[offset + 5] = (byte) (value >>> 40);
        dst[offset + 6] = (byte) (value >>> 48);
        dst[offset + 7] = (byte) (value >>> 56);
    }

    public static byte[] toLittleEndianBytes(long[] values) {
        byte[] dst = new byte[values.length * 8];
        writeLittleEndianLongs(values, dst, 0);
        return dst;
    }

    public static void writeBigEndianLongs(long[] src, byte[] dst, int offset) {
        writeBigEndianLongs(src, 0, src.length, dst, offset);
    }

    public static void writeBigEndianLongs(long[] src, int srcOff, int count, byte[] dst, int dstOff) {
        for (int i = 0; i < count; ++i) {
            writeBigEndianLong(src[srcOff + i], dst, dstOff + i * 8);
        }
    }
}
