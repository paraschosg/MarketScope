package com.marketscope.ui;

import com.marketscope.analysis.Analysis;
import com.marketscope.analysis.Backtest;
import com.marketscope.analysis.Levels;
import com.marketscope.analysis.Outlook;
import com.marketscope.analysis.Pattern;
import com.marketscope.analysis.Sentiment;
import com.marketscope.data.MarketData;
import com.marketscope.data.NewsFeed;
import com.marketscope.data.WatchlistStore;
import com.marketscope.model.Asset;
import com.marketscope.model.AssetKind;
import com.marketscope.model.Interval;
import com.marketscope.model.NewsItem;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single window: a watchlist on the left, the chart in the middle, and on the right what
 * the chart and the headlines suggest, with every pattern's track record on this asset.
 * Reading and analysing happen on background threads; everything here runs on the JavaFX thread.
 */
public final class MainView {

    static final Duration AUTO_REFRESH = Duration.seconds(60);
    static final java.time.Duration NEWS_FOR = java.time.Duration.ofMinutes(5);
    static final String DISCLAIMER = "Analysis, not financial advice. Patterns describe the past; they do not"
            + " promise the future.";

    private record CachedNews(NewsFeed.News news, Instant readAt) {
    }

    private final HostServices host;
    private final MarketData market = new MarketData();
    private final NewsFeed newsFeed = new NewsFeed();
    private final WatchlistStore store;
    private final ExecutorService work = Executors.newVirtualThreadPerTaskExecutor();
    private final ZoneId zone = ZoneId.systemDefault();

    private final BorderPane root = new BorderPane();
    private final ObservableList<Asset> watchlist;
    private final ListView<Asset> list = new ListView<>();
    private final Map<Asset, MarketData.Series> quotes = new ConcurrentHashMap<>();
    private final Map<Asset, CachedNews> news = new ConcurrentHashMap<>();
    private final ChartView chart = new ChartView();
    private final Label message = new Label();
    private final Label assetName = new Label();
    private final Label assetSymbol = new Label();
    private final Label kindChip = new Label();
    private final Label priceLabel = new Label();
    private final Label changeLabel = new Label();
    private final Label sourceLabel = new Label();
    private final Label status = new Label("Ready");
    private final ToggleGroup intervals = new ToggleGroup();
    private final VBox side = new VBox(12);
    private final Timeline autoRefresh;

    private Asset current;
    private Interval interval = Interval.D1;
    private Analysis analysis;
    private NewsFeed.News currentNews;
    private long generation;

    public MainView(HostServices host) {
        this(host, new WatchlistStore());
    }

    public MainView(HostServices host, WatchlistStore store) {
        this.host = host;
        this.store = store;
        this.watchlist = FXCollections.observableArrayList(store.load());
        root.setLeft(sidebar());
        BorderPane centre = new BorderPane();
        centre.setTop(new VBox(header(), toolbar()));
        StackPane chartArea = new StackPane(chart, message);
        chartArea.getStyleClass().add("ms-chart-area");
        StackPane.setAlignment(message, Pos.CENTER);
        message.getStyleClass().add("ms-message");
        message.setMaxWidth(460);
        message.setVisible(false);
        chartArea.setPadding(new Insets(4, 4, 0, 4));
        centre.setCenter(chartArea);
        root.setCenter(centre);
        root.setRight(sidePanel());
        root.setBottom(statusBar());
        autoRefresh = new Timeline(new KeyFrame(AUTO_REFRESH, e -> {
            if (current != null) {
                load(true, true);
            }
        }));
        autoRefresh.setCycleCount(Animation.INDEFINITE);
        renderEmptySide();
    }

    public Node node() {
        return root;
    }

    /** Opens the first asset and fills the watchlist prices in the background. */
    public void start() {
        if (!watchlist.isEmpty() && list.getSelectionModel().isEmpty()) {
            list.getSelectionModel().selectFirst();
        }
        autoRefresh.play();
        loadQuotes(List.copyOf(watchlist));
    }

    public void dispose() {
        autoRefresh.stop();
        work.shutdownNow();
    }

    // Layout ---------------------------------------------------------------------------------------

