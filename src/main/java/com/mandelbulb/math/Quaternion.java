package com.mandelbulb.math;

/**
 * Immutable unit quaternion used for camera orientation.
 *
 * <p>Quaternions avoid gimbal lock and interpolate smoothly (slerp), which is what makes camera
 * smoothing and fly-through paths free of sudden jumps.
 */
public record Quaternion(double w, double x, double y, double z) {

    public static final Quaternion IDENTITY = new Quaternion(1, 0, 0, 0);

    /** Rotation of {@code angle} radians about {@code axis} (right-handed). */
    public static Quaternion fromAxisAngle(Vector3 axis, double angle) {
        Vector3 a = axis.normalize(Vector3.UNIT_Z);
        double h = angle * 0.5;
        double s = Math.sin(h);
        return new Quaternion(Math.cos(h), a.x() * s, a.y() * s, a.z() * s);
    }

    /**
     * Builds the rotation that maps the local axes X, Y, Z onto {@code right}, {@code forward}
     * and {@code up}. The three vectors must form an orthonormal right-handed basis.
     */
    public static Quaternion fromBasis(Vector3 right, Vector3 forward, Vector3 up) {
        // Rotation matrix with columns (right, forward, up); standard matrix -> quaternion.
        double m00 = right.x(), m01 = forward.x(), m02 = up.x();
        double m10 = right.y(), m11 = forward.y(), m12 = up.y();
        double m20 = right.z(), m21 = forward.z(), m22 = up.z();
        double trace = m00 + m11 + m22;
        double w, x, y, z;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1.0) * 2;
            w = 0.25 * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
            w = (m21 - m12) / s;
            x = 0.25 * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25 * s;
            z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25 * s;
        }
        return new Quaternion(w, x, y, z).normalize();
    }

    /** Hamilton product: the result applies {@code o} first, then {@code this}. */
    public Quaternion mul(Quaternion o) {
        return new Quaternion(
                w * o.w - x * o.x - y * o.y - z * o.z,
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w);
    }

    public Quaternion conjugate() {
        return new Quaternion(w, -x, -y, -z);
    }

    public Quaternion negate() {
        return new Quaternion(-w, -x, -y, -z);
    }

    public double dot(Quaternion o) {
        return w * o.w + x * o.x + y * o.y + z * o.z;
    }

    public double norm() {
        return Math.sqrt(w * w + x * x + y * y + z * z);
    }

    public Quaternion normalize() {
        double n = norm();
        if (!(n > 1e-300) || !Double.isFinite(n)) {
            return IDENTITY;
        }
        return new Quaternion(w / n, x / n, y / n, z / n);
    }

    /** Rotates {@code v} by this (unit) quaternion: q v q*. */
    public Vector3 rotate(Vector3 v) {
        // Optimised form of q * (0,v) * conj(q): v + 2w(u x v) + 2 u x (u x v), u = (x,y,z).
        double tx = 2 * (y * v.z() - z * v.y());
        double ty = 2 * (z * v.x() - x * v.z());
        double tz = 2 * (x * v.y() - y * v.x());
        return new Vector3(
                v.x() + w * tx + (y * tz - z * ty),
                v.y() + w * ty + (z * tx - x * tz),
                v.z() + w * tz + (x * ty - y * tx));
    }

    /** Spherical linear interpolation along the shortest arc. */
    public static Quaternion slerp(Quaternion a, Quaternion b, double t) {
        double cos = a.dot(b);
        if (cos < 0) {
            // q and -q are the same rotation; flip to take the short way round.
            b = b.negate();
            cos = -cos;
        }
        if (cos > 0.9995) {
            // Nearly parallel: slerp degenerates numerically, nlerp is accurate here.
            return new Quaternion(
                    a.w + (b.w - a.w) * t, a.x + (b.x - a.x) * t,
                    a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t).normalize();
        }
        double theta = Math.acos(cos);
        double sin = Math.sin(theta);
        double wa = Math.sin((1 - t) * theta) / sin;
        double wb = Math.sin(t * theta) / sin;
        return new Quaternion(
                a.w * wa + b.w * wb, a.x * wa + b.x * wb,
                a.y * wa + b.y * wb, a.z * wa + b.z * wb).normalize();
    }

    /**
     * C1-continuous interpolation through a sequence of orientations (used by fly-through paths).
     * The four quaternions are first aligned into the same hemisphere so that component-wise
     * Catmull-Rom interpolation follows the short arcs; the result is renormalised. For the
     * moderate rotations between camera waypoints this is visually indistinguishable from
     * SQUAD while being far simpler.
     */
    public static Quaternion catmullRom(Quaternion q0, Quaternion q1, Quaternion q2, Quaternion q3, double t) {
        if (q0.dot(q1) < 0) q0 = q0.negate();
        if (q2.dot(q1) < 0) q2 = q2.negate();
        if (q3.dot(q2) < 0) q3 = q3.negate();
        return new Quaternion(
                MathUtils.catmullRom(q0.w, q1.w, q2.w, q3.w, t),
                MathUtils.catmullRom(q0.x, q1.x, q2.x, q3.x, t),
                MathUtils.catmullRom(q0.y, q1.y, q2.y, q3.y, t),
                MathUtils.catmullRom(q0.z, q1.z, q2.z, q3.z, t)).normalize();
    }

    public boolean isFinite() {
        return Double.isFinite(w) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
