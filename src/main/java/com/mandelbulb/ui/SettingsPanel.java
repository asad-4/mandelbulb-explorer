package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/** Base class of the scrollable tabs in the control panel. */
abstract class SettingsPanel extends ScrollPane {

    protected final ApplicationState state;
    protected final BoundControls controls;
    protected final VBox content = new VBox(2);

    SettingsPanel(ApplicationState state, Runnable onChange) {
        this.state = state;
        this.controls = new BoundControls(onChange);
        content.getStyleClass().add("panel-content");
        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("settings-panel");
    }

    /** Re-reads all values from the model. */
    public void refresh() {
        controls.refresh();
    }

    /** Called a few times per second for live read-outs. */
    public void pulse() {
    }
}
