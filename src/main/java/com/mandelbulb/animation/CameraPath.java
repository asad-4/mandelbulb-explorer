package com.mandelbulb.animation;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.math.MathUtils;
import com.mandelbulb.math.Quaternion;
import com.mandelbulb.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fly-through path through camera waypoints.
 *
 * <p>Eye positions follow a Catmull-Rom spline (passes through every waypoint with continuous
 * velocity), orientations a quaternion Catmull-Rom spline, and field of view / pivot distance
 * are interpolated geometrically. The global parameter is shaped by an {@link Easing} so the
 * flight starts and ends gently.
 */
public final class CameraPath {

    private final List<Camera> waypoints = new ArrayList<>();

    public List<Camera> waypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public int size() {
        return waypoints.size();
    }

    public void add(Camera c) {
        waypoints.add(c);
    }

    public void insert(int index, Camera c) {
        waypoints.add(index, c);
    }

    public void remove(int index) {
        if (index >= 0 && index < waypoints.size()) waypoints.remove(index);
    }

    public void set(int index, Camera c) {
        waypoints.set(index, c);
    }

    public void move(int index, int delta) {
        int j = index + delta;
        if (index >= 0 && index < waypoints.size() && j >= 0 && j < waypoints.size()) {
            Collections.swap(waypoints, index, j);
        }
    }

    public void clear() {
        waypoints.clear();
    }

    /**
     * Camera at path parameter u in [0,1].
     *
     * @return null if the path has no waypoints
     */
    public Camera sample(double u, Easing easing) {
        int n = waypoints.size();
        if (n == 0) return null;
        if (n == 1) return waypoints.get(0);
        double s = easing.ease(u) * (n - 1);
        int i = Math.min((int) Math.floor(s), n - 2);
        double f = s - i;
        Camera c0 = waypoints.get(Math.max(i - 1, 0));
        Camera c1 = waypoints.get(i);
        Camera c2 = waypoints.get(i + 1);
        Camera c3 = waypoints.get(Math.min(i + 2, n - 1));

        Vector3 p0 = c0.position(), p1 = c1.position(), p2 = c2.position(), p3 = c3.position();
        Vector3 eye = new Vector3(
                MathUtils.catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), f),
                MathUtils.catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), f),
                MathUtils.catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), f));
        Quaternion q = Quaternion.catmullRom(c0.orientation(), c1.orientation(), c2.orientation(), c3.orientation(), f);
        double dist = Math.exp(MathUtils.lerp(Math.log(c1.distance()), Math.log(c2.distance()), f));
        double fov = MathUtils.lerp(c1.fovDegrees(), c2.fovDegrees(), f);
        double near = Math.min(c1.near(), c2.near());
        double far = Math.max(c1.far(), c2.far());
        Vector3 forward = q.rotate(Vector3.UNIT_Y);
        return new Camera(eye.addScaled(forward, dist), q, dist, fov, near, far).sanitized();
    }
}
