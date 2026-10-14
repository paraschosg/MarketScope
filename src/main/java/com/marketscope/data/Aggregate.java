package com.marketscope.data;

import com.marketscope.model.Candle;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds longer candles out of shorter ones. */
final class Aggregate {

    private Aggregate() {
    }

    /** Groups candles into buckets of {@code period} counted from the epoch (UTC). */
    static List<Candle> byPeriod(List<Candle> candles, Duration period) {
        List<Candle> out = new ArrayList<>();
        long size = period.getSeconds();
        long bucket = Long.MIN_VALUE;
        double open = 0;
        double high = 0;
        double low = 0;
        double close = 0;
        double volume = 0;
        for (Candle c : candles) {
            long b = Math.floorDiv(c.time().getEpochSecond(), size);
            if (b != bucket) {
                if (bucket != Long.MIN_VALUE) {
                    out.add(new Candle(Instant.ofEpochSecond(bucket * size), open, high, low, close, volume));
                }
                bucket = b;
                open = c.open();
                high = c.high();
                low = c.low();
                volume = 0;
            }
            high = Math.max(high, c.high());
            low = Math.min(low, c.low());
            close = c.close();
            volume += c.volume();
        }
        if (bucket != Long.MIN_VALUE) {
            out.add(new Candle(Instant.ofEpochSecond(bucket * size), open, high, low, close, volume));
        }
        return out;
    }
}
