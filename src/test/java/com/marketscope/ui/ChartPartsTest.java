package com.marketscope.ui;

import com.marketscope.model.Candle;
import com.marketscope.model.Interval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChartPartsTest {

    private static final Instant T0 = Instant.parse("2026-10-12T00:00:00Z");

    private static Candle day(int i, double o, double h, double l, double c) {
        return new Candle(T0.plusSeconds(86_400L * i), o, h, l, c, 1);
    }

    @Test
    void heikinAshiAveragesTheCandles() {
        List<Candle> ha = ChartType.heikinAshi(List.of(day(0, 10, 12, 9, 11), day(1, 11, 14, 10, 13)));
        assertEquals((10 + 12 + 9 + 11) / 4.0, ha.get(0).close(), 1e-12);
        assertEquals((10 + 11) / 2.0, ha.get(0).open(), 1e-12);
        double secondOpen = (ha.get(0).open() + ha.get(0).close()) / 2;
        assertEquals(secondOpen, ha.get(1).open(), 1e-12, "the previous average bar's midpoint");
        assertEquals((11 + 14 + 10 + 13) / 4.0, ha.get(1).close(), 1e-12);
        assertEquals(14, ha.get(1).high(), 1e-12);
        assertEquals(ChartType.CANDLES.display(List.of(day(0, 1, 2, 0.5, 1.5))).getFirst().close(), 1.5, 1e-12,
                "other types draw the real candles");
    }

    @Test
    void eventsLandOnTheCandleThatHoldsThemOrInTheFuture() {
        List<Candle> candles = List.of(day(0, 1, 1, 1, 1), day(1, 1, 1, 1, 1), day(2, 1, 1, 1, 1));
        assertEquals(1, ChartView.indexOf(candles, Interval.D1, T0.plusSeconds(86_400L + 3_600)), 1e-12,
                "an event in the middle of day 1 sits on day 1's candle");
        assertEquals(0, ChartView.indexOf(candles, Interval.D1, T0.minusSeconds(60)), 1e-12,
                "before the first candle: the first");
        assertEquals(3.5, ChartView.indexOf(candles, Interval.D1, T0.plusSeconds(86_400L * 3 + 43_200)), 1e-12,
                "a day and a half after the last candle opened: 1.5 slots on");
    }

    @Test
    void untilReadsNaturally() {
        Instant now = Instant.parse("2026-10-15T10:00:00Z");
        assertEquals("in 35 min", Format.until(now.plusSeconds(35 * 60), now));
        assertEquals("in 2 h 05 min", Format.until(now.plusSeconds(125 * 60), now));
        assertEquals("in 1 d 3 h", Format.until(now.plusSeconds(27 * 3600), now));
        assertEquals("now", Format.until(now.minusSeconds(5), now));
    }
}
