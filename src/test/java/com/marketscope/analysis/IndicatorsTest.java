package com.marketscope.analysis;

import com.marketscope.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorsTest {

    private static final double[] ONE_TO_TEN = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    @Test
    void simpleAverage() {
        double[] sma = Indicators.sma(ONE_TO_TEN, 3);
        assertTrue(Double.isNaN(sma[1]), "undefined until three values exist");
        assertEquals(2, sma[2], 1e-12);
        assertEquals(9, sma[9], 1e-12);
    }

    @Test
    void exponentialAverageIsSeededWithTheSimpleOne() {
        double[] ema = Indicators.ema(ONE_TO_TEN, 3);
        assertEquals(2, ema[2], 1e-12, "seed = mean of 1, 2, 3");
        assertEquals(9, ema[9], 1e-12, "k = 0.5 on a straight line lags by exactly one");
        double[] withGap = {Double.NaN, Double.NaN, 4, 4, 4, 4};
        assertEquals(4, Indicators.ema(withGap, 3)[4], 1e-12, "leading NaNs are skipped");
    }

    @Test
    void rsiMatchesWildersWorkedExample() {
        // The classic 14-period example (StockCharts). Its average gain is 3.34 / 14 = 0.2386 and its
        // average loss 1.40 / 14 = 0.10; the article rounds the gain to 0.24 and prints 70.53, the
        // exact value is 100 - 100 / (1 + 2.386) = 70.46.
        double[] close = {44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03,
                45.61, 46.28, 46.28};
        assertEquals(70.464, Indicators.rsi(close, 14)[14], 0.001);
        assertEquals(100, Indicators.rsi(ONE_TO_TEN, 3)[9], 1e-9, "only gains");
    }

    @Test
    void flatPricesGiveFlatIndicators() {
        double[] flat = new double[60];
        java.util.Arrays.fill(flat, 7);
        Indicators.Bands bands = Indicators.bollinger(flat, 20, 2);
        assertEquals(7, bands.upper()[59], 1e-12);
        assertEquals(7, bands.lower()[59], 1e-12);
        assertEquals(0, Indicators.macd(flat, 12, 26, 9).histogram()[59], 1e-12);
        assertEquals(50, Indicators.rsi(flat, 14)[59], 1e-12, "no gains and no losses");
    }

    @Test
    void averageTrueRangeOfSteadyCandles() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candles.add(new Candle(Instant.ofEpochSecond(i * 86_400L), 10, 11, 9, 10, 1));
        }
        assertEquals(2, Indicators.last(Indicators.atr(candles, 14)), 1e-12);
    }
}
