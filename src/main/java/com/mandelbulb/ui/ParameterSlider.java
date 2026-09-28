package com.mandelbulb.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * Labelled slider with an exact-entry text field.
 *
 * <p>Supports logarithmic scales (for parameters spanning decades such as epsilons and step
 * counts) and integer values. Typed values are validated and clamped; unparsable input is
 * rejected with a visual hint and reverted instead of ever reaching the renderer.
 */
public final class ParameterSlider extends HBox {

    private final Slider slider;
    private final TextField field;
    private final double min;
    private final double max;
    private final boolean log;
    private final boolean integer;
    private final int decimals;
    private DoubleConsumer onChange = v -> { };
    private boolean updating;
    private double value;

    public ParameterSlider(String name, double min, double max, boolean log, boolean integer, int decimals, String help) {
        this.min = min;
        this.max = max;
        this.log = log;
        this.integer = integer;
        this.decimals = decimals;
        getStyleClass().add("parameter-slider");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);

        Label label = new Label(name);
        label.getStyleClass().add("parameter-label");
        label.setMinWidth(104);
        label.setPrefWidth(104);

        slider = new Slider(toSlider(min), toSlider(max), toSlider(min));
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.setMinWidth(60);

        field = new TextField();
        field.setPrefColumnCount(6);
        field.setPrefWidth(76);
        field.setMinWidth(76);
        field.getStyleClass().add("parameter-field");

        if (help != null) {
            Tooltip tip = new Tooltip(help + "\nRange: " + format(min) + " – " + format(max));
            tip.setShowDelay(Duration.millis(400));
            tip.setWrapText(true);
            tip.setMaxWidth(360);
            label.setTooltip(tip);
            slider.setTooltip(tip);
            field.setTooltip(tip);
        }

        slider.valueProperty().addListener((obs, o, n) -> {
            if (updating) return;
            double v = normalize(fromSlider(n.doubleValue()));
            value = v;
            updating = true;
            field.setText(format(v));
            updating = false;
            field.getStyleClass().remove("invalid");
            onChange.accept(v);
        });
        field.setOnAction(e -> commitText());
        field.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) commitText();
        });
        getChildren().addAll(label, slider, field);
    }

    public void setOnChange(DoubleConsumer onChange) {
        this.onChange = onChange;
    }

    private void commitText() {
        if (updating) return;
        String text = field.getText().trim().replace(',', '.');
        double v;
        try {
            v = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            v = Double.NaN;
        }
        if (!Double.isFinite(v)) {
            // Reject and restore the last good value.
            if (!field.getStyleClass().contains("invalid")) field.getStyleClass().add("invalid");
            setValue(value);
            return;
        }
        field.getStyleClass().remove("invalid");
        v = normalize(v);
        if (v == value && text.equals(format(v))) return;
        setValue(v);
        onChange.accept(v);
    }

    /** Clamps into range and rounds integer parameters. */
    private double normalize(double v) {
        v = Math.max(min, Math.min(max, v));
        return integer ? Math.rint(v) : v;
    }

    /** Updates the displayed value without firing the change callback. */
    public void setValue(double v) {
        if (!Double.isFinite(v)) return;
        value = normalize(v);
        updating = true;
        slider.setValue(toSlider(value));
        if (!field.isFocused() || !field.getText().equals(format(value))) {
            field.setText(format(value));
        }
        updating = false;
    }

    public double getValue() {
        return value;
    }

    private double toSlider(double v) {
        return log ? Math.log10(Math.max(v, 1e-300)) : v;
    }

    private double fromSlider(double s) {
        return log ? Math.pow(10, s) : s;
    }

    private String format(double v) {
        if (integer) return Long.toString(Math.round(v));
        double a = Math.abs(v);
        if (a != 0 && (a < 1e-3 || a >= 1e6)) return String.format(Locale.ROOT, "%.2e", v);
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
