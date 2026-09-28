package com.mandelbulb.app;

import com.mandelbulb.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

/** JavaFX application: creates the model and the main window. */
public final class MandelbulbApplication extends Application {

    private ApplicationState state;

    public static void launchApp(String[] args) {
        Application.launch(MandelbulbApplication.class, args);
    }

    @Override
    public void start(Stage stage) {
        state = new ApplicationState();
        MainWindow window = new MainWindow(stage, state);
        window.show();
    }

    @Override
    public void stop() {
        if (state != null) {
            state.shutdown();
        }
    }
}
