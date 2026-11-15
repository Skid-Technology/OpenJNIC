package dev.jnic.loader;

import java.io.IOException;

final class LengthDecoder extends LengthModel {
    private final LzmaDecoder decoder;

    LengthDecoder(LzmaDecoder decoder) {
        this.decoder = decoder;
    }

    final int decode(int posState) throws IOException {
        if (this.decoder.rc.decodeBit(this.choice, 0) == 0) {
            return this.decoder.rc.decodeBitTree(this.low[posState]) + 2;
        }
        if (this.decoder.rc.decodeBit(this.choice, 1) == 0) {
            return this.decoder.rc.decodeBitTree(this.mid[posState]) + 2 + 8;
        }
        return this.decoder.rc.decodeBitTree(this.high) + 2 + 8 + 8;
    }
}
