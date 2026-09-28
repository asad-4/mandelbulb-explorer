package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.camera.Camera;
import com.mandelbulb.camera.CameraController;
import com.mandelbulb.fractal.FractalParameters;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.rendering.FrameBuffer;
import com.mandelbulb.rendering.RenderProgress;
import com.mandelbulb.rendering.RenderStats;
import com.mandelbulb.scene.Light;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The 3D view: displays the engine's latest frame, translates mouse and keyboard input into
 * camera motion and draws the information overlay and light indicator.
 *
 * <p>Rendered pixels are copied into a {@link WritableImage} only when the frame buffer's version
 * changed, and never more than ~40 times per second while a pass is in progress.
 */
final class ViewportPane extends StackPane {

    private static final Font OVERLAY_FONT = Font.font("Menlo", 12);
    private static final Font OVERLAY_TITLE = Font.font("System", FontWeight.BOLD, 14);

    private final ApplicationState state;
    private final Runnable onSettingsChanged;
    private final Runnable onTogglePause;
    private final ImageView imageView = new ImageView();
    private final Canvas overlay = new Canvas();
    private WritableImage image;
    private FrameBuffer shownFrame;
    private int shownVersion = -1;
    private long lastBlitNanos;
    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();
    /** Bottom edge of the info box drawn this frame (0 when hidden), so icons can avoid it. */
    private double infoBoxBottom;
    private double infoBoxRight;

    private final Set<KeyCode> keys = EnumSet.noneOf(KeyCode.class);
    private double lastX;
    private double lastY;
    private double pressX;
    private double pressY;

    ViewportPane(ApplicationState state, Runnable onSettingsChanged, Runnable onTogglePause) {
        this.state = state;
        this.onSettingsChanged = onSettingsChanged;
        this.onTogglePause = onTogglePause;
        getStyleClass().add("viewport");
        setMinSize(0, 0);
        setPrefSize(960, 640);
        setFocusTraversable(true);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.fitWidthProperty().bind(widthProperty());
        imageView.fitHeightProperty().bind(heightProperty());
        imageView.setManaged(true);
        overlay.widthProperty().bind(widthProperty());
        overlay.heightProperty().bind(heightProperty());
        overlay.setMouseTransparent(true);
        overlay.setManaged(false);
        getChildren().addAll(imageView, overlay);

        widthProperty().addListener((o, a, b) -> sizeChanged());
        heightProperty().addListener((o, a, b) -> sizeChanged());

        addEventHandler(MouseEvent.MOUSE_PRESSED, this::mousePressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::mouseDragged);
        addEventHandler(MouseEvent.MOUSE_CLICKED, this::mouseClicked);
        addEventHandler(ScrollEvent.SCROLL, this::scrolled);
        addEventHandler(KeyEvent.KEY_PRESSED, this::keyPressed);
        addEventHandler(KeyEvent.KEY_RELEASED, e -> keys.remove(e.getCode()));
        focusedProperty().addListener((o, a, focused) -> {
            if (!focused) keys.clear();
        });
    }

    private void sizeChanged() {
        double scale = getScene() != null && getScene().getWindow() != null ? getScene().getWindow().getOutputScaleX() : 1.0;
        state.setViewportSize((int) Math.round(getWidth()), (int) Math.round(getHeight()), scale);
    }

    // ---------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------

    private void mousePressed(MouseEvent e) {
        requestFocus();
        lastX = pressX = e.getX();
        lastY = pressY = e.getY();
    }

    private void mouseDragged(MouseEvent e) {
        double dx = e.getX() - lastX, dy = e.getY() - lastY;
        lastX = e.getX();
        lastY = e.getY();
        CameraController cam = state.camera();
        if (e.getButton() == MouseButton.PRIMARY) {
            if (e.isShiftDown()) {
                LightingPanel.moveKeyLight(state.scene().lighting.key, -dx * 0.4, -dy * 0.3);
                onSettingsChanged.run();
            } else if (e.isAltDown() || e.isControlDown() || e.isMetaDown()) {
                cam.freeLook(dx, dy);
            } else {
                cam.orbit(dx, dy);
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            if (e.isControlDown()) {
                cam.roll(dx * 0.005);
            } else {
                cam.pan(dx, dy, (int) Math.max(1, getHeight()));
            }
        } else if (e.getButton() == MouseButton.MIDDLE) {
            cam.dolly(-dy * 0.06);
            cam.pan(dx, 0, (int) Math.max(1, getHeight()));
        }
    }

    private void mouseClicked(MouseEvent e) {
        boolean stationary = Math.hypot(e.getX() - pressX, e.getY() - pressY) < 3;
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && stationary) {
            double[] rp = toRenderPixel(e.getX(), e.getY());
            if (rp != null) {
                Vector3 hit = state.pick(rp[0], rp[1]);
                if (hit != null) {
                    state.camera().focus(hit);
                }
            }
        }
    }

