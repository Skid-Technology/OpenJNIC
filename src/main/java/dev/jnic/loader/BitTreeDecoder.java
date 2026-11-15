package dev.jnic.loader;

final class BitTreeDecoder extends LiteralModel {
    final DistanceDecoder parent;

    BitTreeDecoder(DistanceDecoder parent) {
        this.parent = parent;
    }
}
