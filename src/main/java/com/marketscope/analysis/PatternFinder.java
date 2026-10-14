package com.marketscope.analysis;

import com.marketscope.model.Candle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds {@link PatternKind}s in a candle history. Every rule only looks at candles up to the
 * pattern's {@link Pattern#confirm} candle — swings count only once they are confirmed and
 * shapes only once price breaks their line — so the same rules can be scored on the past
 * without knowing the future. Bottom shapes are found as top shapes on the price mirrored
 * around zero. Pure.
 */
public final class PatternFinder {

    /** Candles on each side that make a swing high or low. */
    static final int SWING = 4;
    /** How long after its last swing a shape may wait for the break that confirms it. */
    static final int MAX_WAIT = 40;
    /** Repeats of one kind closer than this are the same event. */
    static final int DEDUPE = 5;
    /** The range a breakout must leave. */
    static final int RANGE = 20;

    private PatternFinder() {
    }

    public static List<Pattern> find(List<Candle> candles) {
        List<Pattern> out = new ArrayList<>();
        if (candles.size() < 30) {
            return out;
        }
        double[] hi = Indicators.highs(candles);
        double[] lo = Indicators.lows(candles);
        double[] cl = Indicators.closes(candles);
        double[] atr = Indicators.atr(candles, 14);
        doubleTops(hi, lo, cl, atr, false, out);
        doubleTops(negate(lo), negate(hi), negate(cl), atr, true, out);
        headAndShoulders(hi, lo, cl, atr, false, out);
        headAndShoulders(negate(lo), negate(hi), negate(cl), atr, true, out);
        breakouts(hi, lo, cl, Indicators.volumes(candles), out);
        averageCrosses(cl, out);
        rsiExits(cl, out);
        engulfing(candles, cl, out);
        out.sort(Comparator.comparingInt(Pattern::confirm).thenComparing(Pattern::kind));
        return out;
    }

    // Shapes ---------------------------------------------------------------------------------

    private static void doubleTops(double[] hi, double[] lo, double[] cl, double[] atr, boolean mirrored,
                                   List<Pattern> out) {
        PatternKind kind = mirrored ? PatternKind.DOUBLE_BOTTOM : PatternKind.DOUBLE_TOP;
        double sign = mirrored ? -1 : 1;
        List<Pivots.Pivot> peaks = Pivots.highs(hi, SWING);
        List<Pattern> candidates = new ArrayList<>();
        for (int a = 0; a < peaks.size(); a++) {
            for (int b = a + 1; b < peaks.size(); b++) {
                Pivots.Pivot p1 = peaks.get(a);
                Pivots.Pivot p2 = peaks.get(b);
                int distance = p2.index() - p1.index();
                if (distance < 5) {
                    continue;
                }
                if (distance > 60) {
                    break;
                }
                double lowerPeak = Math.min(p1.price(), p2.price());
                double top = Math.max(p1.price(), p2.price());
                double size = (Math.abs(p1.price()) + Math.abs(p2.price())) / 2;
                double scale = scale(atr, p2.index(), size);
                if (Math.abs(p1.price() - p2.price()) > Math.max(0.012 * size, 0.6 * scale)) {
                    continue;
                }
                if (max(hi, p1.index() + 1, p2.index() - 1) > lowerPeak) {
                    continue; // something between the peaks rose above them: not two peaks
                }
                int t = argMin(lo, p1.index() + 1, p2.index() - 1);
                if (t < 0) {
                    continue;
                }
                double trough = lo[t];
                if (lowerPeak - trough < Math.max(0.025 * size, 1.5 * scale)) {
                    continue; // too shallow a dip to call it two peaks
                }
                int confirm = -1;
                for (int j = p2.index() + 1; j < cl.length && j <= p2.index() + MAX_WAIT; j++) {
                    if (hi[j] > top) {
                        break; // price went on to a new high: the shape failed
                    }
                    if (j >= p2.confirm() && cl[j] < trough) {
                        confirm = j;
                        break;
                    }
                }
                if (confirm < 0) {
                    continue;
                }
                candidates.add(new Pattern(kind, p1.index(), confirm, sign * trough, List.of(
                        point(p1.index(), p1.price(), sign), point(t, trough, sign),
                        point(p2.index(), p2.price(), sign), point(confirm, cl[confirm], sign))));
            }
        }
        accept(candidates, out);
    }