    private void scrolled(ScrollEvent e) {
        double delta = e.getDeltaY() != 0 ? e.getDeltaY() : e.getDeltaX();
        double notches = delta / 40.0;
        if (notches == 0) return;
        CameraController cam = state.camera();
        if (e.isControlDown() || e.isMetaDown()) {
            cam.setFov(cam.goal().fovDegrees() * Math.pow(0.95, notches));
            onSettingsChanged.run();
        } else if (e.isShiftDown()) {
            cam.dolly(notches);
        } else {
            cam.zoom(notches);
        }
        e.consume();
    }

    private void keyPressed(KeyEvent e) {
        KeyCode k = e.getCode();
        CameraController cam = state.camera();
        int slot = digitSlot(k);
        if (slot > 0) {
            if (e.isShortcutDown()) {
                cam.saveBookmark(slot);
                state.presets().saveBookmarks(cam);
            } else {
                cam.restoreBookmark(slot);
            }
            e.consume();
            return;
        }
        switch (k) {
            case HOME -> cam.reset();
            case O -> state.setOverlayVisible(!state.isOverlayVisible());
            case P -> onTogglePause.run();
            case PLUS, EQUALS, ADD -> cam.zoom(1);
            case MINUS, SUBTRACT -> cam.zoom(-1);
            default -> keys.add(k);
        }
        e.consume();
    }

    private static int digitSlot(KeyCode k) {
        int d = k.ordinal() - KeyCode.DIGIT0.ordinal();
        if (d >= 1 && d <= 9) return d;
        int n = k.ordinal() - KeyCode.NUMPAD0.ordinal();
        return n >= 1 && n <= 9 ? n : -1;
    }

    /** Continuous keyboard movement, called every frame. */
    private void applyKeys(double dt) {
        if (keys.isEmpty()) return;
        CameraController cam = state.camera();
        double boost = keys.contains(KeyCode.SHIFT) ? 4 : 1;
        double fwd = axis(KeyCode.W, KeyCode.S), right = axis(KeyCode.D, KeyCode.A), up = axis(KeyCode.R, KeyCode.F);
        if (fwd != 0 || right != 0 || up != 0) {
            cam.move(right, fwd, up, dt, boost);
        }
        double roll = axis(KeyCode.E, KeyCode.Q);
        if (roll != 0) cam.roll(roll * dt * 1.2 * boost);
        double ox = axis(KeyCode.RIGHT, KeyCode.LEFT), oy = axis(KeyCode.DOWN, KeyCode.UP);
        if (ox != 0 || oy != 0) cam.orbit(ox * dt * 240 * boost, oy * dt * 240 * boost);
    }

    private double axis(KeyCode positive, KeyCode negative) {
        return (keys.contains(positive) ? 1 : 0) - (keys.contains(negative) ? 1 : 0);
    }

    // ---------------------------------------------------------------------------------------
    // Display
    // ---------------------------------------------------------------------------------------

    /** Maps a viewport position to render-image pixel coordinates (null outside the image). */
    private double[] toRenderPixel(double x, double y) {
        int[] size = state.renderSize();
        double[] r = imageRect(size[0], size[1]);
        double rx = (x - r[0]) / r[2] * size[0], ry = (y - r[1]) / r[3] * size[1];
        if (rx < 0 || ry < 0 || rx >= size[0] || ry >= size[1]) return null;
        return new double[] {rx, ry};
    }

    /** {x, y, w, h} of the letterboxed image inside the viewport. */
    private double[] imageRect(int iw, int ih) {
        double w = getWidth(), h = getHeight();
        double s = Math.min(w / iw, h / ih);
        return new double[] {(w - iw * s) / 2, (h - ih * s) / 2, iw * s, ih * s};
    }

