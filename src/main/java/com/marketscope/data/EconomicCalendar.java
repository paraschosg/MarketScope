package com.marketscope.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;
import com.marketscope.model.EconomicEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * This week's economic calendar from Forex Factory's own public export
 * ({@code nfs.faireconomy.media}). The export asks to be read sparingly, so the answer is kept
 * on disk and read again at most once an hour; when a read fails, the kept copy is used.
 * Thread-safe.
 */
public final class EconomicCalendar {

    static final String URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json";
    static final Duration KEEP_FOR = Duration.ofHours(1);
    public static final String CALENDAR_PAGE = "https://www.forexfactory.com/calendar";
    public static final String NEWS_PAGE = "https://www.forexfactory.com/news";

    /** The week's events and how old they are; {@code problem} is set when a fresh read failed. */
    public record Week(List<EconomicEvent> events, Instant readAt, String problem) {

        public Week {
            events = List.copyOf(events);
        }
    }

    private final Path cache;

    public EconomicCalendar() {
        this(WatchlistStore.home().resolve("ff_calendar_thisweek.json"));
    }

    public EconomicCalendar(Path cache) {
        this.cache = cache;
    }

    /** The week's events, from the kept copy when it is less than an hour old. */
    public synchronized Week week() {
        Instant kept = keptAt();
        if (kept != null && kept.plus(KEEP_FOR).isAfter(Instant.now())) {
            try {
                return new Week(parse(Files.readString(cache, StandardCharsets.UTF_8)), kept, null);
            } catch (IOException | RuntimeException e) {
                // fall through to a fresh read
            }
        }
        try {
            String json = Http.get(URL);
            List<EconomicEvent> events = parse(json);
            Files.createDirectories(cache.getParent());
            Files.writeString(cache, json, StandardCharsets.UTF_8);
            return new Week(events, Instant.now(), null);
        } catch (IOException | RuntimeException e) {
            if (kept != null) {
                try {
                    return new Week(parse(Files.readString(cache, StandardCharsets.UTF_8)), kept, e.getMessage());
                } catch (IOException | RuntimeException ignored) {
                    // no usable copy either
                }
            }
            return new Week(List.of(), Instant.now(), e.getMessage());
        }
    }

    private Instant keptAt() {
        try {
            return Files.exists(cache) ? Files.getLastModifiedTime(cache).toInstant() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** The export: an array of {title, country, date (ISO with offset), impact, forecast, previous}. */
    static List<EconomicEvent> parse(String json) throws IOException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IOException("The calendar export is not JSON.", e);
        }
        if (!root.isJsonArray()) {
            throw new IOException("The calendar export has an unexpected shape.");
        }
        List<EconomicEvent> events = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            JsonObject e = element.getAsJsonObject();
            try {
                events.add(new EconomicEvent(text(e, "title"), text(e, "country"),
                        OffsetDateTime.parse(text(e, "date")).toInstant(),
                        EconomicEvent.Impact.parse(text(e, "impact")), text(e, "forecast"), text(e, "previous")));
            } catch (DateTimeParseException | NullPointerException | IllegalArgumentException ignored) {
                // skip an event the export could not date
            }
        }
        events.sort(Comparator.comparing(EconomicEvent::time));
        return events;
    }

    private static String text(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() ? "" : v.getAsString();
    }

    /**
     * The currencies whose releases matter for an asset: both sides of a forex pair; the dollar
     * for US stocks and for crypto, which trades against it and moves with US data.
     */
    public static Set<String> currencies(Asset asset) {
        if (asset.kind() == AssetKind.FOREX) {
            return Set.of(asset.base(), asset.quote());
        }
        return Set.of("USD");
    }

    /** The events that concern an asset, in time order. */
    public static List<EconomicEvent> forAsset(Asset asset, List<EconomicEvent> events) {
        Set<String> wanted = currencies(asset);
        return events.stream().filter(e -> wanted.contains(e.currency())).toList();
    }

    /** The next high-impact event for the asset within {@code within}, or null. */
    public static EconomicEvent nextHighImpact(Asset asset, List<EconomicEvent> events, Instant now,
                                               Duration within) {
        return forAsset(asset, events).stream()
                .filter(e -> e.impact() == EconomicEvent.Impact.HIGH)
                .filter(e -> !e.time().isBefore(now) && e.time().isBefore(now.plus(within)))
                .findFirst().orElse(null);
    }
}
