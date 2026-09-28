package com.mandelbulb.ui;

import com.mandelbulb.math.Color3;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Creates controls bound to settings fields through getter/setter lambdas.
 *
 * <p>Each control registers a refresher that re-reads its value from the model, so after a preset
 * is loaded (or a value is clamped by validation) {@link #refresh()} brings every control back in
 * sync. Refreshing never fires change callbacks, which prevents feedback loops.
 */
public final class BoundControls {

    private final List<Runnable> refreshers = new ArrayList<>();
    private final Runnable onChange;
    private boolean refreshing;

    public BoundControls(Runnable onChange) {
        this.onChange = onChange;
    }

    private void fire() {
        if (!refreshing) onChange.run();
    }

    public void refresh() {
        refreshing = true;
        try {
            for (Runnable r : refreshers) r.run();
        } finally {
            refreshing = false;
        }
    }

    public void addRefresher(Runnable r) {
        refreshers.add(r);
    }

    public ParameterSlider slider(String label, double min, double max, int decimals, String help,
                                  DoubleSupplier get, DoubleConsumer set) {
        return slider(label, min, max, false, decimals, help, get, set);
    }

    public ParameterSlider logSlider(String label, double min, double max, int decimals, String help,
                                     DoubleSupplier get, DoubleConsumer set) {
        return slider(label, min, max, true, decimals, help, get, set);
    }

    private ParameterSlider slider(String label, double min, double max, boolean log, int decimals, String help,
                                   DoubleSupplier get, DoubleConsumer set) {
        ParameterSlider s = new ParameterSlider(label, min, max, log, false, decimals, help);
        s.setValue(get.getAsDouble());
        s.setOnChange(v -> {
            if (refreshing) return;
            set.accept(v);
            fire();
        });
        refreshers.add(() -> s.setValue(get.getAsDouble()));
        return s;
    }

    public ParameterSlider intSlider(String label, int min, int max, boolean log, String help,
                                     IntSupplier get, IntConsumer set) {
        ParameterSlider s = new ParameterSlider(label, min, max, log, true, 0, help);
        s.setValue(get.getAsInt());
        s.setOnChange(v -> {
            if (refreshing) return;
            set.accept((int) Math.round(v));
            fire();
        });
        refreshers.add(() -> s.setValue(get.getAsInt()));
        return s;
    }

    public CheckBox check(String label, String help, BooleanSupplier get, Consumer<Boolean> set) {
        CheckBox c = new CheckBox(label);
        c.setSelected(get.getAsBoolean());
        if (help != null) c.setTooltip(new Tooltip(help));
        c.selectedProperty().addListener((obs, o, n) -> {
            if (refreshing) return;
            set.accept(n);
            fire();
        });
        refreshers.add(() -> c.setSelected(get.getAsBoolean()));
        return c;
    }

    @SafeVarargs
    public final <T> ComboBox<T> combo(Supplier<T> get, Consumer<T> set, T... values) {
        return combo(Arrays.asList(values), get, set);
    }

    public <T> ComboBox<T> combo(List<T> values, Supplier<T> get, Consumer<T> set) {
        ComboBox<T> c = new ComboBox<>();
        c.getItems().setAll(values);
        c.setValue(get.get());
        c.setMaxWidth(Double.MAX_VALUE);
        c.valueProperty().addListener((obs, o, n) -> {
            if (refreshing || n == null) return;
            set.accept(n);
            fire();
        });
        refreshers.add(() -> c.setValue(get.get()));
        return c;
    }

    public ColorPicker color(Supplier<Color3> get, Consumer<Color3> set) {
        ColorPicker p = new ColorPicker(toFx(get.get()));
        p.getStyleClass().add("button");
        p.setMaxWidth(Double.MAX_VALUE);
        p.valueProperty().addListener((obs, o, n) -> {
            if (refreshing || n == null) return;
            set.accept(fromFx(n));
            fire();
        });
        refreshers.add(() -> p.setValue(toFx(get.get())));
        return p;
    }

    public static Color toFx(Color3 c) {
        return Color.color(clamp(c.r()), clamp(c.g()), clamp(c.b()));
    }

    public static Color3 fromFx(Color c) {
        return new Color3(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** "Label   [control]" row. */
    public static HBox row(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("parameter-label");
        l.setMinWidth(104);
        l.setPrefWidth(104);
        HBox box = new HBox(6, l, control);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return box;
    }

    /** Titled group of controls. */
    public static VBox section(String title, Node... children) {
        Label header = new Label(title);
        header.getStyleClass().add("section-header");
        VBox box = new VBox(6);
        box.getStyleClass().add("section");
        box.getChildren().add(header);
        box.getChildren().addAll(children);
        box.setPadding(new Insets(8, 10, 10, 10));
        return box;
    }

    public static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint");
        l.setWrapText(true);
        return l;
    }
}
