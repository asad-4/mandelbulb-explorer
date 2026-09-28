package com.mandelbulb.animation;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraPathTest {

    @Test
    void pathPassesThroughWaypoints() {
        CameraPath path = new CameraPath();
        Camera a = Camera.lookAt(new Vector3(0, -3, 1), Vector3.ZERO, Vector3.UNIT_Z, 40);
        Camera b = Camera.lookAt(new Vector3(2, -2, 0.5), Vector3.ZERO, Vector3.UNIT_Z, 50);
        Camera c = Camera.lookAt(new Vector3(1.5, 0.5, 0.2), new Vector3(0.3, 0, 0), Vector3.UNIT_Z, 60);
        path.add(a);
        path.add(b);
        path.add(c);
        for (Easing e : Easing.values()) {
            assertEquals(0, a.position().distance(path.sample(0, e).position()), 1e-9, e.name());
            assertEquals(0, c.position().distance(path.sample(1, e).position()), 1e-9, e.name());
        }
        assertEquals(0, b.position().distance(path.sample(0.5, Easing.LINEAR).position()), 1e-9);
        assertEquals(50, path.sample(0.5, Easing.LINEAR).fovDegrees(), 1e-9);
    }

    @Test
    void pathIsContinuous() {
        CameraPath path = new CameraPath();
        path.add(Camera.defaultCamera());
        path.add(Camera.defaultCamera().orbit(1, 0.2));
        path.add(Camera.defaultCamera().orbit(2, -0.1).withDistance(1.5));
        Vector3 prev = path.sample(0, Easing.SMOOTHSTEP).position();
        for (double u = 0.001; u <= 1; u += 0.001) {
            Vector3 p = path.sample(u, Easing.SMOOTHSTEP).position();
            assertTrue(p.distance(prev) < 0.05, "no jumps at u=" + u);
            prev = p;
        }
    }

    @Test
    void easingsMapUnitIntervalOntoItself() {
        for (Easing e : Easing.values()) {
            assertEquals(0, e.ease(0), 1e-12, e.name());
            assertEquals(1, e.ease(1), 1e-12, e.name());
            assertEquals(1, e.ease(7), 1e-12, "clamped");
        }
    }

    @Test
    void animatedSceneIsPureFunctionOfTime() {
        AnimationController anim = new AnimationController();
        anim.settings().rotateObject = true;
        anim.settings().rotationSpeed = 10;
        anim.settings().animatePower = true;
        Scene base = new Scene();
        Scene s1 = anim.sceneAt(base, 2.0);
        Scene s2 = anim.sceneAt(base, 2.0);
        assertEquals(20, s1.fractal.rotationZ, 1e-9);
        assertEquals(s1.fractal.power, s2.fractal.power);
        assertEquals(0, base.fractal.rotationZ, "base scene untouched");
        assertEquals(8.0, base.fractal.power);
    }
}
