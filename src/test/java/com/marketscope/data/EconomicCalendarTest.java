package com.marketscope.data;

import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;
import com.marketscope.model.EconomicEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomicCalendarTest {

    private static final String EXPORT = """
            [{"title":"CPI m/m","country":"USD","date":"2026-10-15T08:30:00-04:00","impact":"High","forecast":"0.3%","previous":"0.4%"},
             {"title":"German ZEW Economic Sentiment","country":"EUR","date":"2026-10-14T05:00:00-04:00","impact":"Medium","forecast":"12.0","previous":"10.3"},
             {"title":"Bank Holiday","country":"JPY","date":"2026-10-11T19:00:00-04:00","impact":"Holiday","forecast":"","previous":""},
             {"title":"No date","country":"USD","date":"","impact":"Low","forecast":"","previous":""}]""";

    @Test
    void readsTheExportInTimeOrder() throws IOException {
        List<EconomicEvent> events = EconomicCalendar.parse(EXPORT);
        assertEquals(3, events.size(), "an event without a date is skipped");
        assertEquals("Bank Holiday", events.getFirst().title());
        EconomicEvent cpi = events.getLast();
        assertEquals(Instant.parse("2026-10-15T12:30:00Z"), cpi.time(), "the export's New York time, as an instant");
        assertEquals(EconomicEvent.Impact.HIGH, cpi.impact());
        assertEquals("0.3%", cpi.forecast());
        assertThrows(IOException.class, () -> EconomicCalendar.parse("<html>blocked</html>"));
    }

    @Test
    void eachMarketGetsItsCurrencies() throws IOException {
        List<EconomicEvent> events = EconomicCalendar.parse(EXPORT);
        Asset eurusd = new Asset(AssetKind.FOREX, "EURUSD", "EUR/USD");
        assertEquals(Set.of("EUR", "USD"), EconomicCalendar.currencies(eurusd));
        assertEquals(2, EconomicCalendar.forAsset(eurusd, events).size());
        Asset bitcoin = new Asset(AssetKind.CRYPTO, "BTCUSDT", "Bitcoin");
        assertEquals(List.of("CPI m/m"), EconomicCalendar.forAsset(bitcoin, events).stream()
                .map(EconomicEvent::title).toList(), "crypto follows US data");
    }

    @Test
    void theNextHighImpactReleaseWithinAWindow() throws IOException {
        List<EconomicEvent> events = EconomicCalendar.parse(EXPORT);
        Asset apple = new Asset(AssetKind.STOCK, "AAPL", "Apple");
        Instant morning = Instant.parse("2026-10-15T09:00:00Z");
        assertEquals("CPI m/m", EconomicCalendar.nextHighImpact(apple, events, morning, Duration.ofHours(12)).title());
        assertNull(EconomicCalendar.nextHighImpact(apple, events, morning, Duration.ofHours(2)), "3.5 h away");
        assertNull(EconomicCalendar.nextHighImpact(apple, events, Instant.parse("2026-10-16T00:00:00Z"),
                Duration.ofHours(12)), "already past");
    }
}
