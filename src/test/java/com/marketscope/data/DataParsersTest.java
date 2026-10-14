package com.marketscope.data;

import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;
import com.marketscope.model.Candle;
import com.marketscope.model.Interval;
import com.marketscope.model.NewsItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataParsersTest {

    @Test
    void binanceKlines() throws IOException {
        String json = "[[1791849600000,\"81850.01\",\"84168.00\",\"81033.66\",\"83169.00\",\"14784.06\",1791935999999,"
                + "\"1230595670.8\",3094331,\"7522.5\",\"626232475.3\",\"0\"]]";
        List<Candle> candles = BinanceSource.parse(json);
        assertEquals(1, candles.size());
        Candle c = candles.getFirst();
        assertEquals(Instant.ofEpochMilli(1791849600000L), c.time());
        assertEquals(83169.00, c.close(), 1e-9);
        assertEquals(14784.06, c.volume(), 1e-9);
        assertThrows(IOException.class, () -> BinanceSource.parse("{\"code\":-1121,\"msg\":\"Invalid symbol.\"}"));
    }

    @Test
    void yahooChartSkipsHoles() throws IOException {
        String json = "{\"chart\":{\"result\":[{\"timestamp\":[100,200,300],\"indicators\":{\"quote\":[{"
                + "\"open\":[1.0,null,3.0],\"high\":[1.5,2.5,3.5],\"low\":[0.5,1.5,2.5],\"close\":[1.2,2.2,3.2],"
                + "\"volume\":[10,20,null]}]}}],\"error\":null}}";
        List<Candle> candles = YahooSource.parse(json);
        assertEquals(2, candles.size(), "the candle with a missing open is left out");
        assertEquals(0, candles.get(1).volume(), 1e-12, "missing volume is 0");
        assertThrows(IOException.class, () -> YahooSource.parse(
                "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"No data found\"}}}"));
    }

    @Test
    void ecbRatesAreSortedByDay() throws IOException {
        String json = "{\"base\":\"EUR\",\"rates\":{\"2026-10-02\":{\"USD\":1.1225},\"2026-10-01\":{\"USD\":1.1298}}}";
        List<Candle> candles = FrankfurterSource.parse(json, "USD");
        assertEquals(1.1298, candles.getFirst().close(), 1e-9);
        assertEquals(1.1225, candles.getLast().high(), 1e-9);
    }

    @Test
    void fourHourCandlesFromHourlyOnes() {
        List<Candle> hourly = List.of(
                new Candle(Instant.parse("2026-10-14T00:00:00Z"), 1, 2, 0.5, 1.5, 10),
                new Candle(Instant.parse("2026-10-14T01:00:00Z"), 1.5, 3, 1, 2.5, 10),
                new Candle(Instant.parse("2026-10-14T04:00:00Z"), 2.5, 2.6, 2, 2.2, 5));
        List<Candle> four = Aggregate.byPeriod(hourly, Interval.H4.length());
        assertEquals(2, four.size());
        assertEquals(1, four.getFirst().open(), 1e-12);
        assertEquals(3, four.getFirst().high(), 1e-12);
        assertEquals(2.5, four.getFirst().close(), 1e-12);
        assertEquals(20, four.getFirst().volume(), 1e-12);
    }

    @Test
    void rssItemsAndASafeParser() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>t</title>
                <item><title>Bitcoin &amp; Ether rally</title><link>https://example.com/a</link>
                <pubDate>Tue, 14 Oct 2026 08:41:00 +0000</pubDate><description>&lt;p&gt;Up &lt;b&gt;5%&lt;/b&gt;&lt;/p&gt;</description></item>
                <item><title></title></item>
                </channel></rss>""";
        List<RssParser.Item> items = RssParser.parse(xml);
        assertEquals(1, items.size(), "an item without a title is skipped");
        assertEquals("Bitcoin & Ether rally", items.getFirst().title());
        assertEquals(Instant.parse("2026-10-14T08:41:00Z"), items.getFirst().published());
        assertEquals("Up 5%", items.getFirst().summary(), "HTML is stripped");

        String hostile = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY x SYSTEM "file:///c:/windows/win.ini">]>
                <rss><channel><item><title>&x;</title></item></channel></rss>""";
        assertThrows(IOException.class, () -> RssParser.parse(hostile), "DTDs and external entities are refused");
    }

    @Test
    void generalCryptoHeadlinesMustMentionTheCoin() {
        Asset sol = new Asset(AssetKind.CRYPTO, "SOLUSDT", "Solana");
        assertTrue(NewsFeed.mentions(new RssParser.Item("Solana DEX volume hits a record", "", null, ""), sol));
        assertTrue(NewsFeed.mentions(new RssParser.Item("SOL jumps 8%", "", null, ""), sol));
        assertFalse(NewsFeed.mentions(new RssParser.Item("Bitcoin consolidates", "", null, "solid demand"), sol),
                "'solid' is not SOL");
    }

    @Test
    void duplicatesAreDroppedAndNewestComesFirst() {
        List<NewsItem> items = NewsFeed.dedupe(List.of(
                new NewsItem("Fed holds rates", "", "a", Instant.parse("2026-10-13T10:00:00Z"), 0),
                new NewsItem("Fed holds rates!", "", "b", Instant.parse("2026-10-13T11:00:00Z"), 0),
                new NewsItem("Stocks rise", "", "a", Instant.parse("2026-10-14T10:00:00Z"), 0)));
        assertEquals(2, items.size());
        assertEquals("Stocks rise", items.getFirst().title());
    }

    @Test
    void typedSymbolsAreSortedIntoMarkets() {
        assertEquals(AssetKind.FOREX, Asset.parse("eur/usd").kind());
        assertEquals("EURUSD=X", Asset.parse("EURUSD").yahooSymbol());
        assertEquals(new Asset(AssetKind.CRYPTO, "ETHUSDT", "ETH"), Asset.parse("ETH"));
        assertEquals("BTC-USD", Asset.parse("BTCUSDT").yahooSymbol());
        assertEquals(AssetKind.STOCK, Asset.parse("tsla").kind());
        assertEquals("BTC/USDT", new Asset(AssetKind.CRYPTO, "BTCUSDT", "Bitcoin").displaySymbol());
    }
}
