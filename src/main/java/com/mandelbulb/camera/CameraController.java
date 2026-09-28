package com.mandelbulb.camera;

import com.mandelbulb.math.MathUtils;
import com.mandelbulb.math.Vector3;

import java.util.function.ToDoubleFunction;

/**
 * Turns user input into camera motion.
 *
 * <p>Input never moves the visible camera directly: it modifies a <em>goal</em> camera, and
 * {@link #update(double)} (called once per UI frame) moves the <em>current</em> camera towards the
 * goal with exponential smoothing. This removes sudden jumps from coarse mouse/keyboard events.
 *
 * <p>All translational speeds are scaled by the distance estimate at the eye, so the camera slows
 * down automatically as it approaches the surface. That is what makes deep zooms usable: the
 * fractal has detail at every scale, so a fixed speed is either too slow far away or overshoots
 * into the surface up close. Zooming and flying are also clamped so a single step never covers
 * more than a fraction of the distance to the surface, which prevents entering the fractal.
 */
public final class CameraController {

    private static final double ORBIT_RADIANS_PER_PIXEL = 0.0065;
    private static final double LOOK_RADIANS_PER_PIXEL = 0.0030;
    private static final double ZOOM_FACTOR_PER_NOTCH = 0.87;
    /**
     * Closest the eye may approach the surface. Approaching by halving the remaining distance
     * would otherwise converge to within rounding error (~1e-16) and cross the surface.
     */
    static final double MIN_SURFACE_DISTANCE = 1e-10;

    private Camera home = Camera.defaultCamera();
    private Camera goal = home;
    private Camera current = home;
    private boolean smoothing = true;
    private double smoothingSpeed = 12.0;
    private ToDoubleFunction<Vector3> surfaceDistance = p -> p.length() - 1.0;
    private final Camera[] bookmarks = new Camera[10];

    public Camera current() {
        return current;
    }

    public Camera goal() {
        return goal;
    }

    /** World-space distance estimate used to scale speeds (should include object rotation). */
    public void setSurfaceDistance(ToDoubleFunction<Vector3> surfaceDistance) {
        this.surfaceDistance = surfaceDistance;
    }

    public void setHome(Camera home) {
        this.home = home.sanitized();
    }

    public boolean isSmoothing() {
        return smoothing;
    }

    public void setSmoothing(boolean smoothing) {
        this.smoothing = smoothing;
    }

    public double smoothingSpeed() {
        return smoothingSpeed;
    }

    public void setSmoothingSpeed(double speed) {
        this.smoothingSpeed = MathUtils.sanitize(speed, 1, 60, 12);
    }

    /** Distance estimate at the eye of the goal camera (&lt;= 0 means inside the fractal). */
    public double surfaceDistanceAtEye() {
        double d = surfaceDistance.applyAsDouble(goal.position());
        return Double.isFinite(d) ? d : 0;
    }

    public boolean isInsideFractal() {
        return surfaceDistance.applyAsDouble(current.position()) <= 0;
    }

    /** Characteristic movement speed in world units per second. */
    public double movementSpeed() {
        return MathUtils.clamp(surfaceDistanceAtEye(), 1e-9, 2.0);
    }

    public void orbit(double dxPixels, double dyPixels) {
        setGoal(goal.orbit(-dxPixels * ORBIT_RADIANS_PER_PIXEL, -dyPixels * ORBIT_RADIANS_PER_PIXEL));
    }

    public void freeLook(double dxPixels, double dyPixels) {
        double s = LOOK_RADIANS_PER_PIXEL * Math.min(1.0, goal.fovDegrees() / 60.0);
        setGoal(goal.freeLook(-dxPixels * s, -dyPixels * s));
    }

    /** Pans so that the content under the cursor follows the mouse. */
    public void pan(double dxPixels, double dyPixels, int viewportHeight) {
        double u = goal.worldUnitsPerPixel(viewportHeight);
        setGoal(goal.moveLocal(-dxPixels * u, 0, dyPixels * u));
    }

