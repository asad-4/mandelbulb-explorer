package com.mandelbulb.camera;

import com.mandelbulb.math.Quaternion;
import com.mandelbulb.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraTest {

    @Test
    void defaultCameraLooksAtTargetFromAbove() {
        Camera c = Camera.defaultCamera();
        Vector3 toTarget = c.target().sub(c.position()).normalize();
        assertEquals(1, toTarget.dot(c.forward()), 1e-12);
        assertTrue(c.position().z() > 0, "slightly above the equator");
        assertEquals(c.distance(), c.position().distance(c.target()), 1e-12);
        assertTrue(c.isValid());
    }

    @Test
    void basisIsOrthonormal() {
        Camera c = Camera.defaultCamera().orbit(0.7, -0.3).roll(0.2);
        assertEquals(0, c.forward().dot(c.right()), 1e-12);
        assertEquals(0, c.forward().dot(c.up()), 1e-12);
        assertEquals(1, c.right().cross(c.forward()).dot(c.up()), 1e-12);
    }

    @Test
    void orbitPreservesTargetAndDistance() {
        Camera c = Camera.defaultCamera();
        Camera o = c.orbit(1.2, 0.4);
        assertEquals(c.target(), o.target());
        assertEquals(c.distance(), o.position().distance(o.target()), 1e-12);
        assertNotEquals(c.position(), o.position());
    }

    @Test
    void freeLookKeepsEyeFixed() {
        Camera c = Camera.defaultCamera();
        Camera f = c.freeLook(0.3, -0.2);
        assertEquals(0, c.position().distance(f.position()), 1e-12);
        assertTrue(c.forward().dot(f.forward()) < 0.999);
    }

    @Test
    void lookAtAndProjectTargetToImageCentre() {
        Camera c = Camera.lookAt(new Vector3(1, -3, 2), new Vector3(0.2, 0.1, -0.1), Vector3.UNIT_Z, 45);
        double[] p = c.project(new Vector3(0.2, 0.1, -0.1), 800, 600);
        assertNotNull(p);
        assertEquals(400, p[0], 1e-9);
        assertEquals(300, p[1], 1e-9);
        assertNull(c.project(c.position().addScaled(c.forward(), -1), 800, 600), "behind the camera");
        // A point up and right of centre lands up and right on screen.
        double[] q = c.project(c.target().add(c.up().mul(0.1)).add(c.right().mul(0.1)), 800, 600);
        assertTrue(q[0] > 400 && q[1] < 300);
    }

    @Test
    void lookAtStraightDownIsStable() {
        Camera c = Camera.lookAt(new Vector3(0, 0, 3), Vector3.ZERO, Vector3.UNIT_Z, 45);
        assertTrue(c.isValid());
        assertEquals(-1, c.forward().z(), 1e-12);
    }

    @Test
    void interpolationHitsEndpoints() {
        Camera a = Camera.defaultCamera();
        Camera b = a.orbit(1.0, 0.3).withDistance(0.5).translate(new Vector3(0.1, 0, 0));
        assertTrue(a.interpolate(b, 0).approximatelyEquals(a, 1e-9));
        assertTrue(a.interpolate(b, 1).approximatelyEquals(b, 1e-9));
        Camera mid = a.interpolate(b, 0.5);
        // Geometric interpolation of distance.
        assertEquals(Math.sqrt(a.distance() * b.distance()), mid.distance(), 1e-9);
    }

    @Test
    void invalidStatesAreSanitized() {
        Camera bad = new Camera(new Vector3(Double.NaN, 0, 0), Quaternion.IDENTITY, 2, 45, 1e-6, 50);
        assertFalse(bad.isValid());
        assertTrue(bad.sanitized().isValid());
        Camera weird = new Camera(Vector3.ZERO, new Quaternion(2, 0, 0, 0), -5, 500, -1, -2);
        Camera fixed = weird.sanitized();
        assertTrue(fixed.isValid());
        assertTrue(fixed.fovDegrees() <= Camera.MAX_FOV);
        assertTrue(fixed.distance() > 0);
    }
}
