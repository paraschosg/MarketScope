package com.marketscope.ui;

import com.marketscope.model.Interval;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Numbers, prices and times as the app shows them. */
public final class Format {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    private Format() {
    }

    /** A price with as many decimals as its size needs: 83,169.00 · 1.1206 · 0.000012. */
    public static String price(double value) {
        if (Double.isNaN(value)) {
            return "—";
        }
        double a = Math.abs(value);
        int decimals = a >= 100 ? 2 : a >= 10 ? 3 : a >= 1 ? 4 : a >= 0.01 ? 5 : 8;
        DecimalFormat format = new DecimalFormat("#,##0." + "0".repeat(decimals), SYMBOLS);
        return format.format(value);
    }

    /** "+1.62 %" or "−0.40 %". */
    public static String change(double percent) {
        return (percent >= 0 ? "+" : "−") + String.format(Locale.ROOT, "%.2f %%", Math.abs(percent));
    }

    /** "14.8K", "1.2M", "3.4B". */
    public static String compact(double value) {
        double a = Math.abs(value);
        if (a >= 1e9) {
            return String.format(Locale.ROOT, "%.1fB", value / 1e9);
        }
        if (a >= 1e6) {
            return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        }
        if (a >= 1e3) {
            return String.format(Locale.ROOT, "%.1fK", value / 1e3);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.0f %%", fraction * 100);
    }

    /** "+6 pts" for an edge of 0.06. */
    public static String points(double fraction) {
        return String.format(Locale.ROOT, "%+.0f pts", fraction * 100);
    }

    /** "just now", "12 min ago", "3 h ago", "2 d ago". */
    public static String ago(Instant then, Instant now) {
        if (then == null) {
            return "";
        }
        Duration d = Duration.between(then, now);
        if (d.toMinutes() < 1) {
            return "just now";
        }
        if (d.toHours() < 1) {
            return d.toMinutes() + " min ago";
        }
        if (d.toDays() < 1) {
            return d.toHours() + " h ago";
        }
        return d.toDays() + " d ago";
    }

    /** A candle time for the axis and the readout, as fine as the interval needs. */
    public static String candleTime(Instant time, Interval interval, ZoneId zone) {
        String pattern = switch (interval) {
            case H1, H4 -> "d MMM HH:mm";
            case D1 -> "d MMM yyyy";
            case W1 -> "d MMM yyyy";
        };
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone).format(time);
    }

    /** A shorter form for axis ticks. */
    public static String axisTime(Instant time, Interval interval, ZoneId zone) {
        String pattern = switch (interval) {
            case H1, H4 -> "d MMM HH:mm";
            case D1 -> "d MMM ''yy";
            case W1 -> "MMM yyyy";
        };
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone).format(time);
    }

    public static String clock(Instant time, ZoneId zone) {
        return DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(zone).format(time);
    }
}
