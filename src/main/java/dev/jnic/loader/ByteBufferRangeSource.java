package dev.jnic.loader;

import java.io.IOException;

public final class ByteBufferRangeSource extends RangeDecoder {
    public final byte[] buf = new byte[65531];
    public int pos = this.buf.length;

    @Override
    public void normalize() throws IOException {
        if ((this.range & 0xFF000000) == 0) {
            try {
                this.code = (this.code << 8) | (this.buf[this.pos++] & 0xFF);
                this.range <<= 8;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IOException();
            }
        }
    }
}