    /**
     * Zooms towards the pivot. Positive notches zoom in. Zooming in is limited to half the
     * distance to the surface per notch so the eye can approach but never pass through it.
     */
    public void zoom(double notches) {
        double d = goal.distance();
        if (notches > 0) {
            double wanted = d - d * Math.pow(ZOOM_FACTOR_PER_NOTCH, notches);
            double de = surfaceDistanceAtEye();
            double allowed = de > 0 ? Math.max(0, 0.5 * (de - MIN_SURFACE_DISTANCE)) : wanted;
            setGoal(goal.withDistance(d - Math.min(wanted, allowed)));
        } else if (notches < 0) {
            setGoal(goal.withDistance(d * Math.pow(1.0 / ZOOM_FACTOR_PER_NOTCH, -notches)));
        }
    }

    /** Moves eye and pivot forward (positive) or backward along the view direction. */
    public void dolly(double notches) {
        double step = movementSpeed() * 0.25 * notches;
        if (step > 0) step = Math.min(step, Math.max(0, 0.5 * (surfaceDistanceAtEye() - MIN_SURFACE_DISTANCE)));
        setGoal(goal.moveLocal(0, step, 0));
    }

    public void roll(double radians) {
        setGoal(goal.roll(radians));
    }

    /**
     * Keyboard fly movement. Amounts are in [-1,1] per axis; {@code dt} in seconds. The step is
     * clamped to 80% of the surface distance so flying can never tunnel into the fractal.
     */
    public void move(double right, double forward, double up, double dt, double boost) {
        double len = Math.sqrt(right * right + forward * forward + up * up);
        if (len < 1e-12) return;
        double de = surfaceDistanceAtEye();
        double step = movementSpeed() * dt * boost;
        if (de > 0) step = Math.min(step, Math.max(0, 0.8 * (de - MIN_SURFACE_DISTANCE)));
        setGoal(goal.moveLocal(right / len * step, forward / len * step, up / len * step));
    }

    public void setFov(double fov) {
        setGoal(goal.withFov(fov));
    }

    public void setClipping(double near, double far) {
        setGoal(goal.withClipping(near, far));
    }

    /** Makes {@code worldPoint} the orbit pivot, keeping the eye in place. */
    public void focus(Vector3 worldPoint) {
        if (worldPoint != null && worldPoint.isFinite()) {
            setGoal(goal.refocus(worldPoint));
        }
    }

    public void reset() {
        setGoal(home);
    }

    /** Sets the camera; {@code immediate} bypasses smoothing (used by presets and animation). */
    public void setCamera(Camera camera, boolean immediate) {
        setGoal(camera);
        if (immediate) {
            current = goal;
        }
    }

    private void setGoal(Camera c) {
        Camera sanitized = c.isValid() ? c : c.sanitized();
        goal = sanitized.isValid() ? sanitized : home;
    }

    /**
     * Advances the current camera towards the goal.
     *
     * @return true if the current camera changed (i.e. a re-render is needed)
     */
    public boolean update(double dt) {
        if (current == goal) {
            return false;
        }
        if (!smoothing || current.approximatelyEquals(goal, 1e-4)) {
            current = goal;
            return true;
        }
        // Frame-rate independent exponential approach: after 1/speed seconds ~63% is covered.
        double alpha = 1 - Math.exp(-smoothingSpeed * MathUtils.clamp(dt, 0, 0.25));
        current = current.interpolate(goal, alpha);
        return true;
    }

    public boolean isMoving() {
        return current != goal;
    }

    public void saveBookmark(int slot) {
        bookmarks[MathUtils.clamp(slot, 0, bookmarks.length - 1)] = goal;
    }

    public boolean restoreBookmark(int slot) {
        Camera c = bookmarks[MathUtils.clamp(slot, 0, bookmarks.length - 1)];
        if (c != null) {
            setGoal(c);
            return true;
        }
        return false;
    }

    public Camera bookmark(int slot) {
        return bookmarks[MathUtils.clamp(slot, 0, bookmarks.length - 1)];
    }

    public void setBookmark(int slot, Camera camera) {
        bookmarks[MathUtils.clamp(slot, 0, bookmarks.length - 1)] = camera;
    }

    public int bookmarkCount() {
        return bookmarks.length;
    }
}
