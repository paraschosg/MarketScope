package com.marketscope.ui;

import com.marketscope.analysis.Analysis;
import com.marketscope.analysis.Indicators;
import com.marketscope.analysis.Levels;
import com.marketscope.analysis.Pattern;
import com.marketscope.analysis.PatternKind;
import com.marketscope.model.Candle;
import com.marketscope.model.EconomicEvent;
import com.marketscope.model.Interval;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A TradingView-style price chart: candles (or bars, line, area, Heikin-Ashi) with volume beneath
 * them, moving averages, Bollinger bands, support and resistance, pattern shapes and economic
 * events, and RSI and MACD panes below.
 *
 * <p>Handling follows TradingView: the wheel zooms around the mouse; dragging the chart pans it
 * (dragging it up or down switches the price scale to manual); dragging the price scale stretches
 * it and dragging the time scale zooms; a double-click on the price scale returns to automatic
 * scaling, anywhere else to the latest candles. The LOG and AUTO switches sit under the price
 * scale and a "back to now" button appears when the latest candle is out of view. JavaFX thread
 * only.
 */
public final class ChartView extends Region {

    static final double AXIS_RIGHT = 84;
    static final double AXIS_BOTTOM = 28;
    static final int RIGHT_OFFSET = 6;
    static final double MIN_SLOT = 1.5;
    static final double MAX_SLOT = 48;
    static final double DEFAULT_SLOT = 7;

