package com.mandelbulb.camera;

import com.mandelbulb.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraControllerTest {

    private static CameraController withUnitSphere() {
        CameraController c = new CameraController();
        c.setSurfaceDistance(p -> p.length() - 1.0);
        c.setSmoothing(false);
        return c;
    }

    @Test
    void zoomingInNeverEntersTheSurface() {
        CameraController c = withUnitSphere();
        for (int i = 0; i < 500; i++) {
            c.zoom(3);
            c.update(1 / 60.0);
            assertTrue(c.current().position().length() > 1.0, "eye stays outside after " + i + " zoom steps");
        }
        assertTrue(c.current().position().length() < 1 + 1e-6, "but gets very close");
    }

    @Test
    void flyingNeverTunnelsIntoTheSurface() {
        CameraController c = withUnitSphere();
        for (int i = 0; i < 2000; i++) {
            c.move(0, 1, 0, 0.1, 10);
            c.update(0.1);
            assertTrue(c.current().position().length() > 1.0);
        }
    }

    @Test
    void smoothingConvergesToGoal() {
        CameraController c = withUnitSphere();
        c.setSmoothing(true);
        c.orbit(200, 50);
        assertTrue(c.isMoving());
        for (int i = 0; i < 600 && c.isMoving(); i++) c.update(1 / 60.0);
        assertFalse(c.isMoving());
        assertSame(c.goal(), c.current());
    }

    @Test
    void bookmarksRoundTrip() {
        CameraController c = withUnitSphere();
        c.orbit(100, 0);
        c.update(1);
        Camera saved = c.goal();
        c.saveBookmark(3);
        c.reset();
        c.update(1);
        assertTrue(c.restoreBookmark(3));
        c.update(1);
        assertSame(saved, c.current());
        assertFalse(c.restoreBookmark(4));
    }

    @Test
    void focusMakesPointThePivotWithoutMovingTheEye() {
        CameraController c = withUnitSphere();
        Vector3 eye = c.goal().position();
        c.focus(new Vector3(0.5, -0.5, 0.3));
        assertEquals(0, eye.distance(c.goal().position()), 1e-9);
        assertEquals(0, c.goal().target().distance(new Vector3(0.5, -0.5, 0.3)), 1e-9);
    }

    @Test
    void speedScalesWithDistanceToSurface() {
        CameraController c = withUnitSphere();
        double far = c.movementSpeed();
        c.setCamera(Camera.lookAt(new Vector3(0, -1.01, 0), Vector3.ZERO, Vector3.UNIT_Z, 45), true);
        assertTrue(c.movementSpeed() < far / 50);
    }
}