    private static void headAndShoulders(double[] hi, double[] lo, double[] cl, double[] atr, boolean mirrored,
                                         List<Pattern> out) {
        PatternKind kind = mirrored ? PatternKind.INVERSE_HEAD_AND_SHOULDERS : PatternKind.HEAD_AND_SHOULDERS;
        double sign = mirrored ? -1 : 1;
        List<Pivots.Pivot> peaks = Pivots.highs(hi, SWING);
        List<Pattern> candidates = new ArrayList<>();
        for (int i = 0; i + 2 < peaks.size(); i++) {
            Pivots.Pivot left = peaks.get(i);
            Pivots.Pivot head = peaks.get(i + 1);
            Pivots.Pivot right = peaks.get(i + 2);
            int span = right.index() - left.index();
            if (span < 8 || span > 90) {
                continue;
            }
            double shoulders = (Math.abs(left.price()) + Math.abs(right.price())) / 2;
            double scale = scale(atr, head.index(), shoulders);
            if (head.price() - Math.max(left.price(), right.price()) < 0.5 * scale) {
                continue;
            }
            if (Math.abs(left.price() - right.price()) > Math.max(0.03 * shoulders, scale)) {
                continue;
            }
            int t1 = argMin(lo, left.index() + 1, head.index() - 1);
            int t2 = argMin(lo, head.index() + 1, right.index() - 1);
            if (t1 < 0 || t2 < 0) {
                continue;
            }
            double low1 = lo[t1];
            double low2 = lo[t2];
            java.util.function.IntToDoubleFunction neckline = j -> low1 + (low2 - low1) * (j - t1) / (t2 - t1);
            if (left.price() < neckline.applyAsDouble(left.index()) + 0.3 * scale
                    || right.price() < neckline.applyAsDouble(right.index()) + 0.3 * scale) {
                continue;
            }
            int confirm = -1;
            for (int j = right.index() + 1; j < cl.length && j <= right.index() + MAX_WAIT; j++) {
                if (hi[j] > head.price()) {
                    break;
                }
                if (j >= right.confirm() && cl[j] < neckline.applyAsDouble(j)) {
                    confirm = j;
                    break;
                }
            }
            if (confirm < 0) {
                continue;
            }
            candidates.add(new Pattern(kind, left.index(), confirm, sign * neckline.applyAsDouble(confirm), List.of(
                    point(left.index(), left.price(), sign), point(t1, low1, sign),
                    point(head.index(), head.price(), sign), point(t2, low2, sign),
                    point(right.index(), right.price(), sign), point(confirm, cl[confirm], sign))));
        }
        accept(candidates, out);
    }

    /**
     * Keeps candidates in order of confirmation, dropping one that confirms within
     * {@link #DEDUPE} candles of the last kept one: the same event seen through different swings.
     * Because only earlier confirmations decide, a pattern found today stays found tomorrow.
     */
    private static void accept(List<Pattern> candidates, List<Pattern> out) {
        candidates.sort(Comparator.comparingInt(Pattern::confirm).thenComparingInt(Pattern::start));
        int lastConfirm = Integer.MIN_VALUE / 2;
        for (Pattern p : candidates) {
            if (p.confirm() - lastConfirm >= DEDUPE) {
                out.add(p);
                lastConfirm = p.confirm();
            }
        }
    }

    // Single-candle and indicator events ---------------------------------------------------------

    private static void breakouts(double[] hi, double[] lo, double[] cl, double[] vol, List<Pattern> out) {
        double[] averageVolume = Indicators.sma(vol, RANGE);
        int lastUp = -DEDUPE * 10;
        int lastDown = -DEDUPE * 10;
        for (int j = RANGE + 1; j < cl.length; j++) {
            double usual = averageVolume[j - 1];
            boolean volumeOk = Double.isNaN(usual) || usual <= 0 || vol[j] >= 1.2 * usual; // no volume: price only
            double ceiling = max(hi, j - RANGE, j - 1);
            double floor = min(lo, j - RANGE, j - 1);
            if (cl[j] > ceiling && cl[j - 1] <= max(hi, j - 1 - RANGE, j - 2) && volumeOk && j - lastUp >= DEDUPE) {
                lastUp = j;
                out.add(new Pattern(PatternKind.BREAKOUT_UP, j - RANGE, j, ceiling, List.of(
                        new Pattern.Point(j - RANGE, ceiling), new Pattern.Point(j - 1, ceiling),
                        new Pattern.Point(j, cl[j]))));
            }
            if (cl[j] < floor && cl[j - 1] >= min(lo, j - 1 - RANGE, j - 2) && volumeOk && j - lastDown >= DEDUPE) {
                lastDown = j;
                out.add(new Pattern(PatternKind.BREAKOUT_DOWN, j - RANGE, j, floor, List.of(
                        new Pattern.Point(j - RANGE, floor), new Pattern.Point(j - 1, floor),
                        new Pattern.Point(j, cl[j]))));
            }
        }
    }

