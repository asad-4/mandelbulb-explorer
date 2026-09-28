package com.mandelbulb.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vector3Test {

    private static final double EPS = 1e-12;

    @Test
    void basicArithmetic() {
        Vector3 a = new Vector3(1, 2, 3), b = new Vector3(-4, 5, 0.5);
        assertEquals(new Vector3(-3, 7, 3.5), a.add(b));
        assertEquals(new Vector3(5, -3, 2.5), a.sub(b));
        assertEquals(new Vector3(2, 4, 6), a.mul(2));
        assertEquals(-4 + 10 + 1.5, a.dot(b), EPS);
        assertEquals(Math.sqrt(14), a.length(), EPS);
        assertEquals(new Vector3(1, 2, 3).addScaled(b, 2), a.add(b.mul(2)));
    }

    @Test
    void crossProductIsOrthogonalAndRightHanded() {
        assertEquals(Vector3.UNIT_Z, Vector3.UNIT_X.cross(Vector3.UNIT_Y));
        Vector3 a = new Vector3(0.3, -1.2, 2), b = new Vector3(4, 0.1, -0.7);
        Vector3 c = a.cross(b);
        assertEquals(0, c.dot(a), 1e-12);
        assertEquals(0, c.dot(b), 1e-12);
    }

    @Test
    void normalizeHandlesZeroAndNonFinite() {
        assertEquals(1, new Vector3(3, 4, 12).normalize().length(), EPS);
        assertEquals(Vector3.ZERO, Vector3.ZERO.normalize());
        assertEquals(Vector3.UNIT_X, new Vector3(Double.NaN, 0, 0).normalize(Vector3.UNIT_X));
        assertEquals(Vector3.UNIT_Y, Vector3.ZERO.normalize(Vector3.UNIT_Y));
    }

    @Test
    void reflectAndLerp() {
        Vector3 d = new Vector3(1, -1, 0).normalize();
        Vector3 r = d.reflect(Vector3.UNIT_Y);
        assertEquals(d.x(), r.x(), EPS);
        assertEquals(-d.y(), r.y(), EPS);
        assertEquals(new Vector3(0.5, 1, 1.5), Vector3.ZERO.lerp(new Vector3(1, 2, 3), 0.5));
    }

    @Test
    void sphericalRoundTrip() {
        double[][] points = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -2}, {0.3, -0.7, 0.2}, {-1.5, -0.2, -0.9}};
        for (double[] p : points) {
            Vector3 v = new Vector3(p[0], p[1], p[2]);
            double[] s = v.toSpherical();
            assertTrue(s[1] >= 0 && s[1] <= Math.PI, "theta in [0, pi]");
            Vector3 back = Vector3.fromSpherical(s[0], s[1], s[2]);
            assertEquals(v.x(), back.x(), 1e-12);
            assertEquals(v.y(), back.y(), 1e-12);
            assertEquals(v.z(), back.z(), 1e-12);
        }
    }

    @Test
    void sphericalConventionMatchesMandelbulbDefinition() {
        // theta = acos(z / r), phi = atan2(y, x)
        double[] s = new Vector3(0, 1, 0).toSpherical();
        assertEquals(1, s[0], EPS);
        assertEquals(Math.PI / 2, s[1], EPS);
        assertEquals(Math.PI / 2, s[2], EPS);
        double[] north = new Vector3(0, 0, 3).toSpherical();
        assertEquals(0, north[1], EPS);
        double[] origin = Vector3.ZERO.toSpherical();
        assertArrayEquals(new double[] {0, 0, 0}, origin, EPS);
    }

    @Test
    void finiteness() {
        assertTrue(new Vector3(1, 2, 3).isFinite());
        assertFalse(new Vector3(1, Double.POSITIVE_INFINITY, 3).isFinite());
        assertFalse(new Vector3(Double.NaN, 0, 0).isFinite());
    }
}
