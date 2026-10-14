package com.marketscope.analysis;

import com.marketscope.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * The standard technical indicators. Every function returns an array as long as its input,
 * with {@code NaN} where the indicator is not defined yet (too few candles before it). Pure.
 */
public final class Indicators {

    /** MACD line, its signal line and their difference. */
    public record Macd(double[] line, double[] signal, double[] histogram) {
    }

    /** Bollinger bands: a moving average and a band some standard deviations either side. */
    public record Bands(double[] middle, double[] upper, double[] lower) {
    }

    private Indicators() {
    }

    public static double[] closes(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::close).toArray();
    }

    public static double[] highs(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::high).toArray();
    }

    public static double[] lows(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::low).toArray();
    }

    public static double[] volumes(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::volume).toArray();
    }

    /** Simple moving average over {@code n} values. */
    public static double[] sma(double[] x, int n) {
        double[] out = nan(x.length);
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += x[i];
            if (i >= n) {
                sum -= x[i - n];
            }
            if (i >= n - 1) {
                out[i] = sum / n;
            }
        }
        return out;
    }

    /**
     * Exponential moving average, seeded with the simple average of the first {@code n} defined
     * values; leading {@code NaN}s in the input are skipped.
     */
    public static double[] ema(double[] x, int n) {
        double[] out = nan(x.length);
        int start = firstRun(x, n);
        if (start < 0) {
            return out;
        }
        double sum = 0;
        for (int i = start; i < start + n; i++) {
            sum += x[i];
        }
        int seedAt = start + n - 1;
        out[seedAt] = sum / n;
        double k = 2.0 / (n + 1);
        for (int i = seedAt + 1; i < x.length; i++) {
            out[i] = Double.isNaN(x[i]) ? out[i - 1] : x[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** Wilder's relative strength index, 0..100. */
    public static double[] rsi(double[] close, int n) {
        double[] out = nan(close.length);
        if (close.length <= n) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= n; i++) {
            double d = close[i] - close[i - 1];
            if (d > 0) {
                gain += d;
            } else {
                loss -= d;
            }
        }
        double avgGain = gain / n;
        double avgLoss = loss / n;
        out[n] = rsiOf(avgGain, avgLoss);
        for (int i = n + 1; i < close.length; i++) {
            double d = close[i] - close[i - 1];
            avgGain = (avgGain * (n - 1) + Math.max(d, 0)) / n;
            avgLoss = (avgLoss * (n - 1) + Math.max(-d, 0)) / n;
            out[i] = rsiOf(avgGain, avgLoss);
        }
        return out;
    }

    private static double rsiOf(double avgGain, double avgLoss) {
        if (avgLoss == 0) {
            return avgGain == 0 ? 50 : 100;
        }
        return 100 - 100 / (1 + avgGain / avgLoss);
    }

    public static Macd macd(double[] close, int fast, int slow, int signal) {
        double[] f = ema(close, fast);
        double[] s = ema(close, slow);
        double[] line = nan(close.length);
        for (int i = 0; i < close.length; i++) {
            if (!Double.isNaN(f[i]) && !Double.isNaN(s[i])) {
                line[i] = f[i] - s[i];
            }
        }
        double[] sig = ema(line, signal);
        double[] hist = nan(close.length);
        for (int i = 0; i < close.length; i++) {
            if (!Double.isNaN(line[i]) && !Double.isNaN(sig[i])) {
                hist[i] = line[i] - sig[i];
            }
        }
        return new Macd(line, sig, hist);
    }

    public static Bands bollinger(double[] close, int n, double k) {
        double[] mid = sma(close, n);
        double[] upper = nan(close.length);
        double[] lower = nan(close.length);
        for (int i = n - 1; i < close.length; i++) {
            double variance = 0;
            for (int j = i - n + 1; j <= i; j++) {
                variance += (close[j] - mid[i]) * (close[j] - mid[i]);
            }
            double sd = Math.sqrt(variance / n);
            upper[i] = mid[i] + k * sd;
            lower[i] = mid[i] - k * sd;
        }
        return new Bands(mid, upper, lower);
    }

    /** Wilder's average true range: how far price typically moves in one candle. */
    public static double[] atr(List<Candle> candles, int n) {
        int size = candles.size();
        double[] out = nan(size);
        if (size < n) {
            return out;
        }
        double[] tr = new double[size];
        for (int i = 0; i < size; i++) {
            Candle c = candles.get(i);
            double range = c.high() - c.low();
            if (i > 0) {
                double previous = candles.get(i - 1).close();
                range = Math.max(range, Math.max(Math.abs(c.high() - previous), Math.abs(c.low() - previous)));
            }
            tr[i] = range;
        }
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += tr[i];
        }
        out[n - 1] = sum / n;
        for (int i = n; i < size; i++) {
            out[i] = (out[i - 1] * (n - 1) + tr[i]) / n;
        }
        return out;
    }

    /** The last defined value, or {@code NaN}. */
    public static double last(double[] x) {
        for (int i = x.length - 1; i >= 0; i--) {
            if (!Double.isNaN(x[i])) {
                return x[i];
            }
        }
        return Double.NaN;
    }

    /** The value at {@code i}, or the nearest defined one before it; {@code NaN} if none. */
    public static double at(double[] x, int i) {
        for (int j = Math.min(i, x.length - 1); j >= 0; j--) {
            if (!Double.isNaN(x[j])) {
                return x[j];
            }
        }
        return Double.NaN;
    }

    private static int firstRun(double[] x, int n) {
        int run = 0;
        for (int i = 0; i < x.length; i++) {
            run = Double.isNaN(x[i]) ? 0 : run + 1;
            if (run == n) {
                return i - n + 1;
            }
        }
        return -1;
    }

    static double[] nan(int length) {
        double[] out = new double[length];
        Arrays.fill(out, Double.NaN);
        return out;
    }
}
