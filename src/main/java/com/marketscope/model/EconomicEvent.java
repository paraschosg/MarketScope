package com.marketscope.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * One release or speech from the economic calendar, e.g. "CPI m/m" for USD.
 *
 * @param currency the currency it concerns, e.g. "USD"
 * @param forecast the expected figure, empty when none
 * @param previous the last figure, empty when none
 */
public record EconomicEvent(String title, String currency, Instant time, Impact impact, String forecast,
                            String previous) {

    /** How much a release usually moves its currency, as Forex Factory rates it. */
    public enum Impact {
        HIGH("High"),
        MEDIUM("Medium"),
        LOW("Low"),
        HOLIDAY("Holiday"),
        NONE("Other");

        private final String label;

        Impact(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Impact parse(String text) {
            return switch (text == null ? "" : text.strip().toLowerCase(Locale.ROOT)) {
                case "high" -> HIGH;
                case "medium" -> MEDIUM;
                case "low" -> LOW;
                case "holiday" -> HOLIDAY;
                default -> NONE;
            };
        }
    }

    public EconomicEvent {
        title = Objects.requireNonNull(title, "title").strip();
        currency = Objects.requireNonNull(currency, "currency").strip().toUpperCase(Locale.ROOT);
        Objects.requireNonNull(time, "time");
        impact = impact == null ? Impact.NONE : impact;
        forecast = forecast == null ? "" : forecast.strip();
        previous = previous == null ? "" : previous.strip();
    }
}
