package com.marketscope.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.marketscope.model.Asset;
import com.marketscope.model.Candle;
import com.marketscope.model.Interval;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stock, forex and (as a fallback) crypto candles from Yahoo Finance's chart endpoint. It is
 * public but unofficial: fine for personal use, and it can change without notice. Yahoo has no
 * 4-hour candles, so those are built from hourly ones.
 */
public final class YahooSource implements PriceSource {

    @Override
    public String name() {
        return "Yahoo Finance (unofficial)";
    }

    @Override
    public boolean supports(Asset asset, Interval interval) {
        return true;
    }

    @Override
    public List<Candle> candles(Asset asset, Interval interval) throws IOException {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/"
                + URLEncoder.encode(asset.yahooSymbol(), StandardCharsets.UTF_8)
                + "?range=" + interval.yahooRange() + "&interval=" + interval.yahoo() + "&includePrePost=false";
        List<Candle> candles = parse(Http.get(url));
        return interval == Interval.H4 ? Aggregate.byPeriod(candles, Interval.H4.length()) : candles;
    }

    static List<Candle> parse(String json) throws IOException {
        JsonObject chart;
        try {
            chart = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("chart");
        } catch (RuntimeException e) {
            throw new IOException("Yahoo sent something that is not chart JSON.", e);
        }
        JsonElement error = chart.get("error");
        if (error != null && !error.isJsonNull()) {
            JsonObject e = error.getAsJsonObject();
            throw new IOException("Yahoo: " + (e.has("description") ? e.get("description").getAsString()
                    : "the symbol was not found"));
        }
        JsonArray results = chart.getAsJsonArray("result");
        if (results == null || results.isEmpty()) {
            throw new IOException("Yahoo had no data for this symbol.");
        }
        JsonObject result = results.get(0).getAsJsonObject();
        JsonArray times = result.getAsJsonArray("timestamp");
        List<Candle> candles = new ArrayList<>();
        if (times == null) {
            return candles;
        }
        JsonObject quote = result.getAsJsonObject("indicators").getAsJsonArray("quote").get(0).getAsJsonObject();
        JsonArray open = quote.getAsJsonArray("open");
        JsonArray high = quote.getAsJsonArray("high");
        JsonArray low = quote.getAsJsonArray("low");
        JsonArray close = quote.getAsJsonArray("close");
        JsonArray volume = quote.getAsJsonArray("volume");
        for (int i = 0; i < times.size(); i++) {
            Double o = number(open, i);
            Double h = number(high, i);
            Double l = number(low, i);
            Double c = number(close, i);
            if (o == null || h == null || l == null || c == null) {
                continue; // Yahoo leaves holes for halted or empty periods
            }
            Double v = number(volume, i);
            candles.add(new Candle(Instant.ofEpochSecond(times.get(i).getAsLong()), o, h, l, c,
                    v == null ? 0 : v));
        }
        return candles;
    }

    private static Double number(JsonArray array, int i) {
        if (array == null || i >= array.size() || array.get(i).isJsonNull()) {
            return null;
        }
        double value = array.get(i).getAsDouble();
        return Double.isFinite(value) ? value : null;
    }
}