    private static final Font TEXT = Font.font("Segoe UI", 12);
    private static final Font SMALL = Font.font("Segoe UI", 11);
    private static final Font BOLD = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12);
    private static final Font MONO = Font.font("Consolas", 11.5);

    /** A pane of the chart: its vertical extent and the value range it shows. */
    record Pane(double top, double bottom, double min, double max, boolean log) {
        double f(double v) {
            return log ? Math.log10(Math.max(v, 1e-12)) : v;
        }

        double y(double value) {
            double lo = f(min);
            double hi = f(max);
            return hi == lo ? (top + bottom) / 2 : bottom - (f(value) - lo) / (hi - lo) * (bottom - top);
        }

        double value(double y) {
            double lo = f(min);
            double hi = f(max);
            double fv = lo + (bottom - y) / (bottom - top) * (hi - lo);
            return log ? Math.pow(10, fv) : fv;
        }

        boolean contains(double y) {
            return y >= top && y <= bottom;
        }
    }

    private enum Drag { NONE, PLOT, PRICE_AXIS, TIME_AXIS }

    private record Box(double x, double y, double w, double h) {
        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private record Flag(Box box, EconomicEvent event) {
    }

    private final Canvas canvas = new Canvas();
    private final Text probe = new Text();

    private Analysis analysis;
    private List<Candle> shown = List.of();
    private String title = "";
    private ZoneId zone = ZoneId.systemDefault();
    private List<EconomicEvent> events = List.of();
    private ChartTheme theme = ChartTheme.LIGHT;
    private ChartType type = ChartType.CANDLES;
    private boolean log;
    private boolean auto = true;
    private double manualMin;
    private double manualMax;
    private double slot = DEFAULT_SLOT;
    private double rightIndex;
    private double hoverX = -1;
    private double hoverY = -1;

    private boolean volume = true;
    private boolean averages = true;
    private boolean bands;
    private boolean levels = true;
    private boolean patterns = true;
    private boolean rsiPane = true;
    private boolean macdPane = true;
    private boolean eventMarks = true;

    private Drag drag = Drag.NONE;
    private double pressX;
    private double pressY;
    private double pressRight;
    private double pressSlot;
    private double pressMin;
    private double pressMax;

    private double plotRight;
    private double plotBottom;
    private Pane price;
    private final List<Flag> flags = new ArrayList<>();
    private Box logBox;
    private Box autoBox;
    private Box nowBox;

    public ChartView() {
        getChildren().add(canvas);
        setMinSize(320, 260);
        setOnScroll(e -> {
            if (analysis == null || plotRight <= 0) {
                return;
            }
            double mouse = Math.min(e.getX(), plotRight);
            double anchor = rightIndex - (plotRight - mouse) / slot;
            slot = Math.clamp(slot * (e.getDeltaY() > 0 ? 1.12 : 1 / 1.12), MIN_SLOT, MAX_SLOT);
            rightIndex = anchor + (plotRight - mouse) / slot;
            clampRight();
            draw();
        });
        setOnMousePressed(e -> {
            pressX = e.getX();
            pressY = e.getY();
            pressRight = rightIndex;
            pressSlot = slot;
            if (price != null) {
                pressMin = price.min();
                pressMax = price.max();
            }
            drag = e.getY() >= plotBottom ? Drag.TIME_AXIS
                    : e.getX() >= plotRight ? (price != null && price.contains(e.getY()) ? Drag.PRICE_AXIS : Drag.NONE)
                    : Drag.PLOT;
        });
        setOnMouseReleased(e -> drag = Drag.NONE);
        setOnMouseDragged(e -> {
            if (analysis == null || price == null) {
                return;
            }
            switch (drag) {
                case PLOT -> {
                    rightIndex = pressRight + (pressX - e.getX()) / slot;
                    clampRight();
                    if (price.contains(pressY) && (!auto || Math.abs(e.getY() - pressY) > 12)) {
                        auto = false;
                        double span = price.f(pressMax) - price.f(pressMin);
                        double shift = (e.getY() - pressY) / (price.bottom() - price.top()) * span;
                        manualMin = unf(price.f(pressMin) + shift);
                        manualMax = unf(price.f(pressMax) + shift);
                    }
                }
                case PRICE_AXIS -> {
                    auto = false;
                    double scale = Math.exp((e.getY() - pressY) * 0.006);
                    double centre = (price.f(pressMin) + price.f(pressMax)) / 2;
                    double half = (price.f(pressMax) - price.f(pressMin)) / 2 * scale;
                    manualMin = unf(centre - half);
                    manualMax = unf(centre + half);
                }
                case TIME_AXIS -> {
                    slot = Math.clamp(pressSlot * Math.exp((e.getX() - pressX) * 0.008), MIN_SLOT, MAX_SLOT);
                    clampRight();
                }
                case NONE -> {
                    return;
                }
            }
            hoverX = -1;
            draw();
        });
        setOnMouseMoved(e -> {
            hoverX = e.getX();
            hoverY = e.getY();
            setCursor(e.getY() >= plotBottom ? Cursor.H_RESIZE : e.getX() >= plotRight ? Cursor.V_RESIZE
                    : Cursor.CROSSHAIR);
            draw();
        });
        setOnMouseExited(e -> {
            hoverX = -1;
            draw();
        });
        setOnMouseClicked(e -> {
            if (analysis == null) {
                return;
            }
            double x = e.getX();
            double y = e.getY();
            if (e.getClickCount() == 1 && Math.abs(x - pressX) < 3 && Math.abs(y - pressY) < 3) {
                if (logBox != null && logBox.contains(x, y)) {
                    setLog(!log);
                    fireSettingsChanged();
                } else if (autoBox != null && autoBox.contains(x, y)) {
                    auto = true;
                    draw();
                } else if (nowBox != null && nowBox.contains(x, y)) {
                    rightIndex = last() + RIGHT_OFFSET;
                    draw();
                }
            } else if (e.getClickCount() == 2) {
                if (x >= plotRight && y < plotBottom) {
                    auto = true;
                } else if (x < plotRight) {
                    resetView();
                }
                draw();
            }
        });
    }

    private Runnable onSettingsChanged = () -> { };

    /** Called when a switch on the chart itself (LOG) changes, so the choice can be remembered. */
    public void setOnSettingsChanged(Runnable action) {
        onSettingsChanged = action == null ? () -> { } : action;
    }

    private void fireSettingsChanged() {
        onSettingsChanged.run();
    }

    // Data and options -----------------------------------------------------------------------------

    /** Shows a new chart from its latest candles. */
    public void show(Analysis next, String chartTitle, ZoneId timeZone) {
        analysis = next;
        title = chartTitle;
        zone = timeZone;
        shown = type.display(next.candles());
        auto = true;
        resetView();
    }

    /** Replaces the data of the chart shown, following the newest candle if the view was on it. */
    public void update(Analysis next) {
        boolean following = analysis == null || rightIndex >= last() + 0.5;
        int added = analysis == null ? 0 : next.candles().size() - analysis.candles().size();
        analysis = next;
        shown = type.display(next.candles());
        rightIndex = following ? last() + RIGHT_OFFSET : rightIndex + added;
        clampRight();
        draw();
    }

    public void clear() {
        analysis = null;
        shown = List.of();
        draw();
    }

    public void resetView() {
        slot = DEFAULT_SLOT;
        if (analysis != null) {
            rightIndex = last() + RIGHT_OFFSET;
        }
        auto = true;
        hoverX = -1;
        draw();
    }

    public void setEvents(List<EconomicEvent> next) {
        events = List.copyOf(next);
        draw();
    }

    public void setTheme(ChartTheme next) {
        theme = next;
        draw();
    }

    public void setType(ChartType next) {
        type = next;
        if (analysis != null) {
            shown = type.display(analysis.candles());
            auto = true;
        }
        draw();
    }

    public void setLog(boolean on) {
        log = on;
        auto = true;
        draw();
    }

    public boolean log() {
        return log;
    }

    public void setVolume(boolean on) {
        volume = on;
        draw();
    }

    public void setAverages(boolean on) {
        averages = on;
        draw();
    }

    public void setBands(boolean on) {
        bands = on;
        draw();
    }

    public void setLevels(boolean on) {
        levels = on;
        draw();
    }

    public void setPatterns(boolean on) {
        patterns = on;
        draw();
    }

    public void setRsi(boolean on) {
        rsiPane = on;
        draw();
    }

    public void setMacd(boolean on) {
        macdPane = on;
        draw();
    }

    public void setEventMarks(boolean on) {
        eventMarks = on;
        draw();
    }

    @Override
    protected void layoutChildren() {
        double w = snapSizeX(getWidth());
        double h = snapSizeY(getHeight());
        if (canvas.getWidth() != w || canvas.getHeight() != h) {
            canvas.setWidth(w);
            canvas.setHeight(h);
            draw();
        }
    }

    // Coordinates ----------------------------------------------------------------------------------

    private int last() {
        return analysis == null ? 0 : analysis.candles().size() - 1;
    }

    private double x(double index) {
        return plotRight - (rightIndex - index + 0.5) * slot;
    }

    private int indexAt(double px) {
        return (int) Math.floor(rightIndex + 1 - (plotRight - px) / slot);
    }

    private void clampRight() {
        if (analysis == null) {
            return;
        }
        double visible = plotRight > 0 ? plotRight / slot : 100;
        rightIndex = Math.clamp(rightIndex, Math.min(last(), 10), last() + Math.max(RIGHT_OFFSET, visible * 0.7));
    }

    private double unf(double fv) {
        return log ? Math.pow(10, fv) : fv;
    }

    /** The time of a candle slot; slots after the last candle continue at the interval's length. */
    private Instant timeOf(int index) {
        List<Candle> candles = analysis.candles();
        if (index <= last()) {
            return candles.get(Math.max(0, index)).time();
        }
        return candles.getLast().time().plus(analysis.interval().length().multipliedBy(index - last()));
    }

    /** The slot an instant falls in: the candle that contains it, or a future slot. */
    static double indexOf(List<Candle> candles, Interval interval, Instant time) {
        Instant lastTime = candles.getLast().time();
        if (!time.isBefore(lastTime)) {
            long seconds = java.time.Duration.between(lastTime, time).getSeconds();
            return candles.size() - 1 + (double) seconds / interval.length().getSeconds();
        }
        int lo = 0;
        int hi = candles.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (candles.get(mid).time().isAfter(time)) {
                hi = mid - 1;
            } else {
                lo = mid;
            }
        }
        return lo;
    }

    // Drawing --------------------------------------------------------------------------------------

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(theme.background());
        g.fillRect(0, 0, w, h);
        flags.clear();
        logBox = null;
        autoBox = null;
        nowBox = null;
        if (analysis == null || w < 120 || h < 140) {
            return;
        }
        plotRight = w - AXIS_RIGHT;
        plotBottom = h - AXIS_BOTTOM;
        if (rightIndex == 0) {
            rightIndex = last() + RIGHT_OFFSET;
        }
        clampRight();
        int first = Math.max(0, (int) Math.floor(rightIndex - plotRight / slot));
        int end = Math.min(last(), (int) Math.ceil(rightIndex));
        if (end < first) {
            first = Math.max(0, end - 5);
        }

        double rsiH = rsiPane ? plotBottom * 0.15 : 0;
        double macdH = macdPane ? plotBottom * 0.17 : 0;
        double priceBottom = plotBottom - rsiH - macdH;
        price = pricePane(first, end, priceBottom);
        Pane rsi = rsiPane ? new Pane(priceBottom, priceBottom + rsiH, 0, 100, false) : null;
        Pane macd = macdPane ? macdPane(first, end, priceBottom + rsiH, plotBottom) : null;

        List<Integer> timeTicks = timeTicks(first, end);
        drawGrid(g, timeTicks, rsi, macd);
        if (volume) {
            drawVolume(g, first, end);
        }
        if (bands) {
            drawBands(g, first, end);
        }
        drawPrice(g, first, end);
        if (averages) {
            series(g, analysis.sma20(), first, end, price, theme.sma20(), 1.4);
            series(g, analysis.sma50(), first, end, price, theme.sma50(), 1.4);
            series(g, analysis.sma200(), first, end, price, theme.sma200(), 1.6);
        }
        if (levels) {
            drawLevelLines(g);
        }
        if (eventMarks) {
            layoutFlags();
        }
        if (patterns) {
            drawPatterns(g, first, end);
        }
        if (eventMarks) {
            drawEventLines(g);
        }
        drawLastPriceLine(g);
        if (rsi != null) {
            drawRsi(g, first, end, rsi);
        }
        if (macd != null) {
            drawMacd(g, first, end, macd);
        }
        drawFrame(g, w, h, priceBottom, rsi, macd);
        drawPriceAxis(g, rsi, macd);
        drawTimeAxis(g, timeTicks);
        if (eventMarks) {
            drawEventFlags(g);
        }
        drawLegends(g, rsi, macd);
        drawCrosshair(g, w, rsi, macd);
        drawScaleButtons(g, w, h);
        drawNowButton(g);
        drawEventTooltip(g, w);
    }

    private Pane pricePane(int first, int end, double bottom) {
        if (!auto) {
            return new Pane(0, bottom, manualMin, manualMax, log);
        }
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int i = first; i <= end; i++) {
            Candle c = shown.get(i);
            lo = Math.min(lo, type == ChartType.LINE || type == ChartType.AREA ? c.close() : c.low());
            hi = Math.max(hi, type == ChartType.LINE || type == ChartType.AREA ? c.close() : c.high());
            if (averages) {
                for (double[] s : List.of(analysis.sma20(), analysis.sma50(), analysis.sma200())) {
                    if (!Double.isNaN(s[i])) {
                        lo = Math.min(lo, s[i]);
                        hi = Math.max(hi, s[i]);
                    }
                }
            }
            if (bands && !Double.isNaN(analysis.bollinger().upper()[i])) {
                lo = Math.min(lo, analysis.bollinger().lower()[i]);
                hi = Math.max(hi, analysis.bollinger().upper()[i]);
            }
        }
        if (log && lo <= 0) {
            lo = Math.max(hi * 1e-6, 1e-12);
        }
        Pane raw = new Pane(0, bottom, lo, hi, log);
        // TradingView's scale margins: 10 % free above the highest price, 8 % below the lowest.
        double span = raw.f(hi) - raw.f(lo);
        if (span <= 0) {
            span = Math.abs(raw.f(hi)) * 0.01 + 1e-9;
        }
        double top = raw.f(hi) + span * 0.10 / 0.82;
        double low = raw.f(lo) - span * 0.08 / 0.82;
        return new Pane(0, bottom, log ? Math.pow(10, low) : low, log ? Math.pow(10, top) : top, log);
    }

    private Pane macdPane(int first, int end, double top, double bottom) {
        Indicators.Macd m = analysis.macd();
        double max = 0;
        for (int i = first; i <= end; i++) {
            for (double v : new double[] {m.line()[i], m.signal()[i], m.histogram()[i]}) {
                if (!Double.isNaN(v)) {
                    max = Math.max(max, Math.abs(v));
                }
            }
        }
        max = max == 0 ? 1 : max * 1.15;
        return new Pane(top, bottom, -max, max, false);
    }

    private List<Integer> timeTicks(int first, int end) {
        int step = Math.max(1, (int) Math.ceil(110 / slot));
        int from = Math.max(0, (int) Math.floor(rightIndex - plotRight / slot));
        int to = (int) Math.ceil(rightIndex);
        List<Integer> ticks = new ArrayList<>();
        for (int i = from - from % step; i <= to; i += step) {
            if (i >= 0 && x(i) > 30 && x(i) < plotRight - 30) {
                ticks.add(i);
            }
        }
        return ticks;
    }

    private void drawGrid(GraphicsContext g, List<Integer> timeTicks, Pane rsi, Pane macd) {
        g.setStroke(theme.grid());
        g.setLineWidth(1);
        for (int i : timeTicks) {
            double px = crisp(x(i));
            g.strokeLine(px, 0, px, plotBottom);
        }
        for (double v : priceTicks(price)) {
            double py = crisp(price.y(v));
            g.strokeLine(0, py, plotRight, py);
        }
    }

    /** Round prices about 40 px apart, in linear or log spacing. */
    private List<Double> priceTicks(Pane p) {
        List<Double> out = new ArrayList<>();
        double height = p.bottom() - p.top();
        int count = Math.max(2, (int) (height / 46));
        List<Double> candidates = niceTicks(p.min(), p.max(), p.log() ? count * 3 : count);
        double lastY = Double.POSITIVE_INFINITY;
        for (double v : candidates) {
            double y = p.y(v);
            if (y < p.top() + 8 || y > p.bottom() - 4) {
                continue;
            }
            if (lastY - y >= 30) {
                out.add(v);
                lastY = y;
            }
        }
        return out;
    }

    private void drawVolume(GraphicsContext g, int first, int end) {
        double max = 0;
        for (int i = first; i <= end; i++) {
            max = Math.max(max, analysis.candles().get(i).volume());
        }
        if (max == 0) {
            return; // forex has no volume
        }
        double zone = (price.bottom() - price.top()) * 0.22;
        double body = Math.max(1, slot * 0.72);
        for (int i = first; i <= end; i++) {
            Candle c = analysis.candles().get(i);
            if (c.volume() <= 0) {
                continue;
            }
            double height = c.volume() / max * zone;
            g.setFill((c.up() ? theme.up() : theme.down()).deriveColor(0, 1, 1, theme.dark() ? 0.4 : 0.32));
            g.fillRect(x(i) - body / 2, price.bottom() - height, body, height);
        }
    }

    private void drawBands(GraphicsContext g, int first, int end) {
        Indicators.Bands b = analysis.bollinger();
        List<double[]> upper = new ArrayList<>();
        List<double[]> lower = new ArrayList<>();
        for (int i = first; i <= end; i++) {
            if (!Double.isNaN(b.upper()[i])) {
                upper.add(new double[] {x(i), price.y(b.upper()[i])});
                lower.add(new double[] {x(i), price.y(b.lower()[i])});
            }
        }
        if (upper.size() < 2) {
            return;
        }
        g.setFill(theme.bands().deriveColor(0, 1, 1, 0.07));
        g.beginPath();
        g.moveTo(upper.getFirst()[0], upper.getFirst()[1]);
        upper.forEach(p -> g.lineTo(p[0], p[1]));
        for (int i = lower.size() - 1; i >= 0; i--) {
            g.lineTo(lower.get(i)[0], lower.get(i)[1]);
        }
        g.closePath();
        g.fill();
        series(g, b.upper(), first, end, price, theme.bands(), 1);
        series(g, b.middle(), first, end, price, theme.bands().deriveColor(0, 1, 1, 0.6), 1);
        series(g, b.lower(), first, end, price, theme.bands(), 1);
    }

    private void drawPrice(GraphicsContext g, int first, int end) {
        switch (type) {
            case LINE, AREA -> {
                double[] closes = shown.stream().mapToDouble(Candle::close).toArray();
                if (type == ChartType.AREA && end > first) {
                    g.setFill(new LinearGradient(0, price.top(), 0, price.bottom(), false, CycleMethod.NO_CYCLE,
                            new Stop(0, theme.line().deriveColor(0, 1, 1, 0.28)),
                            new Stop(1, theme.line().deriveColor(0, 1, 1, 0.02))));
                    g.beginPath();
                    g.moveTo(x(first), price.bottom());
                    for (int i = first; i <= end; i++) {
                        g.lineTo(x(i), price.y(closes[i]));
                    }
                    g.lineTo(x(end), price.bottom());
                    g.closePath();
                    g.fill();
                }
                series(g, closes, first, end, price, theme.line(), 2);
            }
            case BARS -> {
                double tick = Math.max(1, slot * 0.35);
                g.setLineWidth(Math.max(1, Math.min(2, slot / 6)));
                for (int i = first; i <= end; i++) {
                    Candle c = shown.get(i);
                    double px = crisp(x(i));
                    g.setStroke(c.up() ? theme.up() : theme.down());
                    g.strokeLine(px, price.y(c.high()), px, price.y(c.low()));
                    g.strokeLine(px - tick, price.y(c.open()), px, price.y(c.open()));
                    g.strokeLine(px, price.y(c.close()), px + tick, price.y(c.close()));
                }
            }
            case CANDLES, HEIKIN_ASHI -> {
                double body = Math.max(1, slot * 0.72);
                for (int i = first; i <= end; i++) {
                    Candle c = shown.get(i);
                    Color color = c.up() ? theme.up() : theme.down();
                    double px = crisp(x(i));
                    g.setStroke(color);
                    g.setLineWidth(1);
                    g.strokeLine(px, price.y(c.high()), px, price.y(c.low()));
                    if (slot >= 3) {
                        double top = price.y(Math.max(c.open(), c.close()));
                        double height = Math.max(1, price.y(Math.min(c.open(), c.close())) - top);
                        g.setFill(color);
                        g.fillRect(Math.round(px - body / 2), top, Math.max(1, Math.round(body)), height);
                    }
                }
            }
        }
    }

    private void series(GraphicsContext g, double[] values, int first, int end, Pane p, Color color, double width) {
        g.setStroke(color);
        g.setLineWidth(width);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        boolean drawing = false;
        g.beginPath();
        for (int i = first; i <= end; i++) {
            if (Double.isNaN(values[i])) {
                drawing = false;
                continue;
            }
            if (drawing) {
                g.lineTo(x(i), p.y(values[i]));
            } else {
                g.moveTo(x(i), p.y(values[i]));
                drawing = true;
            }
        }
        g.stroke();
    }

    private void drawLevelLines(GraphicsContext g) {
        g.setLineWidth(1);
        for (Levels.Level level : analysis.levels()) {
            double py = crisp(price.y(level.price()));
            if (!price.contains(py)) {
                continue;
            }
            g.setStroke((level.support() ? theme.up() : theme.down()).deriveColor(0, 1, 1, 0.55));
            g.setLineDashes(6, 5);
            g.strokeLine(0, py, plotRight, py);
            g.setLineDashes((double[]) null);
        }
    }

    private void drawPatterns(GraphicsContext g, int first, int end) {
        int lastIndex = last();
        int horizon = analysis.interval().horizonBars();
        Map<PatternKind, Pattern> newest = new EnumMap<>(PatternKind.class);
        for (Pattern p : analysis.patterns()) {
            newest.put(p.kind(), p);
        }
        List<Box> taken = new ArrayList<>(flags.stream().map(Flag::box).toList());
        g.save();
        g.beginPath();
        g.rect(0, price.top(), plotRight, price.bottom() - price.top());
        g.clip();
        for (Pattern p : analysis.patterns()) {
            if (p.confirm() < first || p.start() > end) {
                continue;
            }
            boolean recent = p.confirm() > lastIndex - horizon;
            boolean shape = p.points().size() >= 4;
            Color color = theme.direction(p.kind().direction()).deriveColor(0, 1, 1, recent ? 0.95 : 0.45);
            if (p.points().size() >= 3) {
                g.setStroke(color);
                g.setLineWidth(recent ? 1.8 : 1.2);
                g.setLineDashes(recent ? null : new double[] {4, 3});
                g.beginPath();
                g.moveTo(x(p.points().getFirst().index()), price.y(p.points().getFirst().price()));
                for (Pattern.Point point : p.points()) {
                    g.lineTo(x(point.index()), price.y(point.price()));
                }
                g.stroke();
                g.setLineDashes((double[]) null);
                if (!Double.isNaN(p.level())) {
                    g.setStroke(color.deriveColor(0, 1, 1, 0.6));
                    g.setLineDashes(2, 3);
                    g.strokeLine(x(p.start()), price.y(p.level()), x(p.confirm()), price.y(p.level()));
                    g.setLineDashes((double[]) null);
                }
            }
            if (recent || shape) {
                marker(g, p, color, newest.get(p.kind()) == p || shape, taken);
            }
        }
        g.restore();
    }

    private void marker(GraphicsContext g, Pattern p, Color color, boolean label, List<Box> taken) {
        Candle c = shown.get(p.confirm());
        double px = x(p.confirm());
        double size = Math.max(3.5, Math.min(6, slot * 0.55));
        boolean up = p.kind().bullish();
        double py = up ? price.y(c.low()) + 5 : price.y(c.high()) - 5;
        g.setFill(color);
        double tip = up ? py : py;
        double baseY = up ? py + size * 1.5 : py - size * 1.5;
        g.fillPolygon(new double[] {px - size, px + size, px}, new double[] {baseY, baseY, tip}, 3);
        if (!label) {
            return;
        }
        String text = p.kind().label();
        double width = width(text, BOLD) + 10;
        double height = 18;
        double top = up ? baseY + 3 : baseY - 3 - height;
        double left = Math.clamp(px - width / 2, 2, plotRight - width - 2);
        Box box = new Box(left, top, width, height);
        for (Box other : taken) {
            if (box.x() < other.x() + other.w() && other.x() < box.x() + box.w()
                    && box.y() < other.y() + other.h() && other.y() < box.y() + box.h()) {
                return;
            }
        }
        taken.add(box);
        g.setFill(color);
        g.fillRoundRect(box.x(), box.y(), box.w(), box.h(), 4, 4);
        g.setFill(Color.WHITE);
        g.setFont(BOLD);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, box.x() + box.w() / 2, box.y() + box.h() / 2);
    }

    private List<EconomicEvent> visibleEvents() {
        List<EconomicEvent> out = new ArrayList<>();
        for (EconomicEvent e : events) {
            if (e.impact() == EconomicEvent.Impact.HIGH) { // medium ones are in the calendar card
                double px = x(indexOf(analysis.candles(), analysis.interval(), e.time()));
                if (px >= 0 && px <= plotRight) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private Color impactColor(EconomicEvent.Impact impact) {
        return switch (impact) {
            case HIGH -> theme.high();
            case MEDIUM -> theme.medium();
            case LOW -> theme.low();
            case HOLIDAY, NONE -> theme.textSecondary();
        };
    }

    private void drawEventLines(GraphicsContext g) {
        g.setLineWidth(1);
        for (EconomicEvent e : visibleEvents()) {
            if (e.impact() != EconomicEvent.Impact.HIGH) {
                continue;
            }
            double px = crisp(x(indexOf(analysis.candles(), analysis.interval(), e.time())));
            g.setStroke(theme.high().deriveColor(0, 1, 1, 0.35));
            g.setLineDashes(2, 4);
            g.strokeLine(px, price.top(), px, price.bottom());
            g.setLineDashes((double[]) null);
        }
    }

    /** Places the event flags (before the pattern labels, which then keep clear of them). */
    private void layoutFlags() {
        for (EconomicEvent e : visibleEvents()) {
            double px = x(indexOf(analysis.candles(), analysis.interval(), e.time()));
            double w = 30;
            double h = 15;
            Box box = null;
            for (int level = 0; level < 3 && box == null; level++) { // the lowest free row
                Box candidate = new Box(px - w / 2, price.bottom() - h - 3 - level * (h + 2), w, h);
                boolean free = flags.stream().map(Flag::box).noneMatch(b -> b.x() < candidate.x() + candidate.w() + 2
                        && candidate.x() < b.x() + b.w() + 2 && b.y() == candidate.y());
                if (free) {
                    box = candidate;
                }
            }
            if (box != null) { // otherwise a crowded moment: the calendar card lists them all
                flags.add(new Flag(box, e));
            }
        }
    }

    private void drawEventFlags(GraphicsContext g) {
        for (Flag flag : flags) {
            Box box = flag.box();
            EconomicEvent e = flag.event();
            double w = box.w();
            double h = box.h();
            Color color = impactColor(e.impact());
            g.setFill(color);
            g.fillRoundRect(box.x(), box.y(), box.w(), box.h(), 4, 4);
            g.setFill(e.impact() == EconomicEvent.Impact.MEDIUM && !theme.dark() ? theme.text() : Color.WHITE);
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(e.currency(), box.x() + w / 2, box.y() + h / 2);
        }
    }

    private void drawEventTooltip(GraphicsContext g, double w) {
        if (hoverX < 0) {
            return;
        }
        for (Flag flag : flags) {
            if (!flag.box().contains(hoverX, hoverY)) {
                continue;
            }
            EconomicEvent e = flag.event();
            List<String> lines = new ArrayList<>();
            lines.add(e.currency() + " · " + e.title());
            lines.add(e.impact().label() + " impact · " + java.time.format.DateTimeFormatter
                    .ofPattern("EEE d MMM HH:mm", Locale.ENGLISH).withZone(zone).format(e.time()));
            String figures = (e.forecast().isEmpty() ? "" : "Forecast " + e.forecast())
                    + (e.forecast().isEmpty() || e.previous().isEmpty() ? "" : " · ")
                    + (e.previous().isEmpty() ? "" : "Previous " + e.previous());
            if (!figures.isEmpty()) {
                lines.add(figures);
            }
            lines.add("Forex Factory calendar");
            double width = 0;
            for (String line : lines) {
                width = Math.max(width, width(line, TEXT));
            }
            width += 20;
            double height = lines.size() * 17 + 10;
            double left = Math.clamp(flag.box().x() + flag.box().w() / 2 - width / 2, 4, w - width - 4);
            double top = flag.box().y() - height - 6;
            g.setFill(theme.dark() ? Color.web("#2A2E39") : Color.WHITE);
            g.fillRoundRect(left, top, width, height, 6, 6);
            g.setStroke(theme.border());
            g.setLineWidth(1);
            g.strokeRoundRect(left, top, width, height, 6, 6);
            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.TOP);
            for (int k = 0; k < lines.size(); k++) {
                g.setFont(k == 0 ? BOLD : TEXT);
                g.setFill(k == 0 ? theme.text() : k == lines.size() - 1 ? theme.textSecondary()
                        : k == 1 ? impactColor(e.impact()) : theme.text());
                g.fillText(lines.get(k), left + 10, top + 6 + k * 17);
            }
            return;
        }
    }

    private void drawLastPriceLine(GraphicsContext g) {
        Candle c = shown.getLast();
        double py = crisp(price.y(c.close()));
        if (!price.contains(py)) {
            return;
        }
        g.setStroke((c.up() ? theme.up() : theme.down()).deriveColor(0, 1, 1, 0.8));
        g.setLineWidth(1);
        g.setLineDashes(1, 3);
        g.strokeLine(0, py, plotRight, py);
        g.setLineDashes((double[]) null);
    }

    private void drawRsi(GraphicsContext g, int first, int end, Pane p) {
        g.setFill(theme.rsi().deriveColor(0, 1, 1, 0.08));
        g.fillRect(0, p.y(70), plotRight, p.y(30) - p.y(70));
        g.setStroke(theme.textSecondary().deriveColor(0, 1, 1, 0.5));
        g.setLineWidth(1);
        g.setLineDashes(4, 4);
        g.strokeLine(0, crisp(p.y(70)), plotRight, crisp(p.y(70)));
        g.strokeLine(0, crisp(p.y(30)), plotRight, crisp(p.y(30)));
        g.setLineDashes((double[]) null);
        series(g, analysis.rsi(), first, end, p, theme.rsi(), 1.5);
    }

    private void drawMacd(GraphicsContext g, int first, int end, Pane p) {
        Indicators.Macd m = analysis.macd();
        double body = Math.max(1, slot * 0.72);
        for (int i = first; i <= end; i++) {
            double v = m.histogram()[i];
            if (Double.isNaN(v)) {
                continue;
            }
            double previous = i > 0 ? m.histogram()[i - 1] : v;
            boolean rising = Double.isNaN(previous) || v >= previous;
            Color color = v >= 0 ? (rising ? theme.up() : theme.up().deriveColor(0, 1, 1, 0.45))
                    : (rising ? theme.down().deriveColor(0, 1, 1, 0.45) : theme.down());
            g.setFill(color);
            double y0 = p.y(0);
            double y1 = p.y(v);
            g.fillRect(x(i) - body / 2, Math.min(y0, y1), body, Math.max(1, Math.abs(y1 - y0)));
        }
        series(g, m.line(), first, end, p, theme.macd(), 1.4);
        series(g, m.signal(), first, end, p, theme.signal(), 1.4);
    }

    private void drawFrame(GraphicsContext g, double w, double h, double priceBottom, Pane rsi, Pane macd) {
        g.setStroke(theme.border());
        g.setLineWidth(1);
        g.strokeLine(crisp(plotRight), 0, crisp(plotRight), h);
        g.strokeLine(0, crisp(plotBottom), w, crisp(plotBottom));
        if (rsi != null) {
            g.strokeLine(0, crisp(rsi.top()), w, crisp(rsi.top()));
        }
        if (macd != null) {
            g.strokeLine(0, crisp(macd.top()), w, crisp(macd.top()));
        }
    }

    private void drawPriceAxis(GraphicsContext g, Pane rsi, Pane macd) {
        g.setFont(MONO);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(theme.textSecondary());
        for (double v : priceTicks(price)) {
            g.fillText(Format.price(v), plotRight + 8, price.y(v));
        }
        if (rsi != null) {
            g.fillText("70", plotRight + 8, rsi.y(70));
            g.fillText("30", plotRight + 8, rsi.y(30));
        }
        if (macd != null) {
            g.fillText("0", plotRight + 8, macd.y(0));
        }
        // Tags on the scale: levels first, the last price over them, the crosshair over all.
        if (levels) {
            for (Levels.Level level : analysis.levels()) {
                double py = price.y(level.price());
                if (price.contains(py)) {
                    Color color = level.support() ? theme.up() : theme.down();
                    outlinedTag(g, Format.price(level.price()), py, color);
                }
            }
        }
        Candle c = shown.getLast();
        double py = price.y(c.close());
        if (price.contains(py)) {
            filledTag(g, Format.price(c.close()), py, c.up() ? theme.up() : theme.down(), Color.WHITE);
        }
    }

    private void outlinedTag(GraphicsContext g, String text, double py, Color color) {
        double w = canvas.getWidth() - plotRight - 6;
        g.setFill(theme.background());
        g.fillRoundRect(plotRight + 3, py - 9, w, 18, 3, 3);
        g.setStroke(color);
        g.setLineWidth(1);
        g.strokeRoundRect(plotRight + 3.5, py - 8.5, w - 1, 17, 3, 3);
        g.setFill(color);
        g.setFont(MONO);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, plotRight + 8, py);
    }

    private void filledTag(GraphicsContext g, String text, double py, Color background, Color foreground) {
        double w = canvas.getWidth() - plotRight - 6;
        g.setFill(background);
        g.fillRoundRect(plotRight + 3, py - 9, w, 18, 3, 3);
        g.setFill(foreground);
        g.setFont(MONO);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, plotRight + 8, py);
    }

    private void drawTimeAxis(GraphicsContext g, List<Integer> ticks) {
        g.setFont(SMALL);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(theme.textSecondary());
        for (int i : ticks) {
            g.fillText(Format.axisTime(timeOf(i), analysis.interval(), zone), x(i), plotBottom + AXIS_BOTTOM / 2);
        }
    }

    private void drawLegends(GraphicsContext g, Pane rsi, Pane macd) {
        int i = hoverIndex();
        Candle c = shown.get(i);
        double previous = i > 0 ? shown.get(i - 1).close() : c.open();
        Color value = c.close() >= previous ? theme.up() : theme.down();
        double x = 8;
        double y = 14;
        x = run(g, title + "   ", x, y, BOLD, theme.text());
        for (String[] part : new String[][] {{"O", Format.price(c.open())}, {"H", Format.price(c.high())},
                {"L", Format.price(c.low())}, {"C", Format.price(c.close())}}) {
            x = run(g, part[0] + " ", x, y, TEXT, theme.textSecondary());
            x = run(g, part[1] + "  ", x, y, TEXT, value);
        }
        double change = c.close() - previous;
        run(g, (change >= 0 ? "+" : "−") + Format.priceLike(Math.abs(change), c.close()) + " ("
                + Format.change(previous == 0 ? 0 : change / previous * 100) + ")", x, y, TEXT, value);
        y += 20;
        if (volume && c.volume() > 0) {
            x = run(g, "Vol  ", 8, y, TEXT, theme.textSecondary());
            run(g, Format.compact(c.volume()), x, y, TEXT, value);
            y += 18;
        }
        if (averages) {
            x = 8;
            x = indicator(g, "SMA 20", analysis.sma20()[Math.min(i, last())], theme.sma20(), x, y);
            x = indicator(g, "SMA 50", analysis.sma50()[Math.min(i, last())], theme.sma50(), x, y);
            indicator(g, "SMA 200", analysis.sma200()[Math.min(i, last())], theme.sma200(), x, y);
            y += 18;
        }
        if (bands) {
            int k = Math.min(i, last());
            x = run(g, "BB 20 2  ", 8, y, TEXT, theme.textSecondary());
            x = run(g, number(analysis.bollinger().upper()[k]) + "  ", x, y, TEXT, theme.bands());
            x = run(g, number(analysis.bollinger().middle()[k]) + "  ", x, y, TEXT, theme.bands());
            run(g, number(analysis.bollinger().lower()[k]), x, y, TEXT, theme.bands());
        }
        int k = Math.min(i, last());
        if (rsi != null) {
            double rx = run(g, "RSI 14  ", 8, rsi.top() + 12, TEXT, theme.textSecondary());
            run(g, Double.isNaN(analysis.rsi()[k]) ? "—" : String.format(Locale.ROOT, "%.2f", analysis.rsi()[k]),
                    rx, rsi.top() + 12, TEXT, theme.rsi());
        }
        if (macd != null) {
            Indicators.Macd m = analysis.macd();
            double mx = run(g, "MACD 12 26 9  ", 8, macd.top() + 12, TEXT, theme.textSecondary());
            mx = run(g, small(m.histogram()[k]) + "  ", mx, macd.top() + 12, TEXT,
                    m.histogram()[k] >= 0 ? theme.up() : theme.down());
            mx = run(g, small(m.line()[k]) + "  ", mx, macd.top() + 12, TEXT, theme.macd());
            run(g, small(m.signal()[k]), mx, macd.top() + 12, TEXT, theme.signal());
        }
    }

    private double indicator(GraphicsContext g, String name, double value, Color color, double x, double y) {
        x = run(g, name + " ", x, y, TEXT, theme.textSecondary());
        return run(g, number(value) + "    ", x, y, TEXT, color);
    }

    private int hoverIndex() {
        if (hoverX >= 0 && hoverX < plotRight && hoverY < plotBottom) {
            return Math.clamp(indexAt(hoverX), 0, last());
        }
        return last();
    }

    private void drawCrosshair(GraphicsContext g, double w, Pane rsi, Pane macd) {
        if (hoverX < 0 || hoverX >= plotRight || hoverY >= plotBottom || drag != Drag.NONE) {
            return;
        }
        int index = indexAt(hoverX);
        double px = crisp(x(index));
        g.setStroke(theme.crosshair());
        g.setLineWidth(1);
        g.setLineDashes(4, 4);
        g.strokeLine(px, 0, px, plotBottom);
        double py = crisp(hoverY);
        g.strokeLine(0, py, plotRight, py);
        g.setLineDashes((double[]) null);
        String value = null;
        if (price.contains(hoverY)) {
            value = Format.price(price.value(hoverY));
        } else if (rsi != null && rsi.contains(hoverY)) {
            value = String.format(Locale.ROOT, "%.2f", rsi.value(hoverY));
        } else if (macd != null && macd.contains(hoverY)) {
            value = small(macd.value(hoverY));
        }
        if (value != null) {
            filledTag(g, value, hoverY, theme.tag(), theme.tagText());
        }
        String time = Format.candleTime(timeOf(Math.max(0, index)), analysis.interval(), zone);
        double tw = width(time, SMALL) + 16;
        double left = Math.clamp(px - tw / 2, 0, plotRight - tw);
        g.setFill(theme.tag());
        g.fillRoundRect(left, plotBottom + 3, tw, AXIS_BOTTOM - 6, 3, 3);
        g.setFill(theme.tagText());
        g.setFont(SMALL);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(time, left + tw / 2, plotBottom + AXIS_BOTTOM / 2);
    }

    private void drawScaleButtons(GraphicsContext g, double w, double h) {
        double y = plotBottom + 5;
        double height = AXIS_BOTTOM - 10;
        logBox = new Box(plotRight + 6, y, 34, height);
        autoBox = new Box(plotRight + 44, y, 34, height);
        button(g, logBox, "LOG", log);
        button(g, autoBox, "AUTO", auto);
    }

    private void button(GraphicsContext g, Box b, String text, boolean on) {
        g.setFill(on ? theme.line().deriveColor(0, 1, 1, 0.16) : theme.background());
        g.fillRoundRect(b.x(), b.y(), b.w(), b.h(), 4, 4);
        g.setFill(on ? theme.line() : theme.textSecondary());
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, b.x() + b.w() / 2, b.y() + b.h() / 2);
    }

    private void drawNowButton(GraphicsContext g) {
        if (rightIndex >= last() + 1) {
            return;
        }
        double size = 26;
        nowBox = new Box(plotRight - size - 12, price.bottom() - size - 26, size, size);
        g.setFill(theme.dark() ? Color.web("#2A2E39") : Color.WHITE);
        g.fillOval(nowBox.x(), nowBox.y(), size, size);
        g.setStroke(theme.border());
        g.strokeOval(nowBox.x() + 0.5, nowBox.y() + 0.5, size - 1, size - 1);
        g.setFill(theme.text());
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText("»", nowBox.x() + size / 2, nowBox.y() + size / 2 - 1);
    }

    // Helpers --------------------------------------------------------------------------------------

    private double run(GraphicsContext g, String text, double x, double y, Font font, Color color) {
        g.setFont(font);
        g.setFill(color);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, x, y);
        return x + width(text, font);
    }

    private double width(String text, Font font) {
        probe.setText(text);
        probe.setFont(font);
        return probe.getLayoutBounds().getWidth();
    }

    private static double crisp(double v) {
        return Math.floor(v) + 0.5;
    }

    private static String number(double v) {
        return Double.isNaN(v) ? "—" : Format.price(v);
    }

    private static String small(double v) {
        return Double.isNaN(v) ? "—" : String.format(Locale.ROOT, "%.4g", v);
    }

    /** Round numbers between {@code min} and {@code max}, about {@code count} of them. */
    static List<Double> niceTicks(double min, double max, int count) {
        List<Double> out = new ArrayList<>();
        double range = max - min;
        if (!(range > 0)) {
            return out;
        }
        double rough = range / count;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
        double normal = rough / magnitude;
        double step = (normal < 1.5 ? 1 : normal < 3 ? 2 : normal < 7 ? 5 : 10) * magnitude;
        for (double v = Math.ceil(min / step) * step; v <= max; v += step) {
            out.add(v);
        }
        return out;
    }
}
