package com.mandelbulb.math;

/**
 * Immutable 3D vector used by the camera, scene and UI layers.
 *
 * <p>The ray-marching hot path deliberately does <em>not</em> use this class: it works on
 * primitive doubles so that no objects are allocated per march step. Vector3 is for code that
 * runs a handful of times per frame, where clarity matters more than allocation.
 */
public record Vector3(double x, double y, double z) {

    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 UNIT_X = new Vector3(1, 0, 0);
    public static final Vector3 UNIT_Y = new Vector3(0, 1, 0);
    public static final Vector3 UNIT_Z = new Vector3(0, 0, 1);

    public Vector3 add(Vector3 o) {
        return new Vector3(x + o.x, y + o.y, z + o.z);
    }

    public Vector3 sub(Vector3 o) {
        return new Vector3(x - o.x, y - o.y, z - o.z);
    }

    public Vector3 mul(double s) {
        return new Vector3(x * s, y * s, z * s);
    }

    /** Component-wise product. */
    public Vector3 mul(Vector3 o) {
        return new Vector3(x * o.x, y * o.y, z * o.z);
    }

    /** Returns {@code this + o * s} without an intermediate allocation. */
    public Vector3 addScaled(Vector3 o, double s) {
        return new Vector3(x + o.x * s, y + o.y * s, z + o.z * s);
    }

    public Vector3 negate() {
        return new Vector3(-x, -y, -z);
    }

    public double dot(Vector3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vector3 cross(Vector3 o) {
        return new Vector3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double distance(Vector3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Returns the unit vector in the same direction. A zero (or non-finite) vector cannot be
     * normalized; in that case {@code fallback} is returned instead of producing NaNs.
     */
    public Vector3 normalize(Vector3 fallback) {
        double len = length();
        if (!(len > 1e-300) || !Double.isFinite(len)) {
            return fallback;
        }
        return new Vector3(x / len, y / len, z / len);
    }

    public Vector3 normalize() {
        return normalize(ZERO);
    }

    public Vector3 lerp(Vector3 o, double t) {
        return new Vector3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    /** Reflects this (incident) vector about the unit normal {@code n}. */
    public Vector3 reflect(Vector3 n) {
        double d = 2 * dot(n);
        return new Vector3(x - d * n.x, y - d * n.y, z - d * n.z);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public double get(int axis) {
        return switch (axis) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            default -> throw new IndexOutOfBoundsException("axis " + axis);
        };
    }

    /**
     * Converts to spherical coordinates using the same convention as the Mandelbulb formula:
     * {@code r = |v|}, {@code theta = acos(z / r)} (polar angle from +Z, in [0, pi]) and
     * {@code phi = atan2(y, x)} (azimuth in (-pi, pi]). At the origin both angles are
     * undefined and returned as 0.
     *
     * @return {r, theta, phi}
     */
    public double[] toSpherical() {
        double r = length();
        if (r < 1e-300) {
            return new double[] {0, 0, 0};
        }
        return new double[] {r, Math.acos(MathUtils.clamp(z / r, -1, 1)), Math.atan2(y, x)};
    }

    /** Inverse of {@link #toSpherical()}. */
    public static Vector3 fromSpherical(double r, double theta, double phi) {
        double st = Math.sin(theta);
        return new Vector3(r * st * Math.cos(phi), r * st * Math.sin(phi), r * Math.cos(theta));
    }

    @Override
    public String toString() {
        return String.format("(%.6f, %.6f, %.6f)", x, y, z);
    }
}
