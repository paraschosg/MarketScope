package com.marketscope.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How each pattern actually played out on this asset's own history: for every past
 * occurrence, did the price, {@code horizon} candles after the confirming candle, move the way
 * the pattern is traditionally read? The answer is set against the base rate — how often price
 * moved that way after any candle at all — because a pattern that is "right 55 % of the time"
 * means nothing if price rose 55 % of the time anyway. Pure.
 */
public final class Backtest {

    /** Fewer past cases than this are shown, but not trusted. */
    public static final int MIN_CASES = 10;
    /** A pattern needs at least this much lead over the base rate to count (0.05 = 5 points). */
    public static final double MIN_EDGE = 0.05;
    /**
     * And the lead must be this many standard errors above the base rate (one-sided, about 90 %
     * confidence), so a few lucky cases do not count. Twelve patterns are tested on every chart,
     * so even this lets the odd fluke through; the app says so.
     */
    public static final double MIN_Z = 1.28;

    /**
     * @param cases       past occurrences whose outcome is known
     * @param hitRate     share of cases where price moved the expected way
     * @param baseRate    share of all candles after which price moved that way
     * @param meanReturn  average price change over the horizon after the pattern, as a fraction
     * @param medianReturn median of that change
     */
    public record Stats(PatternKind kind, int horizon, int cases, double hitRate, double baseRate,
                        double meanReturn, double medianReturn) {

        /** Hit rate above the base rate, in fractions (0.06 = 6 points). */
        public double edge() {
            return hitRate - baseRate;
        }

        public boolean enoughCases() {
            return cases >= MIN_CASES;
        }

        /** How many standard errors the hit rate lies above the base rate. */
        public double zScore() {
            if (cases == 0 || baseRate <= 0 || baseRate >= 1) {
                return 0;
            }
            return edge() / Math.sqrt(baseRate * (1 - baseRate) / cases);
        }

        /** Enough cases, a real lead, and a lead unlikely to be luck. */
        public boolean beatsChance() {
            return enoughCases() && edge() >= MIN_EDGE && zScore() >= MIN_Z;
        }
    }

    private Backtest() {
    }

    public static Map<PatternKind, Stats> run(List<Pattern> patterns, double[] close, int horizon) {
        Map<PatternKind, Stats> out = new EnumMap<>(PatternKind.class);
        double upRate = baseRate(close, horizon, 1);
        double downRate = baseRate(close, horizon, -1);
        for (PatternKind kind : PatternKind.values()) {
            List<Double> returns = new ArrayList<>();
            int hits = 0;
            for (Pattern p : patterns) {
                if (p.kind() != kind || p.confirm() + horizon >= close.length || close[p.confirm()] == 0) {
                    continue;
                }
                double change = close[p.confirm() + horizon] / close[p.confirm()] - 1;
                returns.add(change);
                if (Math.signum(change) == kind.direction()) {
                    hits++;
                }
            }
            int cases = returns.size();
            double base = kind.bullish() ? upRate : downRate;
            out.put(kind, new Stats(kind, horizon, cases, cases == 0 ? 0 : (double) hits / cases, base,
                    cases == 0 ? 0 : returns.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                    median(returns)));
        }
        return out;
    }

    /** Share of candles after which price was higher (direction 1) or lower (-1) {@code horizon} later. */
    static double baseRate(double[] close, int horizon, int direction) {
        int total = 0;
        int moved = 0;
        for (int i = 0; i + horizon < close.length; i++) {
            if (close[i] == 0) {
                continue;
            }
            total++;
            if (Math.signum(close[i + horizon] - close[i]) == direction) {
                moved++;
            }
        }
        return total == 0 ? 0 : (double) moved / total;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }
}
