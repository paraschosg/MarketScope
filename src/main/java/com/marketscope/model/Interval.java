package com.marketscope.model;

import java.time.Duration;

/**
 * A candle size. Each also fixes the look-ahead the pattern statistics use: how many candles
 * later "did the price go the way the pattern suggested" is checked.
 */
public enum Interval {
    H1("1H", Duration.ofHours(1), "1h", "1h", "60d", 24, "1 day"),
    H4("4H", Duration.ofHours(4), "4h", "1h", "180d", 12, "2 days"),
    D1("1D", Duration.ofDays(1), "1d", "1d", "5y", 10, "10 days"),
    W1("1W", Duration.ofDays(7), "1w", "1wk", "max", 4, "4 weeks");

    private final String label;
    private final Duration length;
    private final String binance;
    private final String yahoo;
    private final String yahooRange;
    private final int horizonBars;
    private final String horizonLabel;

    Interval(String label, Duration length, String binance, String yahoo, String yahooRange, int horizonBars,
             String horizonLabel) {
        this.label = label;
        this.length = length;
        this.binance = binance;
        this.yahoo = yahoo;
        this.yahooRange = yahooRange;
        this.horizonBars = horizonBars;
        this.horizonLabel = horizonLabel;
    }

    public String label() {
        return label;
    }

    public Duration length() {
        return length;
    }

    /** Binance's name for this interval. */
    public String binance() {
        return binance;
    }

    /** The interval asked of Yahoo; 4H is built from 1H candles. */
    public String yahoo() {
        return yahoo;
    }

    public String yahooRange() {
        return yahooRange;
    }

    /** Candles until a pattern's outcome is checked. */
    public int horizonBars() {
        return horizonBars;
    }

    /** That look-ahead in words, e.g. "10 days". */
    public String horizonLabel() {
        return horizonLabel;
    }
}