    void update(double dt) {
        applyKeys(dt);
        blit();
        drawOverlay();
    }

    private void blit() {
        FrameBuffer f = state.engine().displayFrame();
        if (f == null) return;
        int version = f.version();
        if (f == shownFrame && version == shownVersion) return;
        long now = System.nanoTime();
        if (f == shownFrame && !f.isComplete() && now - lastBlitNanos < 25_000_000L) return;
        if (image == null || (int) image.getWidth() != f.width() || (int) image.getHeight() != f.height()) {
            image = new WritableImage(f.width(), f.height());
            imageView.setImage(image);
        }
        // Pixels are always opaque, so ARGB and premultiplied ARGB are identical (fast path).
        image.getPixelWriter().setPixels(0, 0, f.width(), f.height(), PixelFormat.getIntArgbPreInstance(), f.pixels(), 0, f.width());
        if (f != shownFrame) {
            frameTimes.addLast(now);
        }
        shownFrame = f;
        shownVersion = version;
        lastBlitNanos = now;
    }

    private double fps() {
        long now = System.nanoTime();
        while (!frameTimes.isEmpty() && now - frameTimes.peekFirst() > 1_000_000_000L) frameTimes.removeFirst();
        return frameTimes.size();
    }

    private void drawOverlay() {
        GraphicsContext g = overlay.getGraphicsContext2D();
        double w = overlay.getWidth(), h = overlay.getHeight();
        g.clearRect(0, 0, w, h);
        infoBoxBottom = 0;
        infoBoxRight = 0;
        double currentFps = fps();
        if (state.isOverlayVisible()) {
            drawInfo(g, currentFps);
        }
        if (state.isLightIndicatorVisible()) {
            drawLights(g);
        }
        if (state.camera().isInsideFractal()) {
            banner(g, w, h, "Camera is inside the fractal — zoom out or press Home", Color.rgb(255, 90, 90));
        } else if (state.isPaused()) {
            banner(g, w, h, "Paused — press P or Render to continue", Color.rgb(255, 210, 90));
        }
    }

