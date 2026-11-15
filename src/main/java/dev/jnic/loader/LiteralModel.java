package dev.jnic.loader;

import java.util.Arrays;

abstract class LiteralModel {
    final short[] probs = new short[768];

    LiteralModel() {
    }

    final void reset() {
        Arrays.fill(this.probs, (short) 1024);
    }
}
