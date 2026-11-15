package dev.jnic.bignum;

import java.util.Arrays;

public class BigIntMath {
    private BigInteger modulus;
    private BigInteger exponent;

    public void setParameters(BigInteger exponent, BigInteger modulus) {
        this.exponent = exponent;
        this.modulus = modulus;
    }

    public int getExponentSignum() {
        return this.exponent == null ? 0 : this.exponent.signum();
    }

    public int getModulusSignum() {
        return this.modulus == null ? 0 : this.modulus.signum();
    }

    public BigInteger fromBytes(byte[] bytes) {
        return new BigInteger(bytes);
    }

    public byte[] toBytes(BigInteger value) {
        return value == null ? new byte[0] : value.toByteArray();
    }

    public BigInteger addInt(BigInteger value, int addend) {
        return value.add(BigInteger.valueOf(addend));
    }

    public byte[] slice(byte[] data, int offset, int length) {
        return Arrays.copyOfRange(data, offset, offset + length);
    }
}
