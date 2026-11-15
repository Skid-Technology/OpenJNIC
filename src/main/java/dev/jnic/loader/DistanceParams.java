package dev.jnic.loader;

abstract class DistanceParams {
    final int lc;
    final int posMask;

    DistanceParams(int lc, int lp) {
        this.lc = lc;
        this.posMask = (1 << lp) - 1;
    }
}
