package com.marketscope.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Something with a price to chart.
 *
 * @param kind   crypto, stock or forex
 * @param symbol crypto: the Binance pair, e.g. "BTCUSDT"; stock: the ticker, e.g. "AAPL";
 *               forex: the pair without separator, e.g. "EURUSD"
 * @param name   a human name, e.g. "Bitcoin"
 */
public record Asset(AssetKind kind, String symbol, String name) {

    private static final List<String> QUOTES = List.of("USDT", "USDC", "FDUSD", "BUSD", "USD", "EUR", "BTC");

    public Asset {
        Objects.requireNonNull(kind, "kind");
        symbol = Objects.requireNonNull(symbol, "symbol").trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("An asset needs a symbol");
        }
        name = name == null || name.isBlank() ? symbol : name.trim();
    }

    /** The coin of a crypto pair: "BTC" for "BTCUSDT". The symbol itself for other kinds. */
    public String base() {
        if (kind == AssetKind.CRYPTO) {
            for (String quote : QUOTES) {
                if (symbol.endsWith(quote) && symbol.length() > quote.length()) {
                    return symbol.substring(0, symbol.length() - quote.length());
                }
            }
        }
        if (kind == AssetKind.FOREX && symbol.length() == 6) {
            return symbol.substring(0, 3);
        }
        return symbol;
    }

    /** The quote currency: "USDT" for "BTCUSDT", "USD" for "EURUSD", "USD" for stocks. */
    public String quote() {
        if (kind == AssetKind.CRYPTO) {
            String base = base();
            return base.equals(symbol) ? "USD" : symbol.substring(base.length());
        }
        if (kind == AssetKind.FOREX && symbol.length() == 6) {
            return symbol.substring(3);
        }
        return "USD";
    }

    /** The symbol Yahoo Finance uses: "AAPL", "EURUSD=X", "BTC-USD". */
    public String yahooSymbol() {
        return switch (kind) {
            case STOCK -> symbol;
            case FOREX -> symbol + "=X";
            case CRYPTO -> base() + "-USD";
        };
    }

    /** "BTC/USDT", "EUR/USD" or "AAPL", for headings. */
    public String displaySymbol() {
        return switch (kind) {
            case STOCK -> symbol;
            case CRYPTO, FOREX -> base().equals(symbol) ? symbol : base() + "/" + quote();
        };
    }

    /**
     * Guesses the kind of a typed symbol: six letters made of two currency codes is forex, a
     * symbol ending in a stablecoin or crypto quote is crypto, anything else a stock ticker.
     */
    public static Asset parse(String text) {
        String s = Objects.requireNonNull(text, "text").trim().toUpperCase(Locale.ROOT).replace("/", "")
                .replace("-", "").replace("=X", "");
        if (s.length() == 6 && Currencies.isFiat(s.substring(0, 3)) && Currencies.isFiat(s.substring(3))) {
            return new Asset(AssetKind.FOREX, s, s.substring(0, 3) + "/" + s.substring(3));
        }
        for (String quote : List.of("USDT", "USDC", "FDUSD")) {
            if (s.endsWith(quote) && s.length() > quote.length()) {
                return new Asset(AssetKind.CRYPTO, s, s.substring(0, s.length() - quote.length()));
            }
        }
        if (Currencies.isKnownCoin(s)) {
            return new Asset(AssetKind.CRYPTO, s + "USDT", s);
        }
        return new Asset(AssetKind.STOCK, s, s);
    }

    /** A starting watchlist across the three markets. */
    public static List<Asset> defaults() {
        return List.of(
                new Asset(AssetKind.CRYPTO, "BTCUSDT", "Bitcoin"),
                new Asset(AssetKind.CRYPTO, "ETHUSDT", "Ethereum"),
                new Asset(AssetKind.CRYPTO, "SOLUSDT", "Solana"),
                new Asset(AssetKind.STOCK, "AAPL", "Apple"),
                new Asset(AssetKind.STOCK, "NVDA", "NVIDIA"),
                new Asset(AssetKind.STOCK, "MSFT", "Microsoft"),
                new Asset(AssetKind.STOCK, "SPY", "S&P 500 ETF"),
                new Asset(AssetKind.FOREX, "EURUSD", "Euro / US dollar"),
                new Asset(AssetKind.FOREX, "GBPUSD", "Pound / US dollar"),
                new Asset(AssetKind.FOREX, "USDJPY", "US dollar / Yen"));
    }
}
