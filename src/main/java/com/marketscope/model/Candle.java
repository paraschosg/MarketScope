package com.marketscope.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One period of trading.
 *
 * @param time   when the period opened
 * @param volume traded volume; 0 when the source has none (forex)
 */
public record Candle(Instant time, double open, double high, double low, double close, double volume) {

    public Candle {
        Objects.requireNonNull(time, "time");
        if (!(Double.isFinite(open) && Double.isFinite(high) && Double.isFinite(low) && Double.isFinite(close))) {
            throw new IllegalArgumentException("A candle needs finite prices at " + time);
        }
        // Sources occasionally report a high below the open or close; keep the candle consistent.
        high = Math.max(high, Math.max(open, close));
        low = Math.min(low, Math.min(open, close));
        volume = Double.isFinite(volume) && volume > 0 ? volume : 0;
    }

    public boolean up() {
        return close >= open;
    }
}
