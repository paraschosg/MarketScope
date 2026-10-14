package com.marketscope.analysis;

import com.marketscope.model.Interval;
import com.marketscope.model.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestAndOutlookTest {

    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");

    @Test
    void hitRateIsMeasuredAgainstTheBaseRate() {
        // Price rises every candle: anything bullish "works", but so does doing nothing.
        double[] close = new double[40];
        for (int i = 0; i < close.length; i++) {
            close[i] = 100 + i;
        }
        Pattern p = new Pattern(PatternKind.GOLDEN_CROSS, 5, 5, Double.NaN, List.of());
        Backtest.Stats s = Backtest.run(List.of(p), close, 10).get(PatternKind.GOLDEN_CROSS);
        assertEquals(1, s.cases());
        assertEquals(1.0, s.hitRate(), 1e-12);
        assertEquals(1.0, s.baseRate(), 1e-12);
        assertEquals(0, s.edge(), 1e-12, "no better than holding: no edge");
        assertEquals(10.0 / 105, s.meanReturn(), 1e-12);
    }

    @Test
    void occurrencesWithoutAKnownOutcomeAreLeftOut() {
        double[] close = {1, 2, 3, 4, 5, 6};
        Pattern tooLate = new Pattern(PatternKind.DOUBLE_TOP, 4, 4, Double.NaN, List.of());
        assertEquals(0, Backtest.run(List.of(tooLate), close, 3).get(PatternKind.DOUBLE_TOP).cases());
    }

    @Test
    void aPatternOnlyVotesWhenItBeatChanceOnEnoughCases() {
        Backtest.Stats weak = new Backtest.Stats(PatternKind.DOUBLE_BOTTOM, 10, 30, 0.55, 0.53, 0.01, 0.01);
        Backtest.Stats strong = new Backtest.Stats(PatternKind.DOUBLE_BOTTOM, 10, 30, 0.70, 0.53, 0.03, 0.02);
        Backtest.Stats few = new Backtest.Stats(PatternKind.DOUBLE_BOTTOM, 10, 4, 1.0, 0.53, 0.05, 0.05);
        Pattern recent = new Pattern(PatternKind.DOUBLE_BOTTOM, 90, 98, 100, List.of());
        assertEquals(0, factor(recent, weak).weight(), "2 points over the base rate is noise");
        assertTrue(factor(recent, strong).weight() > 0, "17 points over the base rate counts, upward");
        assertEquals(0, factor(recent, few).weight(), "4 cases are too few, however good they look");
        assertTrue(factor(recent, few).reading().contains("too few"));
        Backtest.Stats lucky = new Backtest.Stats(PatternKind.DOUBLE_BOTTOM, 10, 16, 0.5625, 0.47, 0.01, 0.01);
        assertEquals(0, factor(recent, lucky).weight(), "9 of 16 against a 47 % base rate could easily be luck");
        assertTrue(factor(recent, lucky).reading().contains("luck"));
        assertTrue(strong.zScore() > Backtest.MIN_Z);
    }

    @Test
    void headlinesDoNotVoteOnACurrencyPair() {
        double[] close = new double[60];
        for (int i = 0; i < close.length; i++) {
            close[i] = 1.10 + i * 0.001;
        }
        List<NewsItem> good = List.of(
                new NewsItem("Dollar rallies", "", "t", NOW, 1),
                new NewsItem("Dollar surges", "", "t", NOW, 1),
                new NewsItem("Dollar gains", "", "t", NOW, 1));
        Outlook pair = Outlook.of(close, Indicators.sma(close, 50), Indicators.sma(close, 200),
                Indicators.rsi(close, 14), Indicators.macd(close, 12, 26, 9), List.of(), Map.of(), Interval.D1, good,
                NOW, false);
        Outlook.Factor news = pair.factors().stream().filter(f -> f.name().equals("News")).findFirst().orElseThrow();
        assertEquals(0, news.weight());
        assertTrue(news.reading().contains("currency pair"));
    }

    private static Outlook.Factor factor(Pattern p, Backtest.Stats s) {
        return Outlook.patternFactors(List.of(p), Map.of(p.kind(), s), Interval.D1, 100).getFirst();
    }

    @Test
    void outlookLeansOnlyWhenSignalsAgree() {
        int n = 260;
        double[] close = new double[n];
        for (int i = 0; i < n; i++) {
            close[i] = 100 + i * 0.5 + (i % 3 == 0 ? -1 : 1);
        }
        double[] sma50 = Indicators.sma(close, 50);
        double[] sma200 = Indicators.sma(close, 200);
        double[] rsi = Indicators.rsi(close, 14);
        Indicators.Macd macd = Indicators.macd(close, 12, 26, 9);
        List<NewsItem> good = List.of(
                new NewsItem("Bitcoin rallies to record high", "", "t", NOW, 1),
                new NewsItem("ETF inflows surge", "", "t", NOW, 1),
                new NewsItem("Analysts upgrade outlook", "", "t", NOW, 1));
        Outlook up = Outlook.of(close, sma50, sma200, rsi, macd, List.of(), Map.of(), Interval.D1, good, NOW);
        assertEquals(Outlook.Lean.UP, up.lean(), up.summary());

        Outlook quiet = Outlook.of(close, sma50, sma200, rsi, macd, List.of(), Map.of(), Interval.D1, List.of(),
                NOW);
        assertTrue(quiet.factors().stream().anyMatch(f -> f.name().equals("News") && f.weight() == 0));
    }

    @Test
    void headlineSentiment() {
        assertTrue(Sentiment.score("Bitcoin surges to an all-time high as ETF inflows jump") > 0.9);
        assertTrue(Sentiment.score("Exchange hacked, tokens plunge after exploit") < -0.9);
        assertEquals(0, Sentiment.score("Apple to hold its annual event on Tuesday"), 1e-12);
        assertTrue(Sentiment.score("Shares fail to rally") < 0, "a negated rally is not good news");
        assertEquals("Neutral", Sentiment.label(0.1));
    }
}
