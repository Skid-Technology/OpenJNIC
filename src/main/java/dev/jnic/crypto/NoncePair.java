package dev.jnic.crypto;

import java.util.concurrent.ThreadLocalRandom;

public class NoncePair {
    private final long high;
    private final long low;
    private final byte[] seed;

    public NoncePair(long high, long low, byte[] seed) {
        this.high = high;
        this.low = low;
        this.seed = seed;
    }

    public NoncePair(long high, long low) {
        this.high = high;
        this.low = low;
        this.seed = new byte[32];
        ThreadLocalRandom.current().nextBytes(this.seed);
    }

    public long getLow() {
        return this.low;
    }

    public long getHigh() {
        return this.high;
    }

    public byte[] getSeed() {
        return this.seed;
    }
}
