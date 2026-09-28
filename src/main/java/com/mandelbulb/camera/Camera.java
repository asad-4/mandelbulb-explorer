package com.mandelbulb.camera;

import com.mandelbulb.math.MathUtils;
import com.mandelbulb.math.Quaternion;
import com.mandelbulb.math.Vector3;

/**
 * Immutable perspective camera.
 *
 * <p>The world is Z-up (matching the Mandelbulb's polar axis). The camera is stored as an orbit
 * rig: a pivot {@code target}, an {@code orientation} and a {@code distance}; the eye position is
 * derived as {@code target - forward * distance}. This makes orbiting, zooming and smooth
 * interpolation natural (interpolating the pivot and orientation moves the eye along arcs, not
 * chords that could cut through the fractal), while free-look and fly movement are expressed by
 * moving the pivot together with the eye.
 *
 * <p>Local camera axes: X = right, Y = forward, Z = up.
 */
public final class Camera {

    public static final double MIN_DISTANCE = 1e-9;
    public static final double MAX_DISTANCE = 100.0;
    public static final double MIN_FOV = 5.0;
    public static final double MAX_FOV = 140.0;

    private final Vector3 target;
    private final Quaternion orientation;
    private final double distance;
    private final double fovDegrees;
    private final double near;
    private final double far;

    public Camera(Vector3 target, Quaternion orientation, double distance, double fovDegrees, double near, double far) {
        this.target = target;
        this.orientation = orientation;
        this.distance = distance;
        this.fovDegrees = fovDegrees;
        this.near = near;
        this.far = far;
    }

    /** Classic three-quarter view from slightly above. */
    public static Camera defaultCamera() {
        return orbitCamera(Vector3.ZERO, Math.toRadians(-35), Math.toRadians(-24), 3.7, 40);
    }

    /**
     * Turntable-style construction: start looking along +Y, then pitch about the right axis and
     * yaw about world Z.
     */
    public static Camera orbitCamera(Vector3 target, double yaw, double pitch, double distance, double fov) {
        Quaternion q = Quaternion.fromAxisAngle(Vector3.UNIT_Z, yaw)
                .mul(Quaternion.fromAxisAngle(Vector3.UNIT_X, pitch));
        return new Camera(target, q, distance, fov, 1e-6, 50.0);
    }

    /** Camera at {@code eye} looking at {@code target}; {@code up} is a hint (default +Z). */
    public static Camera lookAt(Vector3 eye, Vector3 target, Vector3 up, double fov) {
        Vector3 forward = target.sub(eye);
        double dist = forward.length();
        forward = forward.normalize(Vector3.UNIT_Y);
        Vector3 right = forward.cross(up).normalize();
        if (right.lengthSquared() < 0.5) {
            // Looking straight along the up hint: choose any perpendicular.
            right = forward.cross(Math.abs(forward.x()) < 0.9 ? Vector3.UNIT_X : Vector3.UNIT_Y).normalize();
        }
        Vector3 camUp = right.cross(forward).normalize();
        Quaternion q = Quaternion.fromBasis(right, forward, camUp);
        return new Camera(target, q, Math.max(dist, MIN_DISTANCE), fov, 1e-6, 50.0);
    }

    public Vector3 target() {
        return target;
    }

    public Quaternion orientation() {
        return orientation;
    }

    public double distance() {
        return distance;
    }

    public double fovDegrees() {
        return fovDegrees;
    }

    public double near() {
        return near;
    }

    public double far() {
        return far;
    }

    public Vector3 forward() {
        return orientation.rotate(Vector3.UNIT_Y);
    }

    public Vector3 right() {
        return orientation.rotate(Vector3.UNIT_X);
    }

    public Vector3 up() {
        return orientation.rotate(Vector3.UNIT_Z);
    }

    public Vector3 position() {
        return target.addScaled(forward(), -distance);
    }

    public Camera withTarget(Vector3 t) {
        return new Camera(t, orientation, distance, fovDegrees, near, far);
    }

    public Camera withOrientation(Quaternion q) {
        return new Camera(target, q.normalize(), distance, fovDegrees, near, far);
    }

    /** Changes the pivot distance while keeping the target fixed (the eye moves). */
    public Camera withDistance(double d) {
        return new Camera(target, orientation, MathUtils.clamp(d, MIN_DISTANCE, MAX_DISTANCE), fovDegrees, near, far);
    }

    public Camera withFov(double fov) {
        return new Camera(target, orientation, distance, MathUtils.clamp(fov, MIN_FOV, MAX_FOV), near, far);
    }

    public Camera withClipping(double near, double far) {
        return new Camera(target, orientation, distance, fovDegrees, near, far);
    }

    /**
     * Orbits the eye around the target: pitch about the camera's right axis, then yaw about the
     * world Z axis. Target and distance are unchanged.
     */
    public Camera orbit(double yaw, double pitch) {
        Quaternion qPitch = Quaternion.fromAxisAngle(right(), pitch);
        Quaternion qYaw = Quaternion.fromAxisAngle(Vector3.UNIT_Z, yaw);
        return withOrientation(qYaw.mul(qPitch).mul(orientation));
    }

