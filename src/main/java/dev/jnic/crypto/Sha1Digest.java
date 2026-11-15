package dev.jnic.crypto;

public class Sha1Digest extends DigestBase {
    private static final int DIGEST_LENGTH = 20;

    public Sha1Digest() {
        this.reset();
    }

    public int getDigestSize() {
        return DIGEST_LENGTH;
    }

    @Override
    protected void processBlock(byte[] input, int offset) {
        int remaining = input.length - offset;
        if (remaining > 0) {
            this.digest().update(input, offset, remaining);
        }
    }

    @Override
    protected void processLength(long bitLength) {
        this.digest().update((byte) (bitLength >>> 56));
        this.digest().update((byte) (bitLength >>> 48));
        this.digest().update((byte) (bitLength >>> 40));
        this.digest().update((byte) (bitLength >>> 32));
        this.digest().update((byte) (bitLength >>> 24));
        this.digest().update((byte) (bitLength >>> 16));
        this.digest().update((byte) (bitLength >>> 8));
        this.digest().update((byte) bitLength);
    }

    public int doFinal(byte[] out, int outOff) {
        byte[] hash = this.digest().digest();
        System.arraycopy(hash, 0, out, outOff, hash.length);
        return hash.length;
    }

    @Override
    public void reset() {
        this.digest().reset();
    }

    @Override
    protected void processDigest() {
        this.digest().reset();
    }
}
