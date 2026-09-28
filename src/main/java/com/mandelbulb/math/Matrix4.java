package com.mandelbulb.math;

import java.util.Arrays;

/**
 * Immutable 4x4 affine transform stored in row-major order.
 *
 * <p>Used for the object (fractal) rotation: the renderer transforms rays from world space into
 * the fractal's own coordinate system once per ray, so the distance estimator never pays for the
 * rotation inside the march loop.
 */
public final class Matrix4 {

    public static final Matrix4 IDENTITY = new Matrix4(new double[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1});

    private final double[] m;

    private Matrix4(double[] m) {
        this.m = m;
    }

    public static Matrix4 of(double... values) {
        if (values.length != 16) {
            throw new IllegalArgumentException("Matrix4 needs 16 values");
        }
        return new Matrix4(values.clone());
    }

    public static Matrix4 rotationX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Matrix4(new double[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1});
    }

    public static Matrix4 rotationY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Matrix4(new double[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1});
    }

    public static Matrix4 rotationZ(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Matrix4(new double[] {c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    }

    public static Matrix4 translation(double x, double y, double z) {
        return new Matrix4(new double[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1});
    }

    public static Matrix4 scale(double s) {
        return new Matrix4(new double[] {s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, 0, 0, 0, 1});
    }

    public double get(int row, int col) {
        return m[row * 4 + col];
    }

    public Matrix4 multiply(Matrix4 o) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += m[i * 4 + k] * o.m[k * 4 + j];
                }
                r[i * 4 + j] = sum;
            }
        }
        return new Matrix4(r);
    }

    public Matrix4 transpose() {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                r[j * 4 + i] = m[i * 4 + j];
            }
        }
        return new Matrix4(r);
    }

    public Vector3 transformPoint(Vector3 p) {
        return new Vector3(
                m[0] * p.x() + m[1] * p.y() + m[2] * p.z() + m[3],
                m[4] * p.x() + m[5] * p.y() + m[6] * p.z() + m[7],
                m[8] * p.x() + m[9] * p.y() + m[10] * p.z() + m[11]);
    }

    /** Transforms a direction (ignores translation). */
    public Vector3 transformDirection(Vector3 d) {
        return new Vector3(
                m[0] * d.x() + m[1] * d.y() + m[2] * d.z(),
                m[4] * d.x() + m[5] * d.y() + m[6] * d.z(),
                m[8] * d.x() + m[9] * d.y() + m[10] * d.z());
    }

    /** Copies the upper-left 3x3 block (row-major) into {@code out[0..8]}. */
    public void copyRotation(double[] out) {
        out[0] = m[0]; out[1] = m[1]; out[2] = m[2];
        out[3] = m[4]; out[4] = m[5]; out[5] = m[6];
        out[6] = m[8]; out[7] = m[9]; out[8] = m[10];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Matrix4 other && Arrays.equals(m, other.m);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(m);
    }
}
