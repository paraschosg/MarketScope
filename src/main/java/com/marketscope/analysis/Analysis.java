package com.marketscope.analysis;

import com.marketscope.model.Candle;
import com.marketscope.model.Interval;
import com.marketscope.model.NewsItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything worked out for one chart: indicators, patterns with their track record on this
 * asset, support and resistance, and the outlook. Built on a background thread, read on the UI
 * thread; immutable apart from the arrays, which nobody writes after construction.
 */
public record Analysis(Interval interval, boolean newsHasDirection, List<Candle> candles, double[] sma20,
                       double[] sma50, double[] sma200, Indicators.Bands bollinger, double[] rsi, Indicators.Macd macd,
                       List<Pattern> patterns, Map<PatternKind, Backtest.Stats> stats, List<Levels.Level> levels,
                       Outlook outlook) {

    public Analysis {
        candles = List.copyOf(candles);
        patterns = List.copyOf(patterns);
        stats = Map.copyOf(stats);
        levels = List.copyOf(levels);
    }

    public static Analysis of(List<Candle> candles, Interval interval, List<NewsItem> news, Instant now) {
        return of(candles, interval, true, news, now);
    }

    /** @param newsHasDirection false for currency pairs; see {@link Outlook#of}. */
    public static Analysis of(List<Candle> candles, Interval interval, boolean newsHasDirection, List<NewsItem> news,
                              Instant now) {
        double[] close = Indicators.closes(candles);
        double[] sma50 = Indicators.sma(close, 50);
        double[] sma200 = Indicators.sma(close, 200);
        double[] rsi = Indicators.rsi(close, 14);
        Indicators.Macd macd = Indicators.macd(close, 12, 26, 9);
        List<Pattern> patterns = PatternFinder.find(candles);
        Map<PatternKind, Backtest.Stats> stats = Backtest.run(patterns, close, interval.horizonBars());
        Outlook outlook = Outlook.of(close, sma50, sma200, rsi, macd, patterns, stats, interval, news, now,
                newsHasDirection);
        return new Analysis(interval, newsHasDirection, candles, Indicators.sma(close, 20), sma50, sma200,
                Indicators.bollinger(close, 20, 2), rsi, macd, patterns, stats, Levels.find(candles), outlook);
    }

    /** The same analysis with the outlook redone for newly arrived headlines. */
    public Analysis withNews(List<NewsItem> news, Instant now) {
        double[] close = Indicators.closes(candles);
        Outlook again = Outlook.of(close, sma50, sma200, rsi, macd, patterns, stats, interval, news, now,
                newsHasDirection);
        return new Analysis(interval, newsHasDirection, candles, sma20, sma50, sma200, bollinger, rsi, macd, patterns,
                stats, levels, again);
    }

    /**
     * The latest occurrence of each pattern confirmed within the look-ahead window, newest first:
     * the ones still "in play".
     */
    public List<Pattern> recentPatterns() {
        int last = candles.size() - 1;
        Map<PatternKind, Pattern> latest = new java.util.EnumMap<>(PatternKind.class);
        for (Pattern p : patterns) {
            if (p.confirm() > last - interval.horizonBars()) {
                latest.put(p.kind(), p);
            }
        }
        return latest.values().stream().sorted((a, b) -> Integer.compare(b.confirm(), a.confirm())).toList();
    }
}
