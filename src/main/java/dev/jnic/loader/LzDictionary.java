package dev.jnic.loader;

import java.io.IOException;

public final class LzDictionary {
    public final byte[] buf;
    public final int size;
    public int start = 0;
    public int pos = 0;
    public int full = 0;
    public int limit = 0;
    public int pendingLen = 0;
    public int pendingDist = 0;

    public LzDictionary(int dictSize, byte[] presetDict) {
        this.size = dictSize;
        this.buf = new byte[this.size];
        if (presetDict != null) {
            this.pos = Math.min(presetDict.length, dictSize);
            this.full = this.pos;
            this.start = this.pos;
            System.arraycopy(presetDict, presetDict.length - this.pos, this.buf, 0, this.pos);
        }
    }

    public int getByte(int dist) {
        int offset = this.pos - dist - 1;
        if (dist >= this.pos) {
            offset += this.size;
        }
        return this.buf[offset] & 0xFF;
    }

    public void repeat(int dist, int len) throws IOException {
        if (dist < 0 || dist >= this.full) {
            throw new IOException();
        }
        int left = Math.min(this.limit - this.pos, len);
        this.pendingLen = len - left;
        this.pendingDist = dist;
        int back = this.pos - dist - 1;
        if (back < 0) {
            assert this.full == this.size;
            back += this.size;
            int copySize = Math.min(this.size - back, left);
            assert copySize <= dist + 1;
            System.arraycopy(this.buf, back, this.buf, this.pos, copySize);
            this.pos += copySize;
            back = 0;
            left -= copySize;
            if (left == 0) {
                return;
            }
        }
        assert back < this.pos;
        assert left > 0;
        do {
            int copySize = Math.min(left, this.pos - back);
            System.arraycopy(this.buf, back, this.buf, this.pos, copySize);
            this.pos += copySize;
            left -= copySize;
        } while (left > 0);
        if (this.full < this.pos) {
            this.full = this.pos;
        }
    }
}
