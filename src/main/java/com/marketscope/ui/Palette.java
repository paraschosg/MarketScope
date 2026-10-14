package com.marketscope.ui;

import javafx.scene.paint.Color;

/** The colours drawn on canvases; the same tokens as {@code marketscope.css}. */
public final class Palette {

    public static final Color BG = Color.web("#FBFAF8");
    public static final Color SURFACE = Color.web("#FFFFFF");
    public static final Color SUNKEN = Color.web("#F6F4F0");
    public static final Color GRID = Color.web("#EEEBE5");
    public static final Color BORDER = Color.web("#E2DED6");
    public static final Color TEXT = Color.web("#211F1B");
    public static final Color TEXT_SECONDARY = Color.web("#6A655D");
    public static final Color ACCENT = Color.web("#A04A2C");

    /** Rising candles and bullish marks. */
    public static final Color UP = Color.web("#2F7A55");
    /** Falling candles and bearish marks. */
    public static final Color DOWN = Color.web("#B5473A");

    public static final Color SMA20 = Color.web("#36566F");
    public static final Color SMA50 = Color.web("#C08A1E");
    public static final Color SMA200 = Color.web("#8C3F86");
    public static final Color BANDS = Color.web("#5C85A3");
    public static final Color RSI = Color.web("#6D4FA3");
    public static final Color MACD = Color.web("#36566F");
    public static final Color SIGNAL = Color.web("#C08A1E");

    private Palette() {
    }

    public static Color direction(int direction) {
        return direction > 0 ? UP : direction < 0 ? DOWN : TEXT_SECONDARY;
    }
}