    private void banner(GraphicsContext g, double w, double h, String text, Color color) {
        g.setFont(OVERLAY_TITLE);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(Color.rgb(0, 0, 0, 0.6));
        g.fillRoundRect(w / 2 - 230, h - 52, 460, 30, 10, 10);
        g.setFill(color);
        g.fillText(text, w / 2, h - 37);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.BASELINE);
    }

    private void drawInfo(GraphicsContext g, double currentFps) {
        FractalParameters f = state.scene().fractal;
        RenderProgress p = state.engine().progress();
        RenderStats stats = state.engine().lastStats();
        int[] size = state.renderSize();
        int ss = state.scene().render.supersampling;
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "Power:       %.2f", f.power));
        lines.add(String.format(Locale.ROOT, "Iterations:  %d   Bailout: %.1f", f.maxIterations, f.bailout));
        lines.add(String.format(Locale.ROOT, "Resolution:  %d × %d%s", size[0], size[1], ss > 1 ? "  (" + ss + "×" + ss + " SS)" : ""));
        lines.add(String.format(Locale.ROOT, "Ray steps:   %d max%s", state.scene().render.maxSteps,
                stats != null ? String.format(Locale.ROOT, ", %.1f avg", stats.averageSteps()) : ""));
        lines.add(currentFps == 0 && !p.running() ? "FPS:         idle"
                : String.format(Locale.ROOT, "FPS:         %.0f", currentFps));
        if (p.running()) {
            lines.add(String.format(Locale.ROOT, "Render:      pass %d/%d  %s  %.0f%%", p.pass(), p.passCount(),
                    p.block() > 1 ? "(1/" + p.block() + " res)" : (p.supersampling() > 1 ? "(" + p.supersampling() + "× SS)" : "(full)"),
                    p.fraction() * 100));
            lines.add(String.format(Locale.ROOT, "Render time: %.2f s …", p.elapsedSeconds()));
        } else if (stats != null) {
            lines.add("Render:      complete");
            lines.add(String.format(Locale.ROOT, "Render time: %.2f s", stats.seconds()));
        }
        Camera c = state.camera().current();
        lines.add(String.format(Locale.ROOT, "Camera dist: %.4g   FOV %.0f°", c.distance(), c.fovDegrees()));
        if (state.animation().isActive()) {
            lines.add(String.format(Locale.ROOT, "Animation:   %s  t = %.1f s", state.animation().state(), state.animation().time()));
        }

        double boxW = 280, lineH = 16, boxH = 30 + lines.size() * lineH;
        g.setFill(Color.rgb(8, 10, 16, 0.62));
        g.fillRoundRect(10, 10, boxW, boxH, 10, 10);
        infoBoxBottom = 10 + boxH;
        infoBoxRight = 10 + boxW;
        g.setFill(Color.rgb(140, 190, 255));
        g.setFont(OVERLAY_TITLE);
        g.fillText(f.juliaMode ? "Mandelbulb (Julia)" : "Mandelbulb", 20, 30);
        g.setFont(OVERLAY_FONT);
        g.setFill(Color.rgb(225, 230, 240));
        for (int i = 0; i < lines.size(); i++) {
            g.fillText(lines.get(i), 20, 50 + i * lineH);
        }
    }

    /** Draws sun/bulb icons at the projected light positions (arrows at the edge if off-screen). */
    private void drawLights(GraphicsContext g) {
        int[] size = state.renderSize();
        double[] rect = imageRect(size[0], size[1]);
        Camera cam = state.camera().current();
        Light key = state.scene().lighting.key;
        if (key.enabled) {
            Vector3 far = cam.position().addScaled(key.direction(), 1000);
            drawLightIcon(g, projectOrEdge(cam, far, size), rect, size, BoundControls.toFx(key.color), "Key", true);
        }
        Light point = state.scene().lighting.point;
        if (point.enabled) {
            drawLightIcon(g, projectOrEdge(cam, point.position(), size), rect, size, BoundControls.toFx(point.color), "Point", false);
        }
    }

    /**
     * Projects a point; for points behind the camera returns a position far outside the image in
     * the direction of the point, so the icon is pinned to the matching edge.
     */
    private static double[] projectOrEdge(Camera cam, Vector3 p, int[] size) {
        double[] proj = cam.project(p, size[0], size[1]);
        if (proj != null) return proj;
        Vector3 rel = p.sub(cam.position());
        double sx = rel.dot(cam.right()), sy = rel.dot(cam.up());
        double len = Math.hypot(sx, sy);
        if (len < 1e-12) {
            sy = -1;
            len = 1;
        }
        double k = 10.0 * Math.max(size[0], size[1]) / len;
        return new double[] {size[0] * 0.5 + sx * k, size[1] * 0.5 - sy * k, -1};
    }

    private void drawLightIcon(GraphicsContext g, double[] proj, double[] rect, int[] size, Color color, String label, boolean sun) {
        if (proj == null) return;
        double x = rect[0] + proj[0] / size[0] * rect[2];
        double y = rect[1] + proj[1] / size[1] * rect[3];
        double margin = 18;
        boolean clamped = x < rect[0] + margin || x > rect[0] + rect[2] - margin || y < rect[1] + margin || y > rect[1] + rect[3] - margin;
        x = Math.max(rect[0] + margin, Math.min(rect[0] + rect[2] - margin, x));
        y = Math.max(rect[1] + margin, Math.min(rect[1] + rect[3] - margin, y));
        if (x < infoBoxRight + 8 && y < infoBoxBottom + 8) {
            y = infoBoxBottom + margin + 4;
        }
        g.setGlobalAlpha(clamped ? 0.5 : 0.9);
        g.setStroke(color);
        g.setFill(color.deriveColor(0, 1, 1, 0.35));
        g.setLineWidth(1.5);
        g.fillOval(x - 7, y - 7, 14, 14);
        g.strokeOval(x - 7, y - 7, 14, 14);
        if (sun) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                g.strokeLine(x + Math.cos(a) * 10, y + Math.sin(a) * 10, x + Math.cos(a) * 14, y + Math.sin(a) * 14);
            }
        }
        g.setFill(Color.rgb(230, 235, 245));
        g.setFont(OVERLAY_FONT);
        String text = proj[2] < 0 ? label + " (behind)" : label;
        double tx = x > rect[0] + rect[2] - 120 ? x - 16 - text.length() * 7.2 : x + 16;
        g.fillText(text, tx, y + 4);
        g.setGlobalAlpha(1);
    }
}
