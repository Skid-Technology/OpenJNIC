package dev.jnic.bignum;

import java.util.Arrays;
import java.util.Random;

public class BigInteger {
    static final long LONG_MASK = 0xFFFFFFFFL;
    static final long SIGN_BIT_MASK = Long.MIN_VALUE;
    private static final int MAX_MAG_LENGTH = 67108864;

    public static final BigInteger ZERO = new BigInteger(new int[0], 0);
    public static final BigInteger ONE = valueOf(1L);
    public static final BigInteger TWO = valueOf(2L);
    public static final BigInteger NEGATIVE_ONE = valueOf(-1L);
    public static final BigInteger TEN = valueOf(10L);

    int signum;
    int[] mag;

    public BigInteger(byte[] val) {
        BigInteger converted = fromJava(new java.math.BigInteger(val));
        this.signum = converted.signum;
        this.mag = converted.mag;
    }

    public BigInteger(int signum, byte[] magnitude) {
        BigInteger converted = fromJava(new java.math.BigInteger(signum, magnitude));
        this.signum = converted.signum;
        this.mag = converted.mag;
    }

    public BigInteger(String val, int radix) {
        BigInteger converted = fromJava(new java.math.BigInteger(val, radix));
        this.signum = converted.signum;
        this.mag = converted.mag;
    }

    public BigInteger(String val) {
        this(val, 10);
    }

    public BigInteger(int numBits, Random rnd) {
        BigInteger converted = fromJava(new java.math.BigInteger(numBits, rnd));
        this.signum = converted.signum;
        this.mag = converted.mag;
    }

    BigInteger(int[] mag, int signum) {
        this.signum = mag.length == 0 ? 0 : signum;
        this.mag = mag;
        if (this.mag.length >= MAX_MAG_LENGTH) {
            checkRange();
        }
    }

    private BigInteger(long val) {
        if (val < 0L) {
            val = -val;
            this.signum = -1;
        } else if (val == 0L) {
            this.signum = 0;
            this.mag = new int[0];
            return;
        } else {
            this.signum = 1;
        }
        int highWord = (int) (val >>> 32);
        if (highWord == 0) {
            this.mag = new int[]{(int) val};
        } else {
            this.mag = new int[]{highWord, (int) val};
        }
    }

    private void checkRange() {
        if (this.mag.length > MAX_MAG_LENGTH || (this.mag.length == MAX_MAG_LENGTH && this.mag[0] < 0)) {
            throw new ArithmeticException("BigInteger would overflow supported range");
        }
    }

    static java.math.BigInteger toJava(BigInteger value) {
        if (value == null) {
            return null;
        }
        java.math.BigInteger result = java.math.BigInteger.ZERO;
        if (value.mag != null) {
            for (int word : value.mag) {
                result = result.shiftLeft(32).or(java.math.BigInteger.valueOf((long) word & LONG_MASK));
            }
        }
        if (value.signum < 0) {
            result = result.negate();
        }
        return result;
    }

    static BigInteger fromJava(java.math.BigInteger value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            return new BigInteger(new int[0], 0);
        }
        int sign = value.signum();
        java.math.BigInteger abs = value.abs();
        int len = Math.max(1, (abs.bitLength() + 31) / 32);
        int[] words = new int[len];
        for (int i = 0; i < len; i++) {
            words[len - 1 - i] = abs.shiftRight(i * 32).intValue();
        }
        return new BigInteger(words, sign);
    }

    static int bitLengthForInt(int n) {
        return 32 - Integer.numberOfLeadingZeros(n);
    }

    public static BigInteger valueOf(long val) {
        return new BigInteger(val);
    }

    public BigInteger add(BigInteger val) {
        return fromJava(toJava(this).add(toJava(val)));
    }

    public BigInteger subtract(BigInteger val) {
        return fromJava(toJava(this).subtract(toJava(val)));
    }

    public BigInteger multiply(BigInteger val) {
        return fromJava(toJava(this).multiply(toJava(val)));
    }

    public BigInteger divide(BigInteger val) {
        return fromJava(toJava(this).divide(toJava(val)));
    }

    public BigInteger[] divideAndRemainder(BigInteger val) {
        java.math.BigInteger[] parts = toJava(this).divideAndRemainder(toJava(val));
        return new BigInteger[]{fromJava(parts[0]), fromJava(parts[1])};
    }

    public BigInteger remainder(BigInteger val) {
        return fromJava(toJava(this).remainder(toJava(val)));
    }

    public BigInteger mod(BigInteger m) {
        return fromJava(toJava(this).mod(toJava(m)));
    }

    public BigInteger and(BigInteger val) {
        return fromJava(toJava(this).and(toJava(val)));
    }

    public BigInteger or(BigInteger val) {
        return fromJava(toJava(this).or(toJava(val)));
    }

    public BigInteger xor(BigInteger val) {
        return fromJava(toJava(this).xor(toJava(val)));
    }

    public BigInteger andNot(BigInteger val) {
        return fromJava(toJava(this).andNot(toJava(val)));
    }

    public BigInteger not() {
        return fromJava(toJava(this).not());
    }

    public BigInteger gcd(BigInteger val) {
        return fromJava(toJava(this).gcd(toJava(val)));
    }

    public BigInteger modInverse(BigInteger m) {
        return fromJava(toJava(this).modInverse(toJava(m)));
    }

    public BigInteger min(BigInteger val) {
        return fromJava(toJava(this).min(toJava(val)));
    }

    public BigInteger max(BigInteger val) {
        return fromJava(toJava(this).max(toJava(val)));
    }

    public BigInteger negate() {
        return fromJava(toJava(this).negate());
    }

    public BigInteger abs() {
        return this.signum >= 0 ? this : this.negate();
    }

    public BigInteger shiftLeft(int n) {
        return fromJava(toJava(this).shiftLeft(n));
    }

    public BigInteger shiftRight(int n) {
        return fromJava(toJava(this).shiftRight(n));
    }

    public int signum() {
        return this.signum;
    }

    public int intValue() {
        return toJava(this).intValue();
    }

    public long longValue() {
        return toJava(this).longValue();
    }

    public int bitLength() {
        return toJava(this).bitLength();
    }

    public int bitCount() {
        return toJava(this).bitCount();
    }

    public int compareTo(BigInteger val) {
        return toJava(this).compareTo(toJava(val));
    }

    public byte[] toByteArray() {
        return toJava(this).toByteArray();
    }

    public String toString(int radix) {
        int r = (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) ? 10 : radix;
        return toJava(this).toString(r);
    }

    @Override
    public String toString() {
        return toJava(this).toString();
    }

    @Override
    public boolean equals(Object x) {
        if (x == this) {
            return true;
        }
        if (!(x instanceof BigInteger)) {
            return false;
        }
        BigInteger other = (BigInteger) x;
        return this.signum == other.signum && Arrays.equals(this.mag, other.mag);
    }

    @Override
    public int hashCode() {
        return toJava(this).hashCode();
    }
}
