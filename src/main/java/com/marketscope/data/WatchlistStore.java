package com.marketscope.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The watchlist, kept as JSON in {@code ~/.marketscope/watchlist.json} (or the folder given
 * with {@code -Dmarketscope.home}). A missing or unreadable file gives the default list.
 */
public final class WatchlistStore {

    private record Entry(String kind, String symbol, String name) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public WatchlistStore() {
        this(home().resolve("watchlist.json"));
    }

    public WatchlistStore(Path file) {
        this.file = file;
    }

    public static Path home() {
        String override = System.getProperty("marketscope.home");
        return override != null ? Path.of(override) : Path.of(System.getProperty("user.home"), ".marketscope");
    }

    public List<Asset> load() {
        try {
            if (!Files.exists(file)) {
                return Asset.defaults();
            }
            List<Entry> entries = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<List<Entry>>() { }.getType());
            List<Asset> assets = new ArrayList<>();
            for (Entry e : entries == null ? List.<Entry>of() : entries) {
                try {
                    assets.add(new Asset(AssetKind.valueOf(e.kind()), e.symbol(), e.name()));
                } catch (RuntimeException ignored) {
                    // skip an entry edited into something unreadable
                }
            }
            return assets.isEmpty() ? Asset.defaults() : assets;
        } catch (IOException | RuntimeException e) {
            return Asset.defaults();
        }
    }

    public void save(List<Asset> assets) throws IOException {
        List<Entry> entries = assets.stream().map(a -> new Entry(a.kind().name(), a.symbol(), a.name())).toList();
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(entries), StandardCharsets.UTF_8);
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
