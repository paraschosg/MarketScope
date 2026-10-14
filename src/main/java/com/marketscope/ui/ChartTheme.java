package com.marketscope.ui;

import javafx.scene.paint.Color;

/** The colours of the chart canvas, in TradingView's light and dark styles. */
public record ChartTheme(boolean dark, Color background, Color grid, Color text, Color textSecondary, Color border,
                         Color crosshair, Color tag, Color tagText, Color up, Color down, Color line, Color sma20,
                         Color sma50, Color sma200, Color bands, Color rsi, Color macd, Color signal, Color high,
                         Color medium, Color low) {

    public static final ChartTheme LIGHT = new ChartTheme(false,
            Color.web("#FFFFFF"), Color.web("#F0F3FA"), Color.web("#131722"), Color.web("#787B86"),
            Color.web("#E0E3EB"), Color.web("#9598A1"), Color.web("#131722"), Color.WHITE,
            Color.web("#089981"), Color.web("#F23645"), Color.web("#2962FF"),
            Color.web("#2962FF"), Color.web("#FF9800"), Color.web("#9C27B0"), Color.web("#2196F3"),
            Color.web("#7E57C2"), Color.web("#2962FF"), Color.web("#FF6D00"),
            Color.web("#F23645"), Color.web("#FF9800"), Color.web("#C9A700"));

    public static final ChartTheme DARK = new ChartTheme(true,
            Color.web("#131722"), Color.web("#1E2230"), Color.web("#D1D4DC"), Color.web("#787B86"),
            Color.web("#2A2E39"), Color.web("#758696"), Color.web("#363A45"), Color.web("#D1D4DC"),
            Color.web("#089981"), Color.web("#F23645"), Color.web("#2962FF"),
            Color.web("#2962FF"), Color.web("#FF9800"), Color.web("#CE93D8"), Color.web("#64B5F6"),
            Color.web("#B39DDB"), Color.web("#2962FF"), Color.web("#FF6D00"),
            Color.web("#F23645"), Color.web("#FF9800"), Color.web("#FFEB3B"));

    public Color direction(int direction) {
        return direction > 0 ? up : direction < 0 ? down : textSecondary;
    }
}
