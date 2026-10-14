package com.marketscope.analysis;

import java.util.List;

/**
 * One occurrence of a pattern.
 *
 * @param start   the first candle of the shape
 * @param confirm the candle at whose close the pattern became known; nothing after it was used
 *                to find it, so its outcome can be measured honestly from here
 * @param level   the line that confirmed it (neckline, breakout level), or {@code NaN}
 * @param points  the shape's turning points, for drawing
 */
public record Pattern(PatternKind kind, int start, int confirm, double level, List<Point> points) {

    /** A turning point: a candle index and a price. */
    public record Point(int index, double price) {
    }

    public Pattern {
        points = List.copyOf(points);
    }
}
