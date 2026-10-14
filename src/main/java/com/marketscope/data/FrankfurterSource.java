package com.marketscope.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;
import com.marketscope.model.Candle;
import com.marketscope.model.Interval;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The European Central Bank's daily reference rates through the Frankfurter service: official
 * and free, but one rate per working day, so its candles have no high or low. Used for forex
 * when Yahoo is unavailable.
 */
public final class FrankfurterSource implements PriceSource {

    @Override
    public String name() {
        return "ECB reference rates";
    }

    @Override
    public boolean supports(Asset asset, Interval interval) {
        return asset.kind() == AssetKind.FOREX && (interval == Interval.D1 || interval == Interval.W1);
    }

    @Override
    public List<Candle> candles(Asset asset, Interval interval) throws IOException {
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusYears(interval == Interval.W1 ? 10 : 4);
        String url = "https://api.frankfurter.dev/v1/" + start + "..?base=" + asset.base() + "&symbols="
                + asset.quote();
        List<Candle> daily = parse(Http.get(url), asset.quote());
        return interval == Interval.W1 ? Aggregate.byPeriod(daily, Interval.W1.length()) : daily;
    }

    static List<Candle> parse(String json, String quote) throws IOException {
        JsonObject rates;
        try {
            rates = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("rates");
        } catch (RuntimeException e) {
            throw new IOException("The ECB rates service sent something unexpected.", e);
        }
        if (rates == null) {
            throw new IOException("The ECB publishes no rate for this pair.");
        }
        List<Candle> candles = new ArrayList<>();
        for (Map.Entry<String, JsonElement> day : rates.entrySet()) {
            JsonElement rate = day.getValue().getAsJsonObject().get(quote);
            if (rate == null) {
                continue;
            }
            double r = rate.getAsDouble();
            candles.add(new Candle(LocalDate.parse(day.getKey()).atStartOfDay().toInstant(ZoneOffset.UTC),
                    r, r, r, r, 0));
        }
        candles.sort((a, b) -> a.time().compareTo(b.time()));
        return candles;
    }
}
