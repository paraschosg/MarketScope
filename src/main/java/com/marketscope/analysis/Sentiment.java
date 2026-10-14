package com.marketscope.analysis;

import java.util.Locale;
import java.util.Set;

/**
 * A small, offline finance-headline sentiment scorer: counts market-positive and
 * market-negative words, flips a word that follows a negation ("not", "fails to"), and returns
 * a score from -1 to +1. Crude by design — it reads words, not meaning — so the app shows it as
 * a hint, never as a verdict.
 */
public final class Sentiment {

    /** Scores at or beyond this are shown as positive or negative. */
    public static final double THRESHOLD = 0.25;

    private static final Set<String> POSITIVE = Set.of(
            "surge", "surges", "surged", "soar", "soars", "soared", "rally", "rallies", "rallied", "gain", "gains",
            "gained", "jump", "jumps", "jumped", "climb", "climbs", "climbed", "rise", "rises", "rose", "rising",
            "bullish", "beat", "beats", "upgrade", "upgrades", "upgraded", "outperform", "outperforms", "approval",
            "approved", "approves", "adoption", "profit", "profits", "growth", "strong", "stronger", "boost",
            "boosts", "boosted", "inflow", "inflows", "rebound", "rebounds", "rebounded", "recover", "recovers",
            "recovery", "breakthrough", "buyback", "record_high", "all_time_high", "upbeat", "optimism",
            "optimistic", "tops", "higher", "wins", "accumulate", "accumulation", "partnership", "expands",
            "raises", "raised");

    private static final Set<String> NEGATIVE = Set.of(
            "crash", "crashes", "crashed", "plunge", "plunges", "plunged", "tumble", "tumbles", "tumbled", "slump",
            "slumps", "slumped", "fall", "falls", "fell", "falling", "drop", "drops", "dropped", "sink", "sinks",
            "sank", "decline", "declines", "declined", "loss", "losses", "bearish", "miss", "misses", "missed",
            "downgrade", "downgrades", "downgraded", "lawsuit", "sues", "sued", "hack", "hacked", "exploit",
            "exploited", "breach", "fraud", "scam", "ban", "bans", "banned", "probe", "investigation", "fined",
            "outflow", "outflows", "liquidation", "liquidations", "selloff", "sell_off", "warning", "warns",
            "recession", "weak", "weaker", "layoffs", "bankruptcy", "bankrupt", "default", "delist", "delisted",
            "collapse", "collapses", "collapsed", "fear", "fears", "lower", "slides", "slid", "stolen", "drain",
            "drained", "crackdown", "volatile", "uncertainty", "cuts");

    private static final Set<String> NEGATIONS = Set.of("not", "no", "never", "fails", "fail", "failed",
            "without", "despite", "isn't", "wasn't", "won't", "didn't", "doesn't", "don't");

    private Sentiment() {
    }

    public static double score(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normal = text.toLowerCase(Locale.ROOT)
                .replace("all-time high", "all_time_high").replace("all time high", "all_time_high")
                .replace("record high", "record_high").replace("sell-off", "sell_off")
                .replace('’', '\'');
        String[] tokens = normal.split("[^a-z_']+");
        int positive = 0;
        int negative = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            int polarity = POSITIVE.contains(token) ? 1 : NEGATIVE.contains(token) ? -1 : 0;
            if (polarity == 0) {
                continue;
            }
            if (negated(tokens, i)) {
                polarity = -polarity;
            }
            if (polarity > 0) {
                positive++;
            } else {
                negative++;
            }
        }
        int total = positive + negative;
        return total == 0 ? 0 : (double) (positive - negative) / total;
    }

    private static boolean negated(String[] tokens, int i) {
        for (int j = Math.max(0, i - 3); j < i; j++) {
            if (NEGATIONS.contains(tokens[j])) {
                return true;
            }
        }
        return false;
    }

    /** "Positive", "Negative" or "Neutral". */
    public static String label(double score) {
        return score >= THRESHOLD ? "Positive" : score <= -THRESHOLD ? "Negative" : "Neutral";
    }
}