    private Node sidebar() {
        Label mark = new Label("◆");
        mark.getStyleClass().add("ms-brand-mark");
        Label brand = new Label("MarketScope");
        brand.getStyleClass().add("ms-brand");
        HBox title = new HBox(8, mark, brand);
        title.setAlignment(Pos.CENTER_LEFT);
        title.setPadding(new Insets(0, 4, 10, 4));

        Label watching = new Label("WATCHLIST");
        watching.getStyleClass().add("ms-section");
        list.setItems(watchlist);
        list.getStyleClass().add("ms-watchlist");
        list.setCellFactory(v -> new WatchCell());
        list.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now != null && !now.equals(current)) {
                current = now;
                load(false, false);
            }
        });
        MenuItem remove = new MenuItem("Remove from watchlist");
        remove.setOnAction(e -> removeSelected());
        list.setContextMenu(new ContextMenu(remove));
        VBox.setVgrow(list, Priority.ALWAYS);

        TextField add = new TextField();
        add.setPromptText("Add: BTC, AAPL, EUR/USD");
        ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList("Auto", "Crypto", "Stock", "Forex"));
        kind.getSelectionModel().selectFirst();
        kind.setPrefWidth(84);
        Button addButton = new Button("Add");
        addButton.getStyleClass().add("ms-primary");
        Runnable doAdd = () -> addAsset(add.getText(), kind.getValue(), add);
        add.setOnAction(e -> doAdd.run());
        addButton.setOnAction(e -> doAdd.run());
        HBox.setHgrow(add, Priority.ALWAYS);
        HBox addRow = new HBox(6, add, addButton);
        Label hint = caption("Crypto from Binance, stocks and forex from Yahoo Finance. Right-click to remove.");

        VBox box = new VBox(6, title, watching, list, kind, addRow, hint);
        box.getStyleClass().add("ms-sidebar");
        box.setPrefWidth(250);
        box.setMinWidth(220);
        return box;
    }

    private Node header() {
        assetName.getStyleClass().add("ms-asset-name");
        assetSymbol.getStyleClass().add("ms-asset-symbol");
        kindChip.getStyleClass().addAll("ms-chip", "ms-chip-neutral");
        priceLabel.getStyleClass().add("ms-price");
        changeLabel.getStyleClass().add("ms-change");
        sourceLabel.getStyleClass().add("ms-caption");
        HBox name = new HBox(10, assetName, assetSymbol, kindChip);
        name.setAlignment(Pos.BASELINE_LEFT);
        VBox left = new VBox(2, name, sourceLabel);
        HBox priceBox = new HBox(10, priceLabel, changeLabel);
        priceBox.setAlignment(Pos.BASELINE_RIGHT);
        HBox header = new HBox(16, left, spacer(), priceBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("ms-header");
        return header;
    }

    private Node toolbar() {
        HBox segment = new HBox(2);
        segment.getStyleClass().add("ms-segment");
        for (Interval i : Interval.values()) {
            ToggleButton b = new ToggleButton(i.label());
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setToggleGroup(intervals);
            b.setUserData(i);
            b.setSelected(i == interval);
            segment.getChildren().add(b);
        }
        intervals.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) {
                was.setSelected(true); // always keep one interval chosen
                return;
            }
            interval = (Interval) now.getUserData();
            if (current != null) {
                load(false, false);
            }
        });
        CheckBox averages = check("Averages", true, chart::setAverages);
        CheckBox bands = check("Bollinger", false, chart::setBands);
        CheckBox levels = check("Levels", true, chart::setLevels);
        CheckBox patterns = check("Patterns", true, chart::setPatterns);
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> {
            if (current != null) {
                load(true, false);
            }
        });
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        segment.setMinWidth(Region.USE_PREF_SIZE);
        Label help = new Label("Wheel: zoom · drag: pan · double-click: latest");
        help.getStyleClass().add("ms-caption");
        help.setMinWidth(0);
        HBox bar = new HBox(14, segment, averages, bands, levels, patterns, spacer(), help, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("ms-toolbar");
        return bar;
    }

    private Node sidePanel() {
        side.getStyleClass().add("ms-side");
        side.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(side);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("ms-side-scroll");
        StackPane panel = new StackPane(scroll);
        panel.getStyleClass().add("ms-side-panel");
        panel.setPrefWidth(380);
        panel.setMinWidth(340);
        return panel;
    }

    private Node statusBar() {
        Label disclaimer = new Label(DISCLAIMER);
        disclaimer.getStyleClass().add("ms-disclaimer");
        HBox bar = new HBox(12, status, spacer(), disclaimer);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("ms-status");
        return bar;
    }

    // Loading --------------------------------------------------------------------------------------

    private void load(boolean fresh, boolean quiet) {
        long ticket = ++generation;
        Asset asset = current;
        Interval chosen = interval;
        if (!quiet) {
            showMessage("Reading " + asset.displaySymbol() + " " + chosen.label() + " prices…");
            status.setText("Reading " + asset.displaySymbol() + "…");
            renderHeader(asset, quotes.get(asset));
        }
        work.submit(() -> {
            try {
                MarketData.Series series = market.series(asset, chosen, fresh);
                CachedNews cached = news.get(asset);
                List<NewsItem> known = cached == null ? List.of() : cached.news().items();
                Analysis result = Analysis.of(series.candles(), chosen, asset.kind() != AssetKind.FOREX, known,
                        Instant.now());
                Platform.runLater(() -> {
                    if (ticket == generation) {
                        showAnalysis(series, result, quiet, cached == null ? null : cached.news());
                    }
                });
                if (cached == null || cached.readAt().plus(NEWS_FOR).isBefore(Instant.now())) {
                    NewsFeed.News fetched = newsFeed.news(asset);
                    news.put(asset, new CachedNews(fetched, Instant.now()));
                    Platform.runLater(() -> {
                        if (ticket == generation && analysis != null) {
                            currentNews = fetched;
                            analysis = analysis.withNews(fetched.items(), Instant.now());
                            renderSide();
                        }
                    });
                }
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    if (ticket != generation) {
                        return;
                    }
                    status.setText("Could not read " + asset.displaySymbol() + ": " + e.getMessage());
                    if (!quiet) {
                        chart.clear();
                        analysis = null;
                        showMessage("Could not read prices for " + asset.displaySymbol() + ".\n\n" + e.getMessage()
                                + "\n\nCheck the symbol and your connection, then press Refresh.");
                        renderEmptySide();
                    }
                });
            }
        });
    }

    private void showAnalysis(MarketData.Series series, Analysis result, boolean quiet, NewsFeed.News knownNews) {
        boolean sameChart = quiet && analysis != null && analysis.interval() == result.interval();
        analysis = result;
        currentNews = knownNews;
        message.setVisible(false);
        if (sameChart) {
            chart.update(result);
        } else {
            chart.show(result, zone);
        }
        if (series.interval() == Interval.D1) {
            quotes.put(series.asset(), series);
            list.refresh();
        }
        renderHeader(series.asset(), series);
        renderSide();
        status.setText(series.candles().size() + " candles from " + series.source() + " · read "
                + Format.clock(series.readAt(), zone) + " · refreshes every minute");
    }

    /** The daily series of every watchlist asset, one after another, for the prices in the list. */
    private void loadQuotes(List<Asset> assets) {
        work.submit(() -> {
            for (Asset asset : assets) {
                try {
                    MarketData.Series series = market.series(asset, Interval.D1, false);
                    quotes.put(asset, series);
                    Platform.runLater(list::refresh);
                    Thread.sleep(150); // be gentle with the free services
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException | RuntimeException e) {
                    // the list just shows no price for it
                }
            }
        });
    }

    private void addAsset(String text, String kind, TextField field) {
        if (text == null || text.isBlank()) {
            return;
        }
        Asset parsed;
        try {
            parsed = Asset.parse(text);
            if (!"Auto".equals(kind)) {
                AssetKind chosen = AssetKind.valueOf(kind.toUpperCase(java.util.Locale.ROOT));
                String symbol = chosen == AssetKind.CRYPTO && parsed.kind() != AssetKind.CRYPTO
                        ? parsed.symbol() + "USDT" : parsed.symbol();
                parsed = new Asset(chosen, symbol, parsed.name());
            }
        } catch (RuntimeException e) {
            status.setText("That does not look like a symbol.");
            return;
        }
        Asset asset = parsed;
        Asset existing = watchlist.stream().filter(a -> a.kind() == asset.kind() && a.symbol().equals(asset.symbol()))
                .findFirst().orElse(null);
        if (existing == null) {
            watchlist.add(asset);
            save();
            loadQuotes(List.of(asset));
            existing = asset;
        }
        field.clear();
        list.getSelectionModel().select(existing);
        list.scrollTo(existing);
    }

    private void removeSelected() {
        Asset selected = list.getSelectionModel().getSelectedItem();
        if (selected == null || watchlist.size() <= 1) {
            return;
        }
        watchlist.remove(selected);
        save();
    }

    private void save() {
        try {
            store.save(List.copyOf(watchlist));
        } catch (IOException e) {
            status.setText("The watchlist could not be saved: " + e.getMessage());
        }
    }

    // Rendering -----------------------------------------------------------------------------------

    private void renderHeader(Asset asset, MarketData.Series series) {
        assetName.setText(asset.name());
        assetSymbol.setText(asset.displaySymbol());
        kindChip.setText(asset.kind().label());
        if (series == null) {
            priceLabel.setText("");
            changeLabel.setText("");
            sourceLabel.setText("");
            return;
        }
        priceLabel.setText(Format.price(series.last().close()) + " " + asset.quote());
        double change = series.changePercent();
        changeLabel.setText(Format.change(change) + " · last " + series.interval().label() + " candle");
        setDirection(changeLabel, change);
        sourceLabel.setText("Prices: " + series.source() + " · " + series.candles().size() + " "
                + series.interval().label() + " candles");
    }

    private void renderEmptySide() {
        VBox card = card("WHAT THIS SHOWS");
        card.getChildren().add(wrapped("Pick an asset on the left. MarketScope draws its chart, finds classic"
                + " patterns, checks how each pattern actually played out on that asset's own history, reads the"
                + " latest headlines, and sums it up as a lean — never as advice.", "ms-factor-reading"));
        side.getChildren().setAll(card);
    }

    private void renderSide() {
        if (analysis == null) {
            renderEmptySide();
            return;
        }
        side.getChildren().setAll(outlookCard(), patternsCard(), recordCard(), levelsCard(), newsCard());
    }

    private Node outlookCard() {
        Outlook outlook = analysis.outlook();
        VBox card = card("OUTLOOK · " + analysis.interval().label());
        Label lean = new Label(outlook.lean().label());
        lean.getStyleClass().addAll("ms-lean", switch (outlook.lean()) {
            case UP -> "ms-lean-up";
            case DOWN -> "ms-lean-down";
            case MIXED -> "ms-lean-mixed";
        });
        card.getChildren().addAll(lean, wrapped(outlook.summary(), "ms-factor-reading"));
        for (Outlook.Factor f : outlook.factors()) {
            Label arrow = new Label(f.direction() > 0 ? "▲" : f.direction() < 0 ? "▼" : "●");
            arrow.getStyleClass().add("ms-arrow");
            setDirection(arrow, f.direction());
            arrow.setMinWidth(16);
            Label name = new Label(f.name());
            name.getStyleClass().add("ms-factor-name");
            VBox text = new VBox(1, name, wrapped(f.reading(), "ms-factor-reading"));
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(6, arrow, text);
            row.setAlignment(Pos.TOP_LEFT);
            card.getChildren().add(row);
        }
        card.getChildren().add(wrapped("A reading of the chart and the headlines, not a forecast or financial"
                + " advice. A pattern only counts when it beat chance on this chart's own history.", "ms-caption"));
        return card;
    }

    private Node patternsCard() {
        List<Pattern> recent = analysis.recentPatterns();
        VBox card = card("PATTERNS IN PLAY · LAST " + analysis.interval().horizonLabel().toUpperCase(java.util.Locale.ROOT));
        if (recent.isEmpty()) {
            card.getChildren().add(wrapped("No pattern confirmed in the last " + analysis.interval().horizonLabel()
                    + ". Older ones are drawn faintly on the chart.", "ms-factor-reading"));
            return card;
        }
        int last = analysis.candles().size() - 1;
        for (Pattern p : recent) {
            Backtest.Stats s = analysis.stats().get(p.kind());
            Label chip = chip(p.kind().bullish() ? "Bullish" : "Bearish", p.kind().direction());
            Label name = new Label(p.kind().label());
            name.getStyleClass().add("ms-factor-name");
            int age = last - p.confirm();
            Label when = new Label(age == 0 ? "last candle" : age + " candles ago");
            when.getStyleClass().add("ms-caption");
            HBox head = new HBox(8, name, chip, spacer(), when);
            head.setAlignment(Pos.CENTER_LEFT);
            String record = s == null || s.cases() == 0 ? "No earlier cases on this chart."
                    : "Here: " + s.cases() + " earlier cases, moved " + (p.kind().bullish() ? "up" : "down") + " "
                    + analysis.interval().horizonLabel() + " later " + Format.percent(s.hitRate()) + " of the time vs "
                    + Format.percent(s.baseRate()) + " for any candle"
                    + (!s.enoughCases() ? " — too few cases to judge."
                    : s.beatsChance() ? " (" + Format.points(s.edge()) + ", beats chance here)."
                    : " (" + Format.points(s.edge()) + ", could be luck, so it does not count).");
            card.getChildren().add(new VBox(2, head, wrapped(p.kind().description(), "ms-factor-reading"),
                    wrapped(record, "ms-caption")));
        }
        return card;
    }

    private Node recordCard() {
        VBox card = card("PATTERN TRACK RECORD ON THIS CHART");
        List<Backtest.Stats> rows = new ArrayList<>(analysis.stats().values().stream().filter(s -> s.cases() > 0)
                .toList());
        rows.sort(Comparator.comparing(Backtest.Stats::enoughCases).reversed()
                .thenComparing(Comparator.comparingDouble(Backtest.Stats::edge).reversed()));
        if (rows.isEmpty()) {
            card.getChildren().add(wrapped("Not enough history for any pattern yet.", "ms-factor-reading"));
            return card;
        }
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);
        ColumnConstraints first = new ColumnConstraints();
        first.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(first);
        String[] heads = {"Pattern", "Cases", "Right", "Any", "Edge"};
        for (int c = 0; c < heads.length; c++) {
            Label h = new Label(heads[c]);
            h.getStyleClass().add("ms-table-head");
            h.setMinWidth(Region.USE_PREF_SIZE);
            grid.add(h, c, 0);
        }
        int r = 1;
        for (Backtest.Stats s : rows) {
            Label name = new Label(s.kind().label());
            name.setMinWidth(Region.USE_PREF_SIZE);
            grid.add(name, 0, r);
            grid.add(mono(String.valueOf(s.cases())), 1, r);
            grid.add(mono(Format.percent(s.hitRate())), 2, r);
            grid.add(mono(Format.percent(s.baseRate())), 3, r);
            Label edge = mono(s.enoughCases() ? Format.points(s.edge()) : "few");
            if (s.beatsChance()) {
                setDirection(edge, s.kind().direction());
            } else {
                edge.getStyleClass().add("ms-secondary");
            }
            grid.add(edge, 4, r);
            r++;
        }
        card.getChildren().addAll(grid, wrapped("\"Right\": how often price moved the expected way "
                + analysis.interval().horizonLabel() + " after the pattern. \"Any\": how often it moved that way after"
                + " any candle. Only the difference, the edge, says anything: it is coloured only at 5+ points over 10+"
                + " cases and when unlikely to be luck. With twelve patterns tested, the odd fluke still slips through.",
                "ms-caption"));
        return card;
    }

    private Node levelsCard() {
        VBox card = card("SUPPORT & RESISTANCE");
        if (analysis.levels().isEmpty()) {
            card.getChildren().add(wrapped("No price level was touched often enough recently.", "ms-factor-reading"));
            return card;
        }
        double last = analysis.candles().getLast().close();
        for (Levels.Level level : analysis.levels()) {
            Label kind = chip(level.support() ? "Support" : "Resistance", level.support() ? 1 : -1);
            Label value = mono(Format.price(level.price()));
            double distance = (level.price() / last - 1) * 100;
            Label detail = new Label(level.touches() + " touches · " + Format.change(distance) + " away");
            detail.getStyleClass().add("ms-caption");
            HBox row = new HBox(8, kind, value, spacer(), detail);
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        }
        return card;
    }

    private Node newsCard() {
        VBox card = card("NEWS");
        if (currentNews == null) {
            card.getChildren().add(wrapped("Reading headlines…", "ms-factor-reading"));
            return card;
        }
        List<NewsItem> items = currentNews.items();
        if (items.isEmpty()) {
            card.getChildren().add(wrapped("No recent headlines found for this asset.", "ms-factor-reading"));
        } else {
            double average = currentNews.averageSentiment();
            card.getChildren().add(wrapped(items.size() + " headlines, on balance "
                    + Sentiment.label(average).toLowerCase(java.util.Locale.ROOT) + " ("
                    + String.format(java.util.Locale.ROOT, "%+.2f", average) + ")", "ms-factor-name"));
        }
        Instant now = Instant.now();
        for (NewsItem item : items.stream().limit(15).toList()) {
            int tone = item.sentiment() >= Sentiment.THRESHOLD ? 1 : item.sentiment() <= -Sentiment.THRESHOLD ? -1 : 0;
            Label chip = chip(Sentiment.label(item.sentiment()), tone);
            chip.setMinWidth(Region.USE_PREF_SIZE);
            Hyperlink title = new Hyperlink(item.title());
            title.setWrapText(true);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setOnAction(e -> open(item.link()));
            Label meta = new Label(item.source() + (item.published() == null ? "" : " · "
                    + Format.ago(item.published(), now)));
            meta.getStyleClass().add("ms-caption");
            VBox text = new VBox(1, title, meta);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(8, chip, text);
            row.setAlignment(Pos.TOP_LEFT);
            card.getChildren().add(row);
        }
        for (String problem : currentNews.problems()) {
            card.getChildren().add(wrapped("Not read: " + problem, "ms-caption"));
        }
        card.getChildren().add(wrapped("Sentiment is scored from the words in each headline: a rough hint, not"
                + " an understanding of the story.", "ms-caption"));
        return card;
    }

    private void open(String link) {
        if (host != null && link != null && (link.startsWith("https://") || link.startsWith("http://"))) {
            host.showDocument(link);
        }
    }

    // Small parts --------------------------------------------------------------------------------

    private void showMessage(String text) {
        message.setText(text);
        message.setVisible(true);
    }

    private static VBox card(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("ms-card-title");
        VBox card = new VBox(8, label);
        card.getStyleClass().add("ms-card");
        return card;
    }

    private static Label wrapped(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Label caption(String text) {
        return wrapped(text, "ms-caption");
    }

    private static Label mono(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("ms-mono");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label chip(String text, int direction) {
        Label chip = new Label(text);
        chip.getStyleClass().addAll("ms-chip", direction > 0 ? "ms-chip-up" : direction < 0 ? "ms-chip-down"
                : "ms-chip-neutral");
        return chip;
    }

    private static CheckBox check(String text, boolean on, java.util.function.Consumer<Boolean> action) {
        CheckBox box = new CheckBox(text);
        box.setMinWidth(Region.USE_PREF_SIZE);
        box.setSelected(on);
        box.selectedProperty().addListener((o, was, now) -> action.accept(now));
        return box;
    }

    /** Colours a label by direction, touching its style classes only when the colour changes. */
    private static void setDirection(Label label, double direction) {
        String wanted = direction > 0 ? "ms-up" : direction < 0 ? "ms-down" : "ms-secondary";
        if (label.getStyleClass().contains(wanted)) {
            return;
        }
        label.getStyleClass().removeAll("ms-up", "ms-down", "ms-secondary");
        label.getStyleClass().add(wanted);
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /**
     * A watchlist row: symbol and name, last price and the day's change. Its nodes are built once
     * and only their text changes, so a row updated while it is being styled stays styled.
     */
    private final class WatchCell extends ListCell<Asset> {
        private final Label symbol = new Label();
        private final Label name = new Label();
        private final Label price = new Label();
        private final Label change = new Label();
        private final HBox row;

        WatchCell() {
            symbol.getStyleClass().add("ms-watch-symbol");
            name.getStyleClass().add("ms-watch-name");
            price.getStyleClass().add("ms-watch-price");
            change.getStyleClass().add("ms-watch-change");
            VBox left = new VBox(1, symbol, name);
            VBox right = new VBox(1, price, change);
            right.setAlignment(Pos.CENTER_RIGHT);
            row = new HBox(8, left, spacer(), right);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(Asset asset, boolean empty) {
            super.updateItem(asset, empty);
            if (empty || asset == null) {
                setGraphic(null);
                return;
            }
            symbol.setText(asset.displaySymbol());
            name.setText(asset.name());
            MarketData.Series q = quotes.get(asset);
            price.setText(q == null ? "…" : Format.price(q.last().close()));
            change.setText(q == null ? "" : Format.change(q.changePercent()));
            setDirection(change, q == null ? 0 : q.changePercent());
            setGraphic(row);
        }
    }

    // For snapshots ----------------------------------------------------------------------------

    /** True once a chart and its headlines are on screen. */
    public boolean ready() {
        return analysis != null && currentNews != null;
    }

    public void choose(int watchlistIndex, Interval chosen) {
        interval = chosen;
        intervals.getToggles().stream().filter(t -> t.getUserData() == chosen).findFirst()
                .ifPresent(t -> t.setSelected(true));
        list.getSelectionModel().select(watchlistIndex);
    }
}
