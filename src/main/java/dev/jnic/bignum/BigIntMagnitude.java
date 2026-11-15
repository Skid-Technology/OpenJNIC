package dev.jnic.bignum;

import java.util.Arrays;

class BigIntMagnitude {
    static final BigIntMagnitude ONE = new BigIntMagnitude(1);
    static final int KNUTH_POW2_THRESH_LEN = 6;
    static final int KNUTH_POW2_THRESH_ZEROS = 3;

    int[] value;
    int intLen;
    int offset = 0;

    BigIntMagnitude() {
        this.value = new int[1];
        this.intLen = 0;
    }

    BigIntMagnitude(int val) {
        this.value = new int[]{val};
        this.intLen = 1;
    }

    BigIntMagnitude(int[] val) {
        this.value = val;
        this.intLen = val.length;
    }

    BigIntMagnitude(BigInteger b) {
        this.intLen = b.mag.length;
        this.value = Arrays.copyOf(b.mag, this.intLen);
    }

    BigIntMagnitude(BigIntMagnitude val) {
        this.intLen = val.intLen;
        this.value = Arrays.copyOfRange(val.value, val.offset, val.offset + this.intLen);
    }

    private static java.math.BigInteger toJava(BigIntMagnitude v) {
        java.math.BigInteger r = java.math.BigInteger.ZERO;
        for (int i = 0; i < v.intLen; i++) {
            r = r.shiftLeft(32).or(java.math.BigInteger.valueOf((long) v.value[v.offset + i] & BigInteger.LONG_MASK));
        }
        return r;
    }

    private void setFromJava(java.math.BigInteger v) {
        if (v.signum() < 0) {
            v = v.negate();
        }
        int len = Math.max(1, (v.bitLength() + 31) / 32);
        this.value = new int[len];
        this.intLen = len;
        this.offset = 0;
        for (int i = 0; i < len; i++) {
            this.value[len - 1 - i] = v.shiftRight(i * 32).intValue();
        }
    }

    private static BigIntMagnitude fromJava(java.math.BigInteger v) {
        BigIntMagnitude r = new BigIntMagnitude(0);
        r.setFromJava(v);
        return r;
    }

    BigInteger toBigInteger(int sign) {
        return BigInteger.fromJava(sign < 0 ? toJava(this).negate() : toJava(this));
    }

    BigInteger toBigInteger() {
        return BigInteger.fromJava(toJava(this));
    }

    long toLong() {
        return toJava(this).longValue();
    }

    void normalize() {
        while (this.intLen > 0 && this.value[this.offset] == 0) {
            this.offset++;
            this.intLen--;
        }
    }

    void clear() {
        this.offset = 0;
        this.intLen = 0;
        Arrays.fill(this.value, 0);
    }

    final int compare(BigIntMagnitude b) {
        return toJava(this).compareTo(toJava(b));
    }

    void copyValue(BigIntMagnitude src) {
        this.value = src.value.clone();
        this.intLen = src.intLen;
        this.offset = src.offset;
    }

    void copyValue(int[] val) {
        this.value = val.clone();
        this.intLen = val.length;
        this.offset = 0;
    }

    void setValue(int[] val, int length) {
        this.value = val;
        this.intLen = length;
        this.offset = 0;
    }

    boolean isZero() {
        return toJava(this).signum() == 0;
    }

    boolean isOne() {
        return toJava(this).equals(java.math.BigInteger.ONE);
    }

    boolean isEven() {
        return !toJava(this).testBit(0);
    }

    boolean isOdd() {
        return toJava(this).testBit(0);
    }

    boolean isNormal() {
        return this.intLen == 0 || this.value[this.offset] != 0;
    }

    void leftShift(int n) {
        setFromJava(toJava(this).shiftLeft(n));
    }

    void rightShift(int n) {
        setFromJava(toJava(this).shiftRight(n));
    }

    void add(BigIntMagnitude addend) {
        setFromJava(toJava(this).add(toJava(addend)));
    }

    void add(int addend) {
        setFromJava(toJava(this).add(java.math.BigInteger.valueOf(addend)));
    }

    void mul(int y) {
        setFromJava(toJava(this).multiply(java.math.BigInteger.valueOf(y)));
    }

    BigIntMagnitude divide(BigIntMagnitude b) {
        java.math.BigInteger divisor = toJava(b);
        return divisor.signum() == 0 ? fromJava(java.math.BigInteger.ZERO) : fromJava(toJava(this).divide(divisor));
    }

    @Override
    public String toString() {
        return toJava(this).toString();
    }
}