    private static void averageCrosses(double[] cl, List<Pattern> out) {
        double[] fast = Indicators.sma(cl, 50);
        double[] slow = Indicators.sma(cl, 200);
        for (int j = 1; j < cl.length; j++) {
            if (Double.isNaN(fast[j - 1]) || Double.isNaN(slow[j - 1])) {
                continue;
            }
            if (fast[j - 1] <= slow[j - 1] && fast[j] > slow[j]) {
                out.add(new Pattern(PatternKind.GOLDEN_CROSS, j, j, slow[j], List.of(new Pattern.Point(j, slow[j]))));
            } else if (fast[j - 1] >= slow[j - 1] && fast[j] < slow[j]) {
                out.add(new Pattern(PatternKind.DEATH_CROSS, j, j, slow[j], List.of(new Pattern.Point(j, slow[j]))));
            }
        }
    }

    private static void rsiExits(double[] cl, List<Pattern> out) {
        double[] rsi = Indicators.rsi(cl, 14);
        for (int j = 1; j < cl.length; j++) {
            if (Double.isNaN(rsi[j - 1])) {
                continue;
            }
            if (rsi[j - 1] < 30 && rsi[j] >= 30) {
                out.add(new Pattern(PatternKind.RSI_OVERSOLD_EXIT, j, j, Double.NaN,
                        List.of(new Pattern.Point(j, cl[j]))));
            } else if (rsi[j - 1] > 70 && rsi[j] <= 70) {
                out.add(new Pattern(PatternKind.RSI_OVERBOUGHT_EXIT, j, j, Double.NaN,
                        List.of(new Pattern.Point(j, cl[j]))));
            }
        }
    }

    private static void engulfing(List<Candle> candles, double[] cl, List<Pattern> out) {
        double[] trend = Indicators.sma(cl, 20);
        for (int j = 21; j < candles.size(); j++) {
            Candle previous = candles.get(j - 1);
            Candle current = candles.get(j);
            double previousBody = Math.abs(previous.close() - previous.open());
            double currentBody = Math.abs(current.close() - current.open());
            if (previousBody == 0 || currentBody <= previousBody || Double.isNaN(trend[j - 1])) {
                continue;
            }
            boolean bullish = previous.close() < previous.open() && current.close() > current.open()
                    && current.open() <= previous.close() && current.close() >= previous.open()
                    && cl[j - 1] < trend[j - 1];
            boolean bearish = previous.close() > previous.open() && current.close() < current.open()
                    && current.open() >= previous.close() && current.close() <= previous.open()
                    && cl[j - 1] > trend[j - 1];
            if (bullish) {
                out.add(new Pattern(PatternKind.BULLISH_ENGULFING, j - 1, j, Double.NaN,
                        List.of(new Pattern.Point(j, current.low()))));
            } else if (bearish) {
                out.add(new Pattern(PatternKind.BEARISH_ENGULFING, j - 1, j, Double.NaN,
                        List.of(new Pattern.Point(j, current.high()))));
            }
        }
    }

    // Helpers --------------------------------------------------------------------------------------

    private static Pattern.Point point(int index, double price, double sign) {
        return new Pattern.Point(index, sign * price);
    }

    /** One typical candle move at {@code i}; 2 % of the price while the ATR is not defined yet. */
    private static double scale(double[] atr, int i, double price) {
        double a = Indicators.at(atr, i);
        return Double.isNaN(a) || a <= 0 ? 0.02 * price : a;
    }

    private static double max(double[] x, int from, int to) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = Math.max(0, from); i <= to && i < x.length; i++) {
            m = Math.max(m, x[i]);
        }
        return m;
    }

    private static double min(double[] x, int from, int to) {
        double m = Double.POSITIVE_INFINITY;
        for (int i = Math.max(0, from); i <= to && i < x.length; i++) {
            m = Math.min(m, x[i]);
        }
        return m;
    }

    private static int argMin(double[] x, int from, int to) {
        int best = -1;
        for (int i = Math.max(0, from); i <= to && i < x.length; i++) {
            if (best < 0 || x[i] < x[best]) {
                best = i;
            }
        }
        return best;
    }

    private static double[] negate(double[] x) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = -x[i];
        }
        return out;
    }
}
