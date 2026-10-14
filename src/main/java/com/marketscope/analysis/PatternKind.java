package com.marketscope.analysis;

/** The chart patterns MarketScope recognises, each with the move it is traditionally read as. */
public enum PatternKind {
    DOUBLE_TOP("Double top", -1,
            "Two similar peaks with a dip between them; confirmed when price closes below that dip."),
    DOUBLE_BOTTOM("Double bottom", 1,
            "Two similar lows with a bounce between them; confirmed when price closes above that bounce."),
    HEAD_AND_SHOULDERS("Head and shoulders", -1,
            "A high peak between two lower, similar ones; confirmed by a close below the line joining the dips."),
    INVERSE_HEAD_AND_SHOULDERS("Inverse head and shoulders", 1,
            "A deep low between two higher, similar ones; confirmed by a close above the line joining the bounces."),
    BREAKOUT_UP("Breakout up", 1,
            "The first close above the highest high of the previous 20 candles, on above-average volume."),
    BREAKOUT_DOWN("Breakdown", -1,
            "The first close below the lowest low of the previous 20 candles, on above-average volume."),
    GOLDEN_CROSS("Golden cross", 1,
            "The 50-candle average crosses above the 200-candle average."),
    DEATH_CROSS("Death cross", -1,
            "The 50-candle average crosses below the 200-candle average."),
    RSI_OVERSOLD_EXIT("RSI leaves oversold", 1,
            "RSI climbs back above 30 after being below it."),
    RSI_OVERBOUGHT_EXIT("RSI leaves overbought", -1,
            "RSI falls back below 70 after being above it."),
    BULLISH_ENGULFING("Bullish engulfing", 1,
            "After a decline, a rising candle whose body swallows the previous falling one."),
    BEARISH_ENGULFING("Bearish engulfing", -1,
            "After a rise, a falling candle whose body swallows the previous rising one.");

    private final String label;
    private final int direction;
    private final String description;

    PatternKind(String label, int direction, String description) {
        this.label = label;
        this.direction = direction;
        this.description = description;
    }

    public String label() {
        return label;
    }

    /** +1 when traditionally read as bullish, -1 as bearish. */
    public int direction() {
        return direction;
    }

    public boolean bullish() {
        return direction > 0;
    }

    public String description() {
        return description;
    }
}
