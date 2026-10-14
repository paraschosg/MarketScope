package com.marketscope.model;

import java.util.Set;

/** Small lists used to guess what kind of asset a typed symbol is. */
final class Currencies {

    private static final Set<String> FIAT = Set.of("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD",
            "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "TRY", "ZAR", "MXN", "BRL", "CNY", "HKD", "SGD", "INR",
            "KRW", "ILS", "RON", "BGN", "ISK");

    private static final Set<String> COINS = Set.of("BTC", "ETH", "SOL", "BNB", "XRP", "ADA", "DOGE", "AVAX",
            "DOT", "LINK", "MATIC", "POL", "LTC", "TRX", "SHIB", "ATOM", "UNI", "XLM", "NEAR", "APT", "ARB", "OP",
            "SUI", "TON", "PEPE", "INJ", "FIL", "AAVE", "ETC", "BCH", "HBAR", "ICP", "RNDR", "SEI", "TIA");

    private Currencies() {
    }

    static boolean isFiat(String code) {
        return FIAT.contains(code);
    }

    static boolean isKnownCoin(String code) {
        return COINS.contains(code);
    }
}
