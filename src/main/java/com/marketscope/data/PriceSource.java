package com.marketscope.data;

import com.marketscope.model.Asset;
import com.marketscope.model.Candle;
import com.marketscope.model.Interval;

import java.io.IOException;
import java.util.List;

/** Somewhere candles can be read from. Implementations are thread-safe. */
public interface PriceSource {

    /** A short name for the status line, e.g. "Binance". */
    String name();

    /** Whether this source can serve the asset at all. */
    boolean supports(Asset asset, Interval interval);

    /** Candles oldest first; the last one may still be forming. */
    List<Candle> candles(Asset asset, Interval interval) throws IOException;
}
