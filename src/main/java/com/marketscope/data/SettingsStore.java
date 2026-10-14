package com.marketscope.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** The look of the chart, remembered in {@code settings.json} next to the watchlist. */
public final class SettingsStore {

    /** Field defaults apply to anything missing from an older file. */
    public static final class Settings {
        public boolean dark = false;
        public String chartType = "CANDLES";
        public boolean log = false;
        public boolean volume = true;
        public boolean averages = true;
        public boolean bollinger = false;
        public boolean rsi = true;
        public boolean macd = true;
        public boolean levels = true;
        public boolean patterns = true;
        public boolean events = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public SettingsStore() {
        this(WatchlistStore.home().resolve("settings.json"));
    }

    public SettingsStore(Path file) {
        this.file = file;
    }

    public Settings load() {
        try {
            if (Files.exists(file)) {
                Settings s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Settings.class);
                if (s != null) {
                    return s;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // an unreadable file gives the defaults
        }
        return new Settings();
    }

    public void save(Settings settings) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(settings), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the look is a convenience; failing to remember it is not worth a message
        }
    }
}
