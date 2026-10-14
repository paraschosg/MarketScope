package com.marketscope.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;
import com.marketscope.model.Candle;
import com.marketscope.model.Interval;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Crypto candles from Binance's public market-data API (no account, no key). */
public final class BinanceSource implements PriceSource {

    static final int LIMIT = 1000;

    @Override
    public String name() {
        return "Binance";
    }

    @Override
    public boolean supports(Asset asset, Interval interval) {
        return asset.kind() == AssetKind.CRYPTO;
    }

    @Override
    public List<Candle> candles(Asset asset, Interval interval) throws IOException {
        String url = "https://api.binance.com/api/v3/klines?symbol=" + asset.symbol() + "&interval="
                + interval.binance() + "&limit=" + LIMIT;
        return parse(Http.get(url));
    }

    /** Binance klines: arrays of [openTime, open, high, low, close, volume, closeTime, ...]. */
    static List<Candle> parse(String json) throws IOException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IOException("Binance sent something that is not JSON.", e);
        }
        if (root.isJsonObject()) {
            String message = root.getAsJsonObject().has("msg") ? root.getAsJsonObject().get("msg").getAsString()
                    : "unexpected answer";
            throw new IOException("Binance: " + message);
        }
        List<Candle> candles = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            JsonArray row = element.getAsJsonArray();
            candles.add(new Candle(Instant.ofEpochMilli(row.get(0).getAsLong()),
                    row.get(1).getAsDouble(), row.get(2).getAsDouble(), row.get(3).getAsDouble(),
                    row.get(4).getAsDouble(), row.get(5).getAsDouble()));
        }
        return candles;
    }
}
