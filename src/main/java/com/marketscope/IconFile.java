package com.marketscope;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Windows icon of the portable build ({@code tools/portable.ps1}): three candles in
 * the app's earthy up and down colours on a light rounded tile, 16 to 256 px in one {@code .ico}.
 *
 * <pre>java -cp marketscope-0.1.0-all.jar com.marketscope.IconFile target\portable\marketscope.ico</pre>
 */
public final class IconFile {

    static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private static final Color TILE = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BORDER = new Color(0xE2, 0xDE, 0xD6);
    private static final Color UP = new Color(0x2F, 0x7A, 0x55);
    private static final Color DOWN = new Color(0xB5, 0x47, 0x3A);
    private static final Color ACCENT = new Color(0xA0, 0x4A, 0x2C);

    private IconFile() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: IconFile <output.ico>");
            System.exit(64);
        }
        List<byte[]> pngs = new ArrayList<>();
        for (int size : SIZES) {
            pngs.add(png(size));
        }
        Path out = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(out.getParent());
        Files.write(out, ico(SIZES, pngs));
        System.out.println("Wrote " + out + " (" + SIZES.length + " sizes)");
    }

    static byte[] png(int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double inset = size <= 20 ? 0 : size * 0.04;
        double tile = size - 2 * inset;
        double radius = tile * 0.44;
        RoundRectangle2D shape = new RoundRectangle2D.Double(inset, inset, tile, tile, radius, radius);
        g.setColor(TILE);
        g.fill(shape);
        if (size >= 32) {
            g.setColor(BORDER);
            g.setStroke(new BasicStroke((float) Math.max(1, size / 64.0)));
            g.draw(shape);
        }
        // Three candles: down, up, up, rising left to right, with a trend line over them.
        double unit = tile / 10;
        double base = inset;
        double[][] candles = { // x, wickTop, bodyTop, bodyBottom, wickBottom (in tenths of the tile)
                {2.6, 3.6, 4.4, 6.6, 7.6},
                {5.0, 2.6, 3.4, 5.8, 6.8},
                {7.4, 1.6, 2.2, 4.6, 5.6}};
        Color[] colours = {DOWN, UP, UP};
        double body = Math.max(2, unit * 1.5);
        for (int i = 0; i < candles.length; i++) {
            double[] c = candles[i];
            double x = base + c[0] * unit;
            g.setColor(colours[i]);
            g.setStroke(new BasicStroke((float) Math.max(1, unit * 0.32), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(x, base + c[1] * unit, x, base + c[4] * unit));
            g.fill(new RoundRectangle2D.Double(x - body / 2, base + c[2] * unit, body, (c[3] - c[2]) * unit,
                    unit * 0.3, unit * 0.3));
        }
        if (size >= 24) {
            Path2D trend = new Path2D.Double();
            trend.moveTo(base + 1.4 * unit, base + 8.4 * unit);
            trend.lineTo(base + 8.8 * unit, base + 6.6 * unit);
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke((float) Math.max(1, unit * 0.45), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(trend);
        }
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    /** An ICO file holding the PNGs: ICONDIR, one ICONDIRENTRY per image, then the images. */
    static byte[] ico(int[] sizes, List<byte[]> pngs) {
        int count = pngs.size();
        int offset = 6 + 16 * count;
        int total = offset + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0).putShort((short) 1).putShort((short) count);
        for (int i = 0; i < count; i++) {
            int size = sizes[i];
            out.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            out.put((byte) 0).put((byte) 0);
            out.putShort((short) 1).putShort((short) 32);
            out.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) {
            out.put(png);
        }
        return out.array();
    }
}
