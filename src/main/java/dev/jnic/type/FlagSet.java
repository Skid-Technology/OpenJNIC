package dev.jnic.type;

import java.util.BitSet;

public class FlagSet {
    private final BitSet bits;

    public FlagSet(BitSet bits) {
        this.bits = bits;
    }

    public FlagSet() {
        this(new BitSet());
    }

    public static FlagSet of(int flag) {
        FlagSet flagSet = new FlagSet();
        flagSet.bits.set(flag);
        return flagSet;
    }

    void set(int flag) {
        this.bits.set(flag);
    }

    void clear(int flag) {
        this.bits.clear(flag);
    }

    public boolean get(int flag) {
        return this.bits.get(flag);
    }

    public boolean isEmpty() {
        return this.bits.isEmpty();
    }

    public int cardinality() {
        return this.bits.cardinality();
    }

    public int nextSetBit(int fromIndex) {
        return this.bits.nextSetBit(fromIndex);
    }

    FlagSet copy() {
        return new FlagSet((BitSet) this.bits.clone());
    }
}
