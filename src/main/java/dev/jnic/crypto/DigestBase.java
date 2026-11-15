package dev.jnic.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class DigestBase {
    private static final Map<DigestBase, MessageDigest> DIGESTS = Collections.synchronizedMap(new IdentityHashMap<>());

    protected DigestBase() {
    }

    final MessageDigest digest() {
        MessageDigest digest = DIGESTS.get(this);
        if (digest == null) {
            try {
                digest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            DIGESTS.put(this, digest);
        }
        return digest;
    }

    public void update(byte input) {
        this.digest().update(input);
    }

    public void update(byte[] input, int offset, int length) {
        this.digest().update(input, offset, length);
    }

    public void finish() {
        this.digest().digest();
    }

    public void reset() {
        this.digest().reset();
    }

    protected abstract void processBlock(byte[] input, int offset);

    protected abstract void processLength(long bitLength);

    protected abstract void processDigest();
}
