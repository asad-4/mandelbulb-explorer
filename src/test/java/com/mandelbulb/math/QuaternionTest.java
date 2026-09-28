package com.mandelbulb.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuaternionTest {

    private static void assertVec(Vector3 expected, Vector3 actual, double eps) {
        assertEquals(expected.x(), actual.x(), eps, "x");
        assertEquals(expected.y(), actual.y(), eps, "y");
        assertEquals(expected.z(), actual.z(), eps, "z");
    }

    @Test
    void axisAngleRotation() {
        Quaternion q = Quaternion.fromAxisAngle(Vector3.UNIT_Z, Math.PI / 2);
        assertVec(Vector3.UNIT_Y, q.rotate(Vector3.UNIT_X), 1e-12);
        assertVec(Vector3.UNIT_Z, q.rotate(Vector3.UNIT_Z), 1e-12);
    }

    @Test
    void compositionAppliesRightOperandFirst() {
        Quaternion a = Quaternion.fromAxisAngle(Vector3.UNIT_Z, Math.PI / 2);
        Quaternion b = Quaternion.fromAxisAngle(Vector3.UNIT_X, Math.PI / 2);
        Vector3 v = new Vector3(0.2, 0.7, -0.4);
        assertVec(a.rotate(b.rotate(v)), a.mul(b).rotate(v), 1e-12);
    }

    @Test
    void fromBasisReproducesBasis() {
        Quaternion q = Quaternion.fromAxisAngle(new Vector3(1, 2, 3), 1.1);
        Quaternion r = Quaternion.fromBasis(q.rotate(Vector3.UNIT_X), q.rotate(Vector3.UNIT_Y), q.rotate(Vector3.UNIT_Z));
        assertEquals(1, Math.abs(q.dot(r)), 1e-9);
    }

    @Test
    void slerpEndpointsAndMidpoint() {
        Quaternion a = Quaternion.IDENTITY;
        Quaternion b = Quaternion.fromAxisAngle(Vector3.UNIT_Z, Math.PI / 2);
        assertEquals(1, Math.abs(Quaternion.slerp(a, b, 0).dot(a)), 1e-12);
        assertEquals(1, Math.abs(Quaternion.slerp(a, b, 1).dot(b)), 1e-12);
        Quaternion mid = Quaternion.slerp(a, b, 0.5);
        assertVec(new Vector3(Math.cos(Math.PI / 4), Math.sin(Math.PI / 4), 0), mid.rotate(Vector3.UNIT_X), 1e-12);
        // Shortest path: slerp towards -b must equal slerp towards b.
        assertEquals(1, Math.abs(Quaternion.slerp(a, b.negate(), 0.3).dot(Quaternion.slerp(a, b, 0.3))), 1e-12);
    }

    @Test
    void degenerateNormalizeFallsBackToIdentity() {
        assertEquals(Quaternion.IDENTITY, new Quaternion(0, 0, 0, 0).normalize());
        assertEquals(Quaternion.IDENTITY, new Quaternion(Double.NaN, 0, 0, 0).normalize());
    }
}
