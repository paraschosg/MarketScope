package com.marketscope.analysis;

import com.marketscope.model.Interval;
import com.marketscope.model.NewsItem;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sums up what the chart and the headlines say right now, as a lean (up, down or none) and the
 * factors behind it. Deliberately modest: a recent pattern only counts when, on this very
 * asset's history, it beat the base rate over enough cases; the headline score counts for
 * less than the chart; and the result is a reading, never a price target or advice. Pure.
 */
public record Outlook(Lean lean, double score, int agreeing, int voting, List<Factor> factors, String summary) {

    /** Score at or beyond which the outlook leans one way. */
    public static final double LEAN_AT = 1.0;
    /** Headlines this recent are weighed. */
    public static final Duration NEWS_WINDOW = Duration.ofDays(3);

    public enum Lean {
        UP("Leans up"),
        DOWN("Leans down"),
        MIXED("No clear lean");

        private final String label;

        Lean(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * One input to the outlook.
     *
     * @param weight positive pushes up, negative down, 0 is shown but does not vote
     */
    public record Factor(String name, String reading, double weight) {

        public int direction() {
            return (int) Math.signum(weight);
        }
    }

    public Outlook {
        factors = List.copyOf(factors);
    }

    public static Outlook of(double[] close, double[] sma50, double[] sma200, double[] rsi, Indicators.Macd macd,
                             List<Pattern> patterns, Map<PatternKind, Backtest.Stats> stats, Interval interval,
                             List<NewsItem> news, Instant now) {
        return of(close, sma50, sma200, rsi, macd, patterns, stats, interval, news, now, true);
    }

    /**
     * @param newsHasDirection false for currency pairs: good news for one currency is bad news for
     *                         the pair's other side, so headline tone says nothing about the pair
     */
    public static Outlook of(double[] close, double[] sma50, double[] sma200, double[] rsi, Indicators.Macd macd,
                             List<Pattern> patterns, Map<PatternKind, Backtest.Stats> stats, Interval interval,
                             List<NewsItem> news, Instant now, boolean newsHasDirection) {
        List<Factor> factors = new ArrayList<>();
        int last = close.length - 1;
        factors.add(trend(close[last], sma50[last], sma200[last]));
        factors.add(momentum(macd.histogram()[last]));
        factors.add(rsiFactor(rsi[last]));
        factors.addAll(patternFactors(patterns, stats, interval, last));
        Factor headlines = newsFactor(news, now);
        factors.add(newsHasDirection || headlines.weight() == 0 ? headlines
                : new Factor("News", headlines.reading() + " For a currency pair, good news for one side is bad news"
                        + " for the other, so headlines are shown but not weighed.", 0));

        double score = factors.stream().mapToDouble(Factor::weight).sum();
        Lean lean = score >= LEAN_AT ? Lean.UP : score <= -LEAN_AT ? Lean.DOWN : Lean.MIXED;
        int voting = (int) factors.stream().filter(f -> f.weight() != 0).count();
        int up = (int) factors.stream().filter(f -> f.weight() > 0).count();
        int down = (int) factors.stream().filter(f -> f.weight() < 0).count();
        int agreeing = lean == Lean.UP ? up : lean == Lean.DOWN ? down : Math.max(up, down);
        String summary = switch (lean) {
            case UP -> "Leans up: " + agreeing + " of " + voting + " signals point up.";
            case DOWN -> "Leans down: " + agreeing + " of " + voting + " signals point down.";
            case MIXED -> voting == 0 ? "No clear lean: nothing stands out right now."
                    : "No clear lean: " + up + " signal" + (up == 1 ? "" : "s") + " up, " + down + " down, or too weak.";
        };
        return new Outlook(lean, score, agreeing, voting, factors, summary);
    }

    static Factor trend(double close, double sma50, double sma200) {
        if (!Double.isNaN(sma200) && !Double.isNaN(sma50)) {
            if (close > sma200 && sma50 > sma200) {
                return new Factor("Trend", "Price is above its 200-candle average and the 50 is above the 200:"
                        + " an uptrend.", 1);
            }
            if (close < sma200 && sma50 < sma200) {
                return new Factor("Trend", "Price is below its 200-candle average and the 50 is below the 200:"
                        + " a downtrend.", -1);
            }
            return new Factor("Trend", "Price and its 50- and 200-candle averages disagree: no clear trend.", 0);
        }
        if (!Double.isNaN(sma50)) {
            return close > sma50
                    ? new Factor("Trend", "Above its 50-candle average (too little history for the 200).", 0.5)
                    : new Factor("Trend", "Below its 50-candle average (too little history for the 200).", -0.5);
        }
        return new Factor("Trend", "Too little history to tell a trend.", 0);
    }

    static Factor momentum(double histogram) {
        if (Double.isNaN(histogram)) {
            return new Factor("Momentum", "Too little history for MACD.", 0);
        }
        return histogram > 0
                ? new Factor("Momentum", "MACD is above its signal line: momentum is up.", 0.5)
                : new Factor("Momentum", "MACD is below its signal line: momentum is down.", -0.5);
    }

    static Factor rsiFactor(double rsi) {
        if (Double.isNaN(rsi)) {
            return new Factor("RSI", "Too little history for RSI.", 0);
        }
        String value = String.format(Locale.ROOT, "%.0f", rsi);
        if (rsi > 70) {
            return new Factor("RSI", "RSI " + value + ": overbought. The price is stretched; pullbacks are common.",
                    -0.5);
        }
        if (rsi < 30) {
            return new Factor("RSI", "RSI " + value + ": oversold. The price is stretched; bounces are common.",
                    0.5);
        }
        return new Factor("RSI", "RSI " + value + ": neither overbought nor oversold.", 0);
    }

    /** The latest recent occurrence of each pattern, weighed by how it did on this asset before. */
    static List<Factor> patternFactors(List<Pattern> patterns, Map<PatternKind, Backtest.Stats> stats,
                                       Interval interval, int last) {
        Map<PatternKind, Pattern> recent = new LinkedHashMap<>();
        for (Pattern p : patterns) {
            if (p.confirm() > last - interval.horizonBars()) {
                recent.put(p.kind(), p);
            }
        }
        List<Factor> out = new ArrayList<>();
        for (Pattern p : recent.values()) {
            Backtest.Stats s = stats.get(p.kind());
            String when = ago(last - p.confirm());
            String name = p.kind().label() + " " + when;
            if (s == null || !s.enoughCases()) {
                int cases = s == null ? 0 : s.cases();
                out.add(new Factor(name, "Only " + cases + " earlier case" + (cases == 1 ? "" : "s")
                        + " on this chart: too few to judge.", 0));
                continue;
            }
            String move = p.kind().bullish() ? "up" : "down";
            String record = "Before, it was followed by a move " + move + " " + interval.horizonLabel() + " later "
                    + percent(s.hitRate()) + " of the time, against " + percent(s.baseRate())
                    + " after any candle (" + s.cases() + " cases)";
            if (s.beatsChance()) {
                out.add(new Factor(name, record + ".", p.kind().direction() * Math.min(1, s.edge() * 6)));
            } else if (s.edge() >= Backtest.MIN_EDGE) {
                out.add(new Factor(name, record + ": a lead that could easily be luck with this few cases, so it"
                        + " does not count.", 0));
            } else {
                out.add(new Factor(name, record + ": no better than chance here, so it does not count.", 0));
            }
        }
        return out;
    }

    static Factor newsFactor(List<NewsItem> news, Instant now) {
        List<NewsItem> recent = news.stream()
                .filter(n -> n.published() == null || !n.published().isBefore(now.minus(NEWS_WINDOW)))
                .toList();
        List<NewsItem> scored = recent.stream().filter(n -> n.sentiment() != 0).toList();
        if (recent.size() < 3 || scored.size() < 2) {
            return new Factor("News", recent.size() + " headline" + (recent.size() == 1 ? "" : "s")
                    + " in the last 3 days: too few to weigh.", 0);
        }
        double average = scored.stream().mapToDouble(NewsItem::sentiment).average().orElse(0);
        String tone = Sentiment.label(average).toLowerCase(Locale.ROOT);
        String reading = recent.size() + " headlines in the last 3 days, on balance " + tone + " ("
                + String.format(Locale.ROOT, "%+.2f", average) + "). Word-based scoring: a hint only.";
        double weight = Math.abs(average) < 0.1 ? 0 : Math.clamp(average, -0.6, 0.6);
        return new Factor("News", reading, weight);
    }

    private static String ago(int candles) {
        return candles == 0 ? "on the last candle" : candles == 1 ? "1 candle ago" : candles + " candles ago";
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.0f %%", fraction * 100);
    }
}