    /** Rotates the view about the eye (first-person look); the pivot moves with the view. */
    public Camera freeLook(double yaw, double pitch) {
        Vector3 eye = position();
        Quaternion qPitch = Quaternion.fromAxisAngle(right(), pitch);
        Quaternion qYaw = Quaternion.fromAxisAngle(up(), yaw);
        Quaternion q = qYaw.mul(qPitch).mul(orientation).normalize();
        Vector3 f = q.rotate(Vector3.UNIT_Y);
        return new Camera(eye.addScaled(f, distance), q, distance, fovDegrees, near, far);
    }

    /** Rotates about the viewing direction. */
    public Camera roll(double angle) {
        return withOrientation(Quaternion.fromAxisAngle(forward(), angle).mul(orientation));
    }

    /** Moves eye and target together by a world-space offset. */
    public Camera translate(Vector3 delta) {
        return withTarget(target.add(delta));
    }

    /** Moves eye and target in the camera's own frame. */
    public Camera moveLocal(double rightAmount, double forwardAmount, double upAmount) {
        Vector3 d = right().mul(rightAmount).add(forward().mul(forwardAmount)).add(up().mul(upAmount));
        return translate(d);
    }

    /** Keeps the eye where it is but makes {@code point} the new orbit pivot. */
    public Camera refocus(Vector3 point) {
        Vector3 eye = position();
        Camera c = lookAt(eye, point, up(), fovDegrees);
        return new Camera(c.target, c.orientation, c.distance, fovDegrees, near, far);
    }

    /**
     * Interpolates towards {@code o}: the pivot linearly, the orientation by slerp and the
     * distance geometrically (so zooming feels uniform at every scale).
     */
    public Camera interpolate(Camera o, double t) {
        double d = Math.exp(MathUtils.lerp(Math.log(distance), Math.log(o.distance), t));
        return new Camera(target.lerp(o.target, t), Quaternion.slerp(orientation, o.orientation, t), d,
                MathUtils.lerp(fovDegrees, o.fovDegrees, t), MathUtils.lerp(near, o.near, t),
                MathUtils.lerp(far, o.far, t));
    }

    /** True if the two cameras are visually identical (used to stop smoothing). */
    public boolean approximatelyEquals(Camera o, double relTolerance) {
        double scale = Math.max(Math.min(distance, o.distance), 1e-12);
        return target.distance(o.target) < scale * relTolerance
                && Math.abs(orientation.dot(o.orientation)) > 1 - relTolerance * 1e-3
                && Math.abs(Math.log(distance / o.distance)) < relTolerance
                && Math.abs(fovDegrees - o.fovDegrees) < 1e-3
                && Math.abs(near - o.near) < 1e-12 + near * relTolerance
                && Math.abs(far - o.far) < 1e-6;
    }

    /**
     * Projects a world point to pixel coordinates of a {@code width x height} image.
     *
     * @return {x, y, depth} or {@code null} if the point is behind the camera
     */
    public double[] project(Vector3 p, int width, int height) {
        Vector3 rel = p.sub(position());
        double zf = rel.dot(forward());
        if (zf <= 1e-12) {
            return null;
        }
        double tanHalf = Math.tan(Math.toRadians(fovDegrees) * 0.5);
        double aspect = (double) width / height;
        double sx = rel.dot(right()) / (zf * tanHalf * aspect);
        double sy = rel.dot(up()) / (zf * tanHalf);
        return new double[] {(sx + 1) * 0.5 * width, (1 - sy) * 0.5 * height, zf};
    }

    /** World-space size of one pixel at the pivot distance (used for panning). */
    public double worldUnitsPerPixel(int viewportHeight) {
        return 2 * distance * Math.tan(Math.toRadians(fovDegrees) * 0.5) / Math.max(1, viewportHeight);
    }

    public boolean isValid() {
        return target.isFinite() && orientation.isFinite() && Double.isFinite(distance) && distance > 0
                && Double.isFinite(fovDegrees) && Double.isFinite(near) && Double.isFinite(far) && far > near;
    }

    /** Returns a copy with every field forced into a valid range; invalid states reset. */
    public Camera sanitized() {
        if (!target.isFinite() || !orientation.isFinite() || !Double.isFinite(distance)) {
            return defaultCamera();
        }
        double n = MathUtils.sanitize(near, 0, 10, 1e-6);
        double f = MathUtils.sanitize(far, 0.1, 1000, 50);
        if (f <= n) f = n + 1;
        return new Camera(target, orientation.normalize(), MathUtils.clamp(distance, MIN_DISTANCE, MAX_DISTANCE),
                MathUtils.sanitize(fovDegrees, MIN_FOV, MAX_FOV, 42), n, f);
    }

    @Override
    public String toString() {
        return "Camera[eye=" + position() + ", target=" + target + ", distance=" + distance + ", fov=" + fovDegrees + "]";
    }
}
