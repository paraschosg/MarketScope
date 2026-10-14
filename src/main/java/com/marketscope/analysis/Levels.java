package com.marketscope.analysis;

import com.marketscope.model.Candle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Support and resistance: prices where recent swings keep turning. Swing highs and lows of the
 * recent past are grouped when they lie within a fraction of a typical candle of each other; a
 * group touched at least twice is a level. Levels below the last close are support, above it
 * resistance. Pure.
 */
public final class Levels {

    /** How far back levels are looked for, in candles. */
    static final int LOOKBACK = 250;
    /** Levels kept on each side of the price. */
    static final int PER_SIDE = 3;

    /** A price level, how many swings touched it, and whether it is below the price. */
    public record Level(double price, int touches, boolean support) {
    }

    private Levels() {
    }

    public static List<Level> find(List<Candle> candles) {
        int n = candles.size();
        if (n < 30) {
            return List.of();
        }
        double[] hi = Indicators.highs(candles);
        double[] lo = Indicators.lows(candles);
        double last = candles.getLast().close();
        double atr = Indicators.last(Indicators.atr(candles, 14));
        double tolerance = Math.max(Double.isNaN(atr) ? 0 : 0.6 * atr, Math.abs(last) * 0.004);
        int from = Math.max(0, n - LOOKBACK);
        List<Double> prices = new ArrayList<>();
        for (Pivots.Pivot p : Pivots.highs(hi, PatternFinder.SWING)) {
            if (p.index() >= from) {
                prices.add(p.price());
            }
        }
        for (Pivots.Pivot p : Pivots.lows(lo, PatternFinder.SWING)) {
            if (p.index() >= from) {
                prices.add(p.price());
            }
        }
        prices.sort(Double::compare);
        List<Level> levels = new ArrayList<>();
        int i = 0;
        while (i < prices.size()) {
            int j = i;
            double sum = 0;
            while (j < prices.size() && prices.get(j) - prices.get(i) <= tolerance) {
                sum += prices.get(j);
                j++;
            }
            int touches = j - i;
            if (touches >= 2) {
                double price = sum / touches;
                levels.add(new Level(price, touches, price < last));
            }
            i = j;
        }
        List<Level> support = levels.stream().filter(Level::support)
                .sorted(Comparator.comparingDouble((Level l) -> last - l.price())).limit(PER_SIDE).toList();
        List<Level> resistance = levels.stream().filter(l -> !l.support())
                .sorted(Comparator.comparingDouble((Level l) -> l.price() - last)).limit(PER_SIDE).toList();
        List<Level> out = new ArrayList<>(resistance);
        out.addAll(support);
        out.sort(Comparator.comparingDouble(Level::price).reversed());
        return out;
    }
}
