package dev.jnic.loader;

final class DistanceDecoder extends DistanceParams {
    final BitTreeDecoder[] subcoders;
    final LzmaDecoder decoder;

    DistanceDecoder(LzmaDecoder decoder, int lc, int lp) {
        super(lc, lp);
        this.decoder = decoder;
        this.subcoders = new BitTreeDecoder[1 << (lc + lp)];
        for (int i = 0; i < this.subcoders.length; ++i) {
            this.subcoders[i] = new BitTreeDecoder(this);
        }
    }

    final void reset() {
        for (BitTreeDecoder subcoder : this.subcoders) {
            subcoder.reset();
        }
    }
}
