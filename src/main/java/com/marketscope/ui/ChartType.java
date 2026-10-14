package com.marketscope.ui;

import com.marketscope.model.Candle;

import java.util.ArrayList;
import java.util.List;

/** How the price is drawn. Heikin-Ashi changes the candles shown, never the analysis. */
public enum ChartType {
    CANDLES("Candles"),
    BARS("Bars"),
    LINE("Line"),
    AREA("Area"),
    HEIKIN_ASHI("Heikin Ashi");

    private final String label;

    ChartType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The candles to draw for this type. */
    public List<Candle> display(List<Candle> candles) {
        return this == HEIKIN_ASHI ? heikinAshi(candles) : candles;
    }

    /**
     * Heikin-Ashi ("average bar") candles: close = mean of open, high, low and close; open = mean
     * of the previous Heikin-Ashi open and close; high and low stretched to cover both.
     */
    static List<Candle> heikinAshi(List<Candle> candles) {
        List<Candle> out = new ArrayList<>(candles.size());
        double previousOpen = 0;
        double previousClose = 0;
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double close = (c.open() + c.high() + c.low() + c.close()) / 4;
            double open = i == 0 ? (c.open() + c.close()) / 2 : (previousOpen + previousClose) / 2;
            out.add(new Candle(c.time(), open, Math.max(c.high(), Math.max(open, close)),
                    Math.min(c.low(), Math.min(open, close)), close, c.volume()));
            previousOpen = open;
            previousClose = close;
        }
        return out;
    }
}
