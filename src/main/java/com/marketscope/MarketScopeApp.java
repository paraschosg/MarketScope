package com.marketscope;

import com.marketscope.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** The desktop app. */
public final class MarketScopeApp extends Application {

    static final String STYLESHEET = "/com/marketscope/ui/marketscope.css";

    private MainView view;

    @Override
    public void start(Stage stage) {
        view = new MainView(getHostServices());
        Scene scene = new Scene((javafx.scene.Parent) view.node(), 1440, 900);
        scene.getStylesheets().add(MarketScopeApp.class.getResource(STYLESHEET).toExternalForm());
        stage.setTitle("MarketScope");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
        view.start();
        String exitAfter = System.getProperty("marketscope.exitAfter");
        if (exitAfter != null) {
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                    javafx.util.Duration.seconds(Double.parseDouble(exitAfter)));
            pause.setOnFinished(e -> stage.close());
            pause.play();
        }
    }

    @Override
    public void stop() {
        if (view != null) {
            view.dispose();
        }
    }
}
