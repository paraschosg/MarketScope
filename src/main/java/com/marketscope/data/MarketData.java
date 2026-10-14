package com.marketscope.data;

import com.marketscope.model.Asset;
import com.marketscope.model.Candle;
import com.marketscope.model.Interval;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads candles from the best source for an asset, falling back to the next one when a source
 * fails, and keeps answers for a minute so switching back and forth does not hammer the
 * services. Thread-safe.
 */
public final class MarketData {

    static final Duration CACHE_FOR = Duration.ofSeconds(60);

    /** Candles and where they came from. */
    public record Series(Asset asset, Interval interval, List<Candle> candles, String source, Instant readAt) {

        public Series {
            candles = List.copyOf(candles);
        }

        public Candle last() {
            return candles.getLast();
        }

        /** Change of the last close against the one before, in percent; 0 with fewer than two candles. */
        public double changePercent() {
            if (candles.size() < 2) {
                return 0;
            }
            double previous = candles.get(candles.size() - 2).close();
            return previous == 0 ? 0 : (last().close() / previous - 1) * 100;
        }
    }

    private record Key(Asset asset, Interval interval) {
    }

    private final List<PriceSource> sources;
    private final Clock clock;
    private final Map<Key, Series> cache = new ConcurrentHashMap<>();

    public MarketData() {
        this(List.of(new BinanceSource(), new YahooSource(), new FrankfurterSource()), Clock.systemUTC());
    }

    public MarketData(List<PriceSource> sources, Clock clock) {
        this.sources = List.copyOf(sources);
        this.clock = clock;
    }

    /** @param fresh true to skip the cache */
    public Series series(Asset asset, Interval interval, boolean fresh) throws IOException {
        Key key = new Key(asset, interval);
        Series cached = cache.get(key);
        Instant now = clock.instant();
        if (!fresh && cached != null && cached.readAt().plus(CACHE_FOR).isAfter(now)) {
            return cached;
        }
        List<String> problems = new ArrayList<>();
        for (PriceSource source : sources) {
            if (!source.supports(asset, interval)) {
                continue;
            }
            try {
                List<Candle> candles = source.candles(asset, interval);
                if (candles.size() < 2) {
                    problems.add(source.name() + ": too little data");
                    continue;
                }
                Series series = new Series(asset, interval, candles, source.name(), now);
                cache.put(key, series);
                return series;
            } catch (IOException | RuntimeException e) {
                problems.add(source.name() + ": " + e.getMessage());
            }
        }
        throw new IOException("No prices for " + asset.displaySymbol() + ". " + String.join(" · ", problems));
    }
}
