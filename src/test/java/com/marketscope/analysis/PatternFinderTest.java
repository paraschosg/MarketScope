package com.marketscope.analysis;

import com.marketscope.model.Candle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternFinderTest {

    /** Sideways, down to 100, up to 110, back to ~100.5, then up through 110. */
    private static List<Candle> doubleBottom() {
        double[] flat = new double[40];
        for (int i = 0; i < flat.length; i++) {
            flat[i] = 120 + (i % 2 == 0 ? 0.4 : -0.4);
        }
        return Series.path(Series.join(flat, Series.line(120, 100, 15), Series.line(100, 110, 8),
                Series.line(110, 100.5, 8), Series.line(100.5, 118, 14)));
    }

    @Test
    void findsADoubleBottomWhenPriceClosesAboveTheBounce() {
        List<Candle> candles = doubleBottom();
        List<Pattern> bottoms = PatternFinder.find(candles).stream()
                .filter(p -> p.kind() == PatternKind.DOUBLE_BOTTOM).toList();
        assertEquals(1, bottoms.size(), "one double bottom");
        Pattern p = bottoms.getFirst();
        double neckline = p.level();
        assertEquals(110.3, neckline, 1e-9, "the high of the bounce between the lows");
        assertTrue(candles.get(p.confirm()).close() > neckline, "confirmed by a close above it");
        assertTrue(candles.get(p.confirm() - 1).close() <= neckline, "on the first such close");
        assertEquals(4, p.points().size());
    }

    @Test
    void anUnconfirmedShapeIsNotAPattern() {
        List<Candle> candles = doubleBottom();
        // Stop before price got back above the bounce.
        List<Candle> early = candles.subList(0, 40 + 15 + 8 + 8 + 4);
        assertFalse(PatternFinder.find(early).stream().anyMatch(p -> p.kind() == PatternKind.DOUBLE_BOTTOM));
    }

    /**
     * The guarantee the statistics rest on: running the finder on the history up to any candle
     * gives exactly the patterns the full history has up to that candle. Nothing found depends
     * on what came later, and nothing found later changes the past.
     */
    @Test
    void thePastNeverChangesAsNewCandlesArrive() {
        for (long seed = 1; seed <= 4; seed++) {
            List<Candle> all = Series.randomWalk(seed, 700);
            List<Pattern> full = PatternFinder.find(all);
            assertTrue(full.size() > 20, "the walk should produce some patterns, got " + full.size());
            for (int end = 120; end <= 700; end += 37) {
                int last = end - 1;
                Set<String> fromFull = full.stream().filter(p -> p.confirm() <= last).map(PatternFinderTest::key)
                        .collect(Collectors.toSet());
                Set<String> fromPrefix = PatternFinder.find(all.subList(0, end)).stream()
                        .map(PatternFinderTest::key).collect(Collectors.toSet());
                assertEquals(fromFull, fromPrefix, "seed " + seed + ", history up to candle " + last);
            }
        }
    }

    private static String key(Pattern p) {
        return p.kind() + "@" + p.start() + ".." + p.confirm();
    }
}
