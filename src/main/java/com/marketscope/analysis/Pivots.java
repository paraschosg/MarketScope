package com.marketscope.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Swing highs and lows: a candle whose high (low) is the extreme of the {@code k} candles on
 * each side. A swing is only known {@code k} candles after it happens.
 */
public final class Pivots {

    /**
     * @param confirm the first candle at which the swing is known ({@code index + k})
     */
    public record Pivot(int index, double price, boolean high, int confirm) {
    }

    private Pivots() {
    }

    public static List<Pivot> highs(double[] high, int k) {
        List<Pivot> out = new ArrayList<>();
        for (int i = k; i < high.length - k; i++) {
            if (extreme(high, i, k, true)) {
                out.add(new Pivot(i, high[i], true, i + k));
            }
        }
        return out;
    }

    public static List<Pivot> lows(double[] low, int k) {
        List<Pivot> out = new ArrayList<>();
        for (int i = k; i < low.length - k; i++) {
            if (extreme(low, i, k, false)) {
                out.add(new Pivot(i, low[i], false, i + k));
            }
        }
        return out;
    }

    /** At least as extreme as everything to the left, strictly more than everything to the right. */
    private static boolean extreme(double[] x, int i, int k, boolean max) {
        for (int j = i - k; j < i; j++) {
            if (max ? x[j] > x[i] : x[j] < x[i]) {
                return false;
            }
        }
        for (int j = i + 1; j <= i + k; j++) {
            if (max ? x[j] >= x[i] : x[j] <= x[i]) {
                return false;
            }
        }
        return true;
    }
}
