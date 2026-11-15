package dev.jnic.crypto;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class ChaCha20Stream extends InputStream {
    private final byte[] key;
    private final byte[] nonce;
    private final int[] state = new int[16];
    private final byte[] buffer = new byte[64];
    private int bufferIndex = 64;

    private ChaCha20Stream(String constant, byte[] key, byte[] nonce) {
        if (key.length != 32) {
            throw new IllegalArgumentException("Key must be 32 bytes, got " + key.length);
        }
        if (nonce.length != 12) {
            throw new IllegalArgumentException("Nonce must be 12 bytes, got " + nonce.length);
        }
        byte[] constantBytes = constant.getBytes(StandardCharsets.US_ASCII);
        if (constantBytes.length != 16) {
            throw new IllegalArgumentException("Constant must be 16 bytes, got " + constantBytes.length);
        }
        this.key = key;
        this.nonce = nonce;
        EndianUtils.readLittleEndianInts(constantBytes, 0, this.state, 0, 4);
        EndianUtils.readLittleEndianInts(key, 0, this.state, 4, 8);
        this.state[12] = 0;
        EndianUtils.readLittleEndianInts(nonce, 0, this.state, 13, 3);
    }

    public static ChaCha20Stream create(String constant, SecureRandom random) {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        if (random != null) {
            random.nextBytes(key);
            random.nextBytes(nonce);
        } else {
            new SecureRandom().nextBytes(key);
            new SecureRandom().nextBytes(nonce);
        }
        return new ChaCha20Stream(constant, key, nonce);
    }

    public byte[] getKey() {
        return this.key.clone();
    }

    public byte[] getNonce() {
        return this.nonce.clone();
    }

    private void generateBlock() {
        int[] working = new int[16];
        System.arraycopy(this.state, 0, working, 0, 16);
        for (int i = 0; i < 10; ++i) {
            quarterRound(working, 0, 4, 8, 12);
            quarterRound(working, 1, 5, 9, 13);
            quarterRound(working, 2, 6, 10, 14);
            quarterRound(working, 3, 7, 11, 15);
            quarterRound(working, 0, 5, 10, 15);
            quarterRound(working, 1, 6, 11, 12);
            quarterRound(working, 2, 7, 8, 13);
            quarterRound(working, 3, 4, 9, 14);
        }
        for (int i = 0; i < 16; ++i) {
            int word = working[i] + this.state[i];
            this.buffer[i * 4] = (byte) word;
            this.buffer[i * 4 + 1] = (byte) (word >>> 8);
            this.buffer[i * 4 + 2] = (byte) (word >>> 16);
            this.buffer[i * 4 + 3] = (byte) (word >>> 24);
        }
        ++this.state[12];
        this.bufferIndex = 0;
    }

    private static void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] += x[b];
        x[d] = Integer.rotateLeft(x[d] ^ x[a], 16);
        x[c] += x[d];
        x[b] = Integer.rotateLeft(x[b] ^ x[c], 12);
        x[a] += x[b];
        x[d] = Integer.rotateLeft(x[d] ^ x[a], 8);
        x[c] += x[d];
        x[b] = Integer.rotateLeft(x[b] ^ x[c], 7);
    }

    @Override
    public int read(byte[] dst, int off, int len) {
        if (len <= 0) {
            return 0;
        }
        for (int i = 0; i < len; ++i) {
            if (this.bufferIndex >= 64) {
                this.generateBlock();
            }
            dst[off + i] = this.buffer[this.bufferIndex++];
        }
        return len;
    }

    @Override
    public int read() {
        if (this.bufferIndex >= 64) {
            this.generateBlock();
        }
        return this.buffer[this.bufferIndex++] & 0xFF;
    }

    public int getBlockCounter() {
        return this.state[12];
    }
}
