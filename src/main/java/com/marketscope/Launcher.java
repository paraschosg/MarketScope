package com.marketscope;

import javafx.application.Application;

import java.util.Arrays;

/**
 * Entry point. A plain class rather than the {@code Application} itself, so the shaded jar starts
 * without the JavaFX launcher check.
 *
 * <ul>
 *   <li>no arguments: the desktop app</li>
 *   <li>{@code --report SYMBOL [1H|4H|1D|1W]}: the analysis as text, e.g. {@code --report BTC 1D}</li>
 *   <li>{@code --snapshot FILE.png [index] [interval]}: renders the window to an image</li>
 * </ul>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) throws Exception {
        // The portable build points these at a "data" folder next to the program; make sure they exist.
        for (String property : new String[] {"java.io.tmpdir", "marketscope.home", "javafx.cachedir"}) {
            String folder = System.getProperty(property);
            if (folder != null) {
                try {
                    java.nio.file.Files.createDirectories(java.nio.file.Path.of(folder));
                } catch (java.io.IOException | RuntimeException ignored) {
                    // the parts that need it report their own problem
                }
            }
        }
        if (args.length > 0 && args[0].equals("--report")) {
            Report.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && args[0].equals("--snapshot")) {
            Snapshot.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        Application.launch(MarketScopeApp.class, args);
    }
}
