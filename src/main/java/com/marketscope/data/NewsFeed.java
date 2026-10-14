package com.marketscope.data;

import com.marketscope.analysis.Sentiment;
import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;
import com.marketscope.model.NewsItem;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Headlines for an asset from public RSS feeds, scored for sentiment. Yahoo's per-symbol feed
 * is about the asset itself; the crypto news sites cover the whole market, so only their
 * headlines that mention the coin are kept.
 */
public final class NewsFeed {

    public static final int MAX_ITEMS = 40;

    /** Headlines and the feeds that could not be read. */
    public record News(List<NewsItem> items, List<String> problems) {

        public News {
            items = List.copyOf(items);
            problems = List.copyOf(problems);
        }

        /** Mean sentiment of the headlines that have any; 0 when none do. */
        public double averageSentiment() {
            return items.stream().filter(i -> i.sentiment() != 0).mapToDouble(NewsItem::sentiment).average()
                    .orElse(0);
        }
    }

    record Feed(String name, String url, boolean general) {
    }

    public News news(Asset asset) {
        List<NewsItem> all = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Feed feed : feeds(asset)) {
            try {
                for (RssParser.Item item : RssParser.parse(Http.get(feed.url()))) {
                    if (feed.general() && !mentions(item, asset)) {
                        continue;
                    }
                    all.add(new NewsItem(item.title(), item.link(), feed.name(), item.published(),
                            Sentiment.score(item.title())));
                }
            } catch (IOException | RuntimeException e) {
                problems.add(feed.name() + ": " + e.getMessage());
            }
        }
        return new News(dedupe(all), problems);
    }

    static List<Feed> feeds(Asset asset) {
        String yahoo = "https://feeds.finance.yahoo.com/rss/2.0/headline?s="
                + java.net.URLEncoder.encode(asset.yahooSymbol(), java.nio.charset.StandardCharsets.UTF_8)
                + "&region=US&lang=en-US";
        List<Feed> feeds = new ArrayList<>();
        feeds.add(new Feed("Yahoo Finance", yahoo, false));
        if (asset.kind() == AssetKind.CRYPTO) {
            feeds.add(new Feed("Cointelegraph", "https://cointelegraph.com/rss", true));
            feeds.add(new Feed("CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/", true));
        }
        return feeds;
    }

    /** Whether a general crypto headline is about this coin, by name or ticker as a whole word. */
    static boolean mentions(RssParser.Item item, Asset asset) {
        String text = (item.title() + " " + item.summary()).toLowerCase(Locale.ROOT);
        String base = asset.base().toLowerCase(Locale.ROOT);
        String name = asset.name().toLowerCase(Locale.ROOT);
        return word(text, base) || (!name.equals(base) && word(text, name));
    }

    private static boolean word(String text, String needle) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(needle) + "(?![a-z0-9])").matcher(text).find();
    }

    /** One copy of each headline, newest first, at most {@link #MAX_ITEMS}. */
    static List<NewsItem> dedupe(List<NewsItem> items) {
        Map<String, NewsItem> unique = new LinkedHashMap<>();
        for (NewsItem item : items) {
            unique.putIfAbsent(item.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""), item);
        }
        return unique.values().stream()
                .sorted(Comparator.comparing((NewsItem i) -> i.published() == null ? Instant.EPOCH : i.published())
                        .reversed())
                .limit(MAX_ITEMS)
                .toList();
    }
}
