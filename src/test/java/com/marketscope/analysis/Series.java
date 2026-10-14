package com.marketscope.analysis;

import com.marketscope.model.Candle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Candle series for tests. */
final class Series {

    private Series() {
    }

    /** Candles that close at each given price, opening at the previous close, a small range around. */
    static List<Candle> path(double... closes) {
        List<Candle> candles = new ArrayList<>();
        double previous = closes[0];
        for (int i = 0; i < closes.length; i++) {
            double open = previous;
            double close = closes[i];
            candles.add(new Candle(Instant.ofEpochSecond(i * 86_400L), open, Math.max(open, close) + 0.3,
                    Math.min(open, close) - 0.3, close, 1000));
            previous = close;
        }
        return candles;
    }

    /** A straight line from {@code from} to {@code to} in {@code steps} closes (excluding {@code from}). */
    static double[] line(double from, double to, int steps) {
        double[] out = new double[steps];
        for (int i = 1; i <= steps; i++) {
            out[i - 1] = from + (to - from) * i / steps;
        }
        return out;
    }

    static double[] join(double[]... parts) {
        int length = 0;
        for (double[] p : parts) {
            length += p.length;
        }
        double[] out = new double[length];
        int at = 0;
        for (double[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    /** A seeded random walk with occasional trends and volume spikes. */
    static List<Candle> randomWalk(long seed, int n) {
        Random random = new Random(seed);
        List<Candle> candles = new ArrayList<>();
        double price = 100;
        double drift = 0;
        for (int i = 0; i < n; i++) {
            if (i % 60 == 0) {
                drift = (random.nextDouble() - 0.5) * 0.004;
            }
            double open = price;
            double close = open * (1 + drift + random.nextGaussian() * 0.018);
            double high = Math.max(open, close) * (1 + Math.abs(random.nextGaussian()) * 0.008);
            double low = Math.min(open, close) * (1 - Math.abs(random.nextGaussian()) * 0.008);
            double volume = 1000 * (1 + random.nextDouble() * (random.nextInt(10) == 0 ? 4 : 1));
            candles.add(new Candle(Instant.ofEpochSecond(i * 86_400L), open, high, low, close, volume));
            price = close;
        }
        return candles;
    }
}
