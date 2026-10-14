package com.marketscope.model;

/** What kind of market an asset trades in; decides where its prices and news come from. */
public enum AssetKind {
    CRYPTO("Crypto"),
    STOCK("Stock"),
    FOREX("Forex");

    private final String label;

    AssetKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
