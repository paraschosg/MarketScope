package com.marketscope;

import com.marketscope.data.WatchlistStore;
import com.marketscope.model.Interval;
import com.marketscope.ui.MainView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Renders the window with live data to a PNG, without showing it: {@code --snapshot out.png 0 1D}
 * (watchlist index and interval). For checking the layout and for the README.
 */
final class Snapshot {

    private Snapshot() {
    }

    static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "marketscope.png");
        int index = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        Interval interval = args.length > 2 ? switch (args[2].toUpperCase(java.util.Locale.ROOT)) {
            case "1H" -> Interval.H1;
            case "4H" -> Interval.H4;
            case "1W" -> Interval.W1;
            default -> Interval.D1;
        } : Interval.D1;
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> started.complete(null));
        started.get(20, TimeUnit.SECONDS);

        CompletableFuture<MainView> built = new CompletableFuture<>();
        CompletableFuture<Scene> scene = new CompletableFuture<>();
        Platform.runLater(() -> {
            MainView view = new MainView(null, new WatchlistStore(Files.isDirectory(WatchlistStore.home())
                    ? WatchlistStore.home().resolve("watchlist.json") : Path.of("none.json")));
            Scene s = new Scene((javafx.scene.Parent) view.node(), 1440, 900);
            s.getStylesheets().add(Snapshot.class.getResource(MarketScopeApp.STYLESHEET).toExternalForm());
            view.choose(index, interval);
            view.start();
            built.complete(view);
            scene.complete(s);
        });
        MainView view = built.get(20, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            CompletableFuture<Boolean> ready = new CompletableFuture<>();
            Platform.runLater(() -> ready.complete(view.ready()));
            if (ready.get(10, TimeUnit.SECONDS)) {
                break;
            }
            Thread.sleep(250);
        }
        Thread.sleep(1500); // let the watchlist prices arrive
        if (Boolean.getBoolean("marketscope.debugCells")) {
            CompletableFuture<Void> dumped = new CompletableFuture<>();
            Platform.runLater(() -> {
                scene.join().getRoot().applyCss();
                scene.join().getRoot().layout();
                for (javafx.scene.Node n : scene.join().getRoot().lookupAll(".list-cell")) {
                    javafx.scene.control.ListCell<?> cell = (javafx.scene.control.ListCell<?>) n;
                    StringBuilder labels = new StringBuilder();
                    for (javafx.scene.Node l : cell.lookupAll(".label")) {
                        javafx.scene.control.Label label = (javafx.scene.control.Label) l;
                        labels.append(" [").append(label.getText()).append(" fill=").append(label.getTextFill())
                                .append(" w=").append(label.getWidth()).append(" h=").append(label.getHeight()).append(" font=").append(label.getFont().getSize()).append(" op=").append(label.getOpacity())
                                .append("]");
                    }
                    System.out.println("cell " + cell.getIndex() + " item=" + cell.getItem() + " selected="
                            + cell.isSelected() + " cellH=" + cell.getHeight() + " gw=" + (cell.getGraphic() == null ? -1 : ((javafx.scene.layout.Region) cell.getGraphic()).getWidth()) + labels);
                }
                dumped.complete(null);
            });
            dumped.get(10, TimeUnit.SECONDS);
        }
        CompletableFuture<WritableImage> image = new CompletableFuture<>();
        Platform.runLater(() -> image.complete(scene.join().snapshot(null)));
        WritableImage fx = image.get(20, TimeUnit.SECONDS);
        BufferedImage png = new BufferedImage((int) fx.getWidth(), (int) fx.getHeight(), BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = fx.getPixelReader();
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) {
                png.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        ImageIO.write(png, "png", out.toFile());
        System.out.println("Wrote " + out.toAbsolutePath());
        Platform.runLater(view::dispose);
        Platform.exit();
    }
}
