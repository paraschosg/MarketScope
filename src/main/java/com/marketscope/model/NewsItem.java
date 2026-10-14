package com.marketscope.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A headline about an asset or its market.
 *
 * @param published  null when the feed gave no date
 * @param sentiment  -1 (negative) to +1 (positive); 0 neutral or unknown
 */
public record NewsItem(String title, String link, String source, Instant published, double sentiment) {

    public NewsItem {
        title = Objects.requireNonNull(title, "title").strip();
        link = link == null ? "" : link.strip();
        source = source == null ? "" : source.strip();
        sentiment = Math.clamp(sentiment, -1, 1);
    }

    public NewsItem withSentiment(double score) {
        return new NewsItem(title, link, source, published, score);
    }
}
