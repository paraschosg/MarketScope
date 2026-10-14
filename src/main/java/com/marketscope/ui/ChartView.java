package com.marketscope.ui;

import com.marketscope.analysis.Analysis;
import com.marketscope.analysis.Indicators;
import com.marketscope.analysis.Levels;
import com.marketscope.analysis.Pattern;
import com.marketscope.model.Candle;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * A candlestick chart with moving averages, Bollinger bands, support and resistance, pattern
 * shapes, and volume, RSI and MACD panels below. Mouse wheel zooms, dragging pans, a
 * double-click returns to the latest candles, and the crosshair reads out any candle.
 * JavaFX thread only.
 */
public final class ChartView extends Region {

    static final double AXIS_RIGHT = 78;
    static final double AXIS_BOTTOM = 22;
    static final double GAP = 10;
    static final double LEFT = 10;
    static final double TOP = 30;
    static final int DEFAULT_VISIBLE = 160;
    static final int MIN_VISIBLE = 30;

    private static final Font LABEL = Font.font("Segoe UI", 11);
    private static final Font LABEL_BOLD = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11);
    private static final Font NUMBERS = Font.font("Consolas", 11);
    private static final Font READOUT = Font.font("Consolas", 12);

    /** One stacked panel: its vertical extent and the value range it shows. */
    private record Panel(double top, double bottom, double min, double max) {
        double y(double value) {
            return max == min ? (top + bottom) / 2 : bottom - (value - min) / (max - min) * (bottom - top);
        }

        double value(double y) {
            return min + (bottom - y) / (bottom - top) * (max - min);
        }

        boolean contains(double y) {
            return y >= top && y <= bottom;
        }
    }

    private final Canvas canvas = new Canvas();
    private Analysis analysis;
    private ZoneId zone = ZoneId.systemDefault();
    private int visible = DEFAULT_VISIBLE;
    private int end = -1;
    private int hover = -1;
    private double hoverY = -1;
    private boolean averages = true;
    private boolean bands = false;
    private boolean levels = true;
    private boolean patterns = true;
    private double dragX;
    private int dragEnd;

    private double plotRight;
    private double slot;
    private Panel price;

    public ChartView() {
        getChildren().add(canvas);
        setMinSize(320, 260);
        setOnScroll(e -> {
            if (analysis == null) {
                return;
            }
            double factor = e.getDeltaY() > 0 ? 1 / 1.15 : 1.15;
            visible = clampVisible((int) Math.round(visible * factor));
            end = clampEnd(end);
            draw();
        });
        setOnMousePressed(e -> {
            dragX = e.getX();
            dragEnd = end;
        });
        setOnMouseDragged(e -> {
            if (analysis == null || slot <= 0) {
                return;
            }
            int shift = (int) Math.round((dragX - e.getX()) / slot);
            end = clampEnd(dragEnd + shift);
            hover = -1;
            draw();
        });
        setOnMouseMoved(e -> {
            if (analysis == null) {
                return;
            }
            hover = e.getX() < plotRight ? indexAt(e.getX()) : -1;
            hoverY = e.getY();
            draw();
        });
        setOnMouseExited(e -> {
            hover = -1;
            draw();
        });
        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                resetView();
            }
        });
    }

    /** Shows a new chart from its latest candles. */
    public void show(Analysis next, ZoneId timeZone) {
        analysis = next;
        zone = timeZone;
        resetView();
    }

    /** Replaces the data of the chart shown, following the newest candle if the view was on it. */
    public void update(Analysis next) {
        boolean following = analysis == null || end >= analysis.candles().size() - 1;
        analysis = next;
        end = following ? next.candles().size() - 1 : clampEnd(end);
        visible = clampVisible(visible);
        draw();
    }

    public void clear() {
        analysis = null;
        draw();
    }

    public void resetView() {
        if (analysis != null) {
            visible = clampVisible(DEFAULT_VISIBLE);
            end = analysis.candles().size() - 1;
        }
        hover = -1;
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

    private int clampVisible(int value) {
        int size = analysis == null ? DEFAULT_VISIBLE : analysis.candles().size();
        return Math.max(Math.min(MIN_VISIBLE, size), Math.min(value, Math.max(size, MIN_VISIBLE)));
    }

    private int clampEnd(int value) {
        int size = analysis.candles().size();
        return Math.max(Math.min(visible - 1, size - 1), Math.min(value, size - 1));
    }

    private int first() {
        return Math.max(0, end - visible + 1);
    }

    private double x(int index) {
        return plotRight - (end - index + 0.5) * slot;
    }

    private int indexAt(double px) {
        int i = end - (int) Math.floor((plotRight - px) / slot);
        return Math.max(first(), Math.min(end, i));
    }

    // Drawing ------------------------------------------------------------------------------------

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Palette.SURFACE);
        g.fillRect(0, 0, w, h);
        if (analysis == null || w < 100 || h < 120) {
            return;
        }
        List<Candle> candles = analysis.candles();
        int first = first();
        plotRight = w - AXIS_RIGHT;
        slot = (plotRight - LEFT) / visible;
        double bottom = h - AXIS_BOTTOM;
        boolean hasVolume = candles.stream().anyMatch(c -> c.volume() > 0); // forex has none
        double total = bottom - TOP - (hasVolume ? 3 : 2) * GAP;
        double priceBottom = TOP + total * (hasVolume ? 0.60 : 0.70);
        double volumeBottom = hasVolume ? priceBottom + GAP + total * 0.10 : priceBottom;
        double rsiBottom = volumeBottom + GAP + total * 0.13;

        price = pricePanel(TOP, priceBottom, first);
        Panel volume = hasVolume ? new Panel(priceBottom + GAP, volumeBottom, 0, maxVolume(first)) : null;
        Panel rsi = new Panel(volumeBottom + GAP, rsiBottom, 0, 100);
        Panel macd = macdPanel(rsiBottom + GAP, bottom, first);

        drawTimeGrid(g, first, TOP, bottom);
        drawPriceGrid(g, price, w);
        if (bands) {
            drawBands(g, first);
        }
        drawCandles(g, candles, first);
        if (averages) {
            drawLine(g, analysis.sma20(), first, price, Palette.SMA20, 1.3);
            drawLine(g, analysis.sma50(), first, price, Palette.SMA50, 1.4);
            drawLine(g, analysis.sma200(), first, price, Palette.SMA200, 1.6);
        }
        if (levels) {
            drawLevels(g);
        }
        if (patterns) {
            drawPatterns(g, first);
        }
        drawLastPrice(g, candles, w);
        if (volume != null) {
            drawVolume(g, candles, first, volume);
        }
        drawRsi(g, first, rsi);
        drawMacd(g, first, macd);
        drawPanelLabels(g, volume, rsi, macd, first);
        if (volume != null) {
            drawSeparators(g, w, priceBottom, volumeBottom, rsiBottom);
        } else {
            drawSeparators(g, w, priceBottom, rsiBottom);
        }
        drawCrosshair(g, w, bottom);
        drawReadout(g, candles);
        drawLegend(g);
    }

    private Panel pricePanel(double top, double bottom, int first) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int i = first; i <= end; i++) {
            Candle c = analysis.candles().get(i);
            lo = Math.min(lo, c.low());
            hi = Math.max(hi, c.high());
            if (averages) {
                for (double[] series : List.of(analysis.sma20(), analysis.sma50(), analysis.sma200())) {
                    if (!Double.isNaN(series[i])) {
                        lo = Math.min(lo, series[i]);
                        hi = Math.max(hi, series[i]);
                    }
                }
            }
            if (bands && !Double.isNaN(analysis.bollinger().upper()[i])) {
                lo = Math.min(lo, analysis.bollinger().lower()[i]);
                hi = Math.max(hi, analysis.bollinger().upper()[i]);
            }
        }
        double pad = Math.max((hi - lo) * 0.06, Math.abs(hi) * 1e-6);
        return new Panel(top, bottom, lo - pad, hi + pad);
    }

    private double maxVolume(int first) {
        double max = 0;
        for (int i = first; i <= end; i++) {
            max = Math.max(max, analysis.candles().get(i).volume());
        }
        return max == 0 ? 1 : max * 1.05;
    }

    private Panel macdPanel(double top, double bottom, int first) {
        Indicators.Macd m = analysis.macd();
        double max = 0;
        for (int i = first; i <= end; i++) {
            for (double v : new double[] {m.line()[i], m.signal()[i], m.histogram()[i]}) {
                if (!Double.isNaN(v)) {
                    max = Math.max(max, Math.abs(v));
                }
            }
        }
        max = max == 0 ? 1 : max * 1.1;
        return new Panel(top, bottom, -max, max);
    }

    private void drawTimeGrid(GraphicsContext g, int first, double top, double bottom) {
        int count = end - first + 1;
        int step = Math.max(1, (int) Math.ceil(count / 6.0));
        g.setFont(LABEL);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        for (int i = first; i <= end; i++) {
            if (i % step != 0) {
                continue;
            }
            double px = x(i);
            g.setStroke(Palette.GRID);
            g.setLineWidth(1);
            g.strokeLine(px, top, px, bottom);
            g.setFill(Palette.TEXT_SECONDARY);
            g.fillText(Format.axisTime(analysis.candles().get(i).time(), analysis.interval(), zone), px,
                    bottom + 5);
        }
    }

    private void drawPriceGrid(GraphicsContext g, Panel p, double w) {
        g.setFont(NUMBERS);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        for (double v : niceTicks(p.min(), p.max(), 6)) {
            double py = p.y(v);
            g.setStroke(Palette.GRID);
            g.setLineWidth(1);
            g.strokeLine(LEFT, py, plotRight, py);
            g.setFill(Palette.TEXT_SECONDARY);
            g.fillText(Format.price(v), plotRight + 8, py);
        }
    }

    private void drawBands(GraphicsContext g, int first) {
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
        g.setFill(Palette.BANDS.deriveColor(0, 1, 1, 0.08));
        g.beginPath();
        g.moveTo(upper.getFirst()[0], upper.getFirst()[1]);
        for (double[] p : upper) {
            g.lineTo(p[0], p[1]);
        }
        for (int i = lower.size() - 1; i >= 0; i--) {
            g.lineTo(lower.get(i)[0], lower.get(i)[1]);
        }
        g.closePath();
        g.fill();
        drawLine(g, b.upper(), first, price, Palette.BANDS.deriveColor(0, 1, 1, 0.6), 1);
        drawLine(g, b.lower(), first, price, Palette.BANDS.deriveColor(0, 1, 1, 0.6), 1);
    }

    private void drawCandles(GraphicsContext g, List<Candle> candles, int first) {
        double body = Math.max(1, slot * 0.66);
        for (int i = first; i <= end; i++) {
            Candle c = candles.get(i);
            Color color = c.up() ? Palette.UP : Palette.DOWN;
            double px = x(i);
            g.setStroke(color);
            g.setLineWidth(1);
            g.strokeLine(px, price.y(c.high()), px, price.y(c.low()));
            double top = price.y(Math.max(c.open(), c.close()));
            double height = Math.max(1, price.y(Math.min(c.open(), c.close())) - top);
            g.setFill(color);
            g.fillRect(px - body / 2, top, body, height);
        }
    }

    private void drawLine(GraphicsContext g, double[] series, int first, Panel p, Color color, double width) {
        g.setStroke(color);
        g.setLineWidth(width);
        g.setLineCap(StrokeLineCap.ROUND);
        boolean drawing = false;
        g.beginPath();
        for (int i = first; i <= end; i++) {
            if (Double.isNaN(series[i])) {
                drawing = false;
                continue;
            }
            if (drawing) {
                g.lineTo(x(i), p.y(series[i]));
            } else {
                g.moveTo(x(i), p.y(series[i]));
                drawing = true;
            }
        }
        g.stroke();
    }

    private void drawLevels(GraphicsContext g) {
        g.setFont(NUMBERS);
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.LEFT);
        double lastLabel = Double.NEGATIVE_INFINITY;
        for (Levels.Level level : analysis.levels()) { // highest first
            double py = price.y(level.price());
            if (!price.contains(py)) {
                continue;
            }
            Color color = level.support() ? Palette.UP : Palette.DOWN;
            g.setStroke(color.deriveColor(0, 1, 1, 0.5));
            g.setLineWidth(1);
            g.setLineDashes(6, 4);
            g.strokeLine(LEFT, py, plotRight, py);
            g.setLineDashes((double[]) null);
            if (py - lastLabel < 15) {
                continue; // too close to the label above; the line is enough
            }
            lastLabel = py;
            String text = (level.support() ? "S " : "R ") + Format.price(level.price()) + " ×" + level.touches();
            Text probe = new Text(text);
            probe.setFont(NUMBERS);
            double width = probe.getLayoutBounds().getWidth() + 8;
            double left = LEFT + 4;
            g.setFill(Palette.SURFACE.deriveColor(0, 1, 1, 0.9));
            g.fillRoundRect(left, py - 8, width, 16, 4, 4);
            g.setStroke(color.deriveColor(0, 1, 1, 0.6));
            g.strokeRoundRect(left, py - 8, width, 16, 4, 4);
            g.setFill(color);
            g.fillText(text, left + 4, py);
        }
    }

    private void drawPatterns(GraphicsContext g, int first) {
        int last = analysis.candles().size() - 1;
        int horizon = analysis.interval().horizonBars();
        java.util.Map<com.marketscope.analysis.PatternKind, Pattern> newest =
                new java.util.EnumMap<>(com.marketscope.analysis.PatternKind.class);
        for (Pattern p : analysis.patterns()) {
            if (p.confirm() <= end) {
                newest.put(p.kind(), p);
            }
        }
        List<double[]> labels = new ArrayList<>();
        g.save();
        g.beginPath();
        g.rect(LEFT, price.top(), plotRight - LEFT, price.bottom() - price.top());
        g.clip();
        for (Pattern p : analysis.patterns()) {
            if (p.confirm() < first || p.start() > end) {
                continue;
            }
            boolean recent = p.confirm() > last - horizon;
            Color color = Palette.direction(p.kind().direction()).deriveColor(0, 1, 1, recent ? 0.95 : 0.5);
            if (p.points().size() >= 3) {
                g.setStroke(color);
                g.setLineWidth(recent ? 1.8 : 1.2);
                g.setLineDashes(recent ? null : new double[] {4, 3});
                g.beginPath();
                Pattern.Point start = p.points().getFirst();
                g.moveTo(x(start.index()), price.y(start.price()));
                for (Pattern.Point point : p.points()) {
                    g.lineTo(x(point.index()), price.y(point.price()));
                }
                g.stroke();
                g.setLineDashes((double[]) null);
                if (!Double.isNaN(p.level())) {
                    double py = price.y(p.level());
                    g.setStroke(color.deriveColor(0, 1, 1, 0.6));
                    g.setLineDashes(2, 3);
                    g.strokeLine(x(p.start()), py, x(p.confirm()), py);
                    g.setLineDashes((double[]) null);
                }
            }
            boolean shape = p.points().size() >= 4;
            if (p.confirm() >= first && p.confirm() <= end && (recent || shape)) {
                marker(g, p, color, newest.get(p.kind()) == p || shape, labels);
            }
        }
        g.restore();
    }

    private void marker(GraphicsContext g, Pattern p, Color color, boolean label, List<double[]> taken) {
        Candle c = analysis.candles().get(p.confirm());
        double px = x(p.confirm());
        double size = Math.max(4, Math.min(7, slot * 0.6));
        boolean up = p.kind().bullish();
        double py = up ? price.y(c.low()) + 6 : price.y(c.high()) - 6;
        g.setFill(color);
        if (up) {
            g.fillPolygon(new double[] {px - size, px + size, px}, new double[] {py + size * 1.6, py + size * 1.6, py},
                    3);
        } else {
            g.fillPolygon(new double[] {px - size, px + size, px}, new double[] {py - size * 1.6, py - size * 1.6, py},
                    3);
        }
        if (label) {
            Text probe = new Text(p.kind().label());
            probe.setFont(LABEL_BOLD);
            double width = probe.getLayoutBounds().getWidth();
            double height = probe.getLayoutBounds().getHeight();
            double top = up ? py + size * 1.6 + 2 : py - size * 1.6 - 2 - height;
            double centre = Math.max(LEFT + width / 2 + 2, Math.min(plotRight - width / 2 - 2, px)); // inside the plot
            double[] box = {centre - width / 2 - 2, top, width + 4, height};
            for (double[] other : taken) {
                if (box[0] < other[0] + other[2] && other[0] < box[0] + box[2]
                        && box[1] < other[1] + other[3] && other[1] < box[1] + box[3]) {
                    return; // would cover another label: the marker alone will do
                }
            }
            taken.add(box);
            g.setFill(Palette.SURFACE.deriveColor(0, 1, 1, 0.8));
            g.fillRect(box[0], box[1], box[2], box[3]);
            g.setFill(color);
            g.setFont(LABEL_BOLD);
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.TOP);
            g.fillText(p.kind().label(), centre, top);
        }
    }

    private void drawLastPrice(GraphicsContext g, List<Candle> candles, double w) {
        Candle last = candles.getLast();
        double py = price.y(last.close());
        if (!price.contains(py)) {
            return;
        }
        Color color = last.up() ? Palette.UP : Palette.DOWN;
        g.setStroke(color.deriveColor(0, 1, 1, 0.7));
        g.setLineWidth(1);
        g.setLineDashes(2, 3);
        g.strokeLine(LEFT, py, plotRight, py);
        g.setLineDashes((double[]) null);
        tag(g, Format.price(last.close()), py, color, w);
    }

    private void tag(GraphicsContext g, String text, double py, Color background, double w) {
        g.setFill(background);
        g.fillRoundRect(plotRight + 2, py - 9, w - plotRight - 4, 18, 4, 4);
        g.setFill(Color.WHITE);
        g.setFont(NUMBERS);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, plotRight + 6, py);
    }

    private void drawVolume(GraphicsContext g, List<Candle> candles, int first, Panel p) {
        double body = Math.max(1, slot * 0.66);
        for (int i = first; i <= end; i++) {
            Candle c = candles.get(i);
            if (c.volume() <= 0) {
                continue;
            }
            g.setFill((c.up() ? Palette.UP : Palette.DOWN).deriveColor(0, 1, 1, 0.45));
            double top = p.y(c.volume());
            g.fillRect(x(i) - body / 2, top, body, p.bottom() - top);
        }
    }

    private void drawRsi(GraphicsContext g, int first, Panel p) {
        g.setFill(Palette.RSI.deriveColor(0, 1, 1, 0.06));
        g.fillRect(LEFT, p.y(70), plotRight - LEFT, p.y(30) - p.y(70));
        g.setStroke(Palette.BORDER);
        g.setLineWidth(1);
        g.setLineDashes(3, 3);
        g.strokeLine(LEFT, p.y(70), plotRight, p.y(70));
        g.strokeLine(LEFT, p.y(30), plotRight, p.y(30));
        g.setLineDashes((double[]) null);
        drawLine(g, analysis.rsi(), first, p, Palette.RSI, 1.4);
        g.setFont(NUMBERS);
        g.setFill(Palette.TEXT_SECONDARY);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.fillText("70", plotRight + 8, p.y(70));
        g.fillText("30", plotRight + 8, p.y(30));
    }

    private void drawMacd(GraphicsContext g, int first, Panel p) {
        Indicators.Macd m = analysis.macd();
        g.setStroke(Palette.BORDER);
        g.setLineWidth(1);
        g.strokeLine(LEFT, p.y(0), plotRight, p.y(0));
        double body = Math.max(1, slot * 0.66);
        for (int i = first; i <= end; i++) {
            double v = m.histogram()[i];
            if (Double.isNaN(v)) {
                continue;
            }
            g.setFill((v >= 0 ? Palette.UP : Palette.DOWN).deriveColor(0, 1, 1, 0.45));
            double y0 = p.y(0);
            double y1 = p.y(v);
            g.fillRect(x(i) - body / 2, Math.min(y0, y1), body, Math.max(1, Math.abs(y1 - y0)));
        }
        drawLine(g, m.line(), first, p, Palette.MACD, 1.3);
        drawLine(g, m.signal(), first, p, Palette.SIGNAL, 1.3);
    }

    private void drawPanelLabels(GraphicsContext g, Panel volume, Panel rsi, Panel macd, int first) {
        int i = hover >= 0 ? hover : end;
        g.setFont(LABEL_BOLD);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        if (volume != null) {
            label(g, "Volume  " + Format.compact(analysis.candles().get(i).volume()), volume.top() + 2);
        }
        label(g, "RSI 14  " + number(analysis.rsi()[i], "%.1f"), rsi.top() + 2);
        label(g, "MACD 12 26 9  " + number(analysis.macd().histogram()[i], "%.4g"), macd.top() + 2);
    }

    private void label(GraphicsContext g, String text, double y) {
        Text probe = new Text(text);
        probe.setFont(LABEL_BOLD);
        double width = probe.getLayoutBounds().getWidth();
        g.setFill(Palette.SURFACE.deriveColor(0, 1, 1, 0.85));
        g.fillRect(LEFT, y - 1, width + 8, 16);
        g.setFill(Palette.TEXT_SECONDARY);
        g.fillText(text, LEFT + 4, y);
    }

    private static String number(double v, String format) {
        return Double.isNaN(v) ? "—" : String.format(java.util.Locale.ROOT, format, v);
    }

    private void drawSeparators(GraphicsContext g, double w, double... ys) {
        g.setStroke(Palette.BORDER);
        g.setLineWidth(1);
        for (double y : ys) {
            g.strokeLine(0, y + GAP / 2, w, y + GAP / 2);
        }
        g.strokeLine(plotRight, TOP, plotRight, canvas.getHeight() - AXIS_BOTTOM);
    }

    private void drawCrosshair(GraphicsContext g, double w, double bottom) {
        if (hover < 0) {
            return;
        }
        g.setStroke(Palette.TEXT_SECONDARY.deriveColor(0, 1, 1, 0.55));
        g.setLineWidth(1);
        g.setLineDashes(4, 4);
        double px = x(hover);
        g.strokeLine(px, TOP, px, bottom);
        if (price.contains(hoverY)) {
            g.strokeLine(LEFT, hoverY, plotRight, hoverY);
            g.setLineDashes((double[]) null);
            tag(g, Format.price(price.value(hoverY)), hoverY, Palette.TEXT, w);
        }
        g.setLineDashes((double[]) null);
    }

    private void drawReadout(GraphicsContext g, List<Candle> candles) {
        int i = hover >= 0 ? hover : end;
        Candle c = candles.get(i);
        double previous = i > 0 ? candles.get(i - 1).close() : c.open();
        double change = previous == 0 ? 0 : (c.close() / previous - 1) * 100;
        String text = Format.candleTime(c.time(), analysis.interval(), zone) + "   O " + Format.price(c.open())
                + "  H " + Format.price(c.high()) + "  L " + Format.price(c.low()) + "  C " + Format.price(c.close())
                + "  " + Format.change(change) + (c.volume() > 0 ? "   Vol " + Format.compact(c.volume()) : "");
        g.setFont(READOUT);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(c.up() ? Palette.UP : Palette.DOWN);
        g.fillText(text, LEFT + 2, TOP / 2);
    }

    private void drawLegend(GraphicsContext g) {
        if (!averages && !bands) {
            return;
        }
        List<String> names = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        if (averages) {
            names.addAll(List.of("SMA 20", "SMA 50", "SMA 200"));
            colors.addAll(List.of(Palette.SMA20, Palette.SMA50, Palette.SMA200));
        }
        if (bands) {
            names.add("Bollinger 20, 2");
            colors.add(Palette.BANDS);
        }
        g.setFont(LABEL_BOLD);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        double width = 8;
        for (String name : names) {
            Text probe = new Text(name);
            probe.setFont(LABEL_BOLD);
            width += probe.getLayoutBounds().getWidth() + 22;
        }
        double y = price.top() + 11;
        g.setFill(Palette.SURFACE.deriveColor(0, 1, 1, 0.85));
        g.fillRoundRect(LEFT, price.top() + 2, width, 18, 4, 4);
        double x = LEFT + 6;
        for (int k = 0; k < names.size(); k++) {
            g.setStroke(colors.get(k));
            g.setLineWidth(2);
            g.strokeLine(x, y, x + 10, y);
            g.setFill(colors.get(k));
            g.fillText(names.get(k), x + 14, y);
            Text probe = new Text(names.get(k));
            probe.setFont(LABEL_BOLD);
            x += probe.getLayoutBounds().getWidth() + 22;
        }
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
