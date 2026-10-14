package com.marketscope;

import com.marketscope.analysis.Analysis;
import com.marketscope.analysis.Backtest;
import com.marketscope.analysis.Levels;
import com.marketscope.analysis.Outlook;
import com.marketscope.analysis.Pattern;
import com.marketscope.data.MarketData;
import com.marketscope.data.NewsFeed;
import com.marketscope.model.Asset;
import com.marketscope.model.Interval;
import com.marketscope.model.NewsItem;
import com.marketscope.ui.Format;

import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;

/** The analysis of one asset as plain text: {@code --report BTC 1D}. */
final class Report {

    private Report() {
    }

    static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: --report SYMBOL [1H|4H|1D|1W]   e.g. --report BTC 1D, --report AAPL, --report EURUSD");
            return;
        }
        Asset asset = Asset.parse(args[0]);
        Interval interval = args.length > 1 ? Interval.valueOf(switch (args[1].toUpperCase(Locale.ROOT)) {
            case "1H" -> "H1";
            case "4H" -> "H4";
            case "1W" -> "W1";
            default -> "D1";
        }) : Interval.D1;
        MarketData.Series series = new MarketData().series(asset, interval, true);
        NewsFeed.News news = new NewsFeed().news(asset);
        Analysis analysis = Analysis.of(series.candles(), interval,
                asset.kind() != com.marketscope.model.AssetKind.FOREX, news.items(), Instant.now());

        System.out.println(asset.name() + " (" + asset.displaySymbol() + ", " + asset.kind().label() + ") · "
                + interval.label() + " · " + series.candles().size() + " candles from " + series.source());
        System.out.println("Last " + Format.price(series.last().close()) + "  " + Format.change(series.changePercent())
                + " on the last candle");
        Outlook o = analysis.outlook();
        System.out.println();
        System.out.println("OUTLOOK: " + o.lean().label() + "  (score " + String.format(Locale.ROOT, "%+.2f", o.score())
                + ")");
        System.out.println("  " + o.summary());
        for (Outlook.Factor f : o.factors()) {
            String arrow = f.direction() > 0 ? "▲" : f.direction() < 0 ? "▼" : "·";
            System.out.println("  " + arrow + " " + f.name() + ": " + f.reading());
        }
        System.out.println();
        System.out.println("PATTERNS IN PLAY (last " + interval.horizonLabel() + "):");
        if (analysis.recentPatterns().isEmpty()) {
            System.out.println("  none");
        }
        for (Pattern p : analysis.recentPatterns()) {
            System.out.println("  " + p.kind().label() + " at " + Format.candleTime(
                    series.candles().get(p.confirm()).time(), interval, java.time.ZoneOffset.UTC));
        }
        System.out.println();
        System.out.println("TRACK RECORD ON THIS CHART (" + interval.horizonLabel() + " later):");
        System.out.printf(Locale.ROOT, "  %-28s %6s %7s %7s %8s%n", "pattern", "cases", "right", "any", "edge");
        analysis.stats().values().stream().filter(s -> s.cases() > 0)
                .sorted(Comparator.comparing(Backtest.Stats::enoughCases).reversed()
                        .thenComparing(Comparator.comparingDouble(Backtest.Stats::edge).reversed()))
                .forEach(s -> System.out.printf(Locale.ROOT, "  %-28s %6d %7s %7s %8s%n", s.kind().label(), s.cases(),
                        Format.percent(s.hitRate()), Format.percent(s.baseRate()),
                        (s.enoughCases() ? Format.points(s.edge()) : "few") + (s.beatsChance() ? "  *" : "")));
        System.out.println("  * beats chance on this chart (5+ points, 10+ cases, unlikely to be luck)");
        System.out.println();
        System.out.println("LEVELS:");
        for (Levels.Level l : analysis.levels()) {
            System.out.println("  " + (l.support() ? "support    " : "resistance ") + Format.price(l.price()) + "  ("
                    + l.touches() + " touches)");
        }
        System.out.println();
        System.out.println("NEWS: " + news.items().size() + " headlines, average sentiment "
                + String.format(Locale.ROOT, "%+.2f", news.averageSentiment()));
        for (NewsItem n : news.items().stream().limit(8).toList()) {
            System.out.println("  [" + String.format(Locale.ROOT, "%+.2f", n.sentiment()) + "] " + n.title() + "  ("
                    + n.source() + ")");
        }
        for (String problem : news.problems()) {
            System.out.println("  not read: " + problem);
        }
        System.out.println();
        System.out.println("Analysis, not financial advice. Patterns describe the past; they do not promise the future.");
    }
}
