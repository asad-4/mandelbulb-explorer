package com.mandelbulb.util;

import com.mandelbulb.fractal.TrapType;
import com.mandelbulb.math.Color3;
import com.mandelbulb.rendering.ColorMode;
import com.mandelbulb.rendering.Palette;
import com.mandelbulb.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SettingsCodecTest {

    @Test
    void sceneRoundTripsThroughProperties() {
        Scene s = new Scene();
        s.fractal.power = 7.25;
        s.fractal.juliaMode = true;
        s.render.maxSteps = 333;
        s.lighting.key.color = Color3.fromHex("#123456");
        s.lighting.point.enabled = true;
        s.color.palette = Palette.LAVA;
        s.color.mode = ColorMode.TRAP_RGB;
        s.color.trapType = TrapType.AXES;
        s.camera = s.camera.orbit(0.4, 0.2).withDistance(1.234);

        Properties p = new Properties();
        SettingsCodec.write(s, "", p);
        Scene back = new Scene();
        SettingsCodec.read(back, "", p);

        assertEquals(7.25, back.fractal.power);
        assertTrue(back.fractal.juliaMode);
        assertEquals(333, back.render.maxSteps);
        assertEquals("#123456", back.lighting.key.color.toHex());
        assertTrue(back.lighting.point.enabled);
        assertEquals(Palette.LAVA, back.color.palette);
        assertEquals(ColorMode.TRAP_RGB, back.color.mode);
        assertEquals(TrapType.AXES, back.color.trapType);
        assertTrue(back.camera.approximatelyEquals(s.camera, 1e-12));
    }

    @Test
    void malformedAndOutOfRangeValuesAreRejectedOrClamped() {
        Properties p = new Properties();
        p.setProperty("fractal.power", "banana");
        p.setProperty("fractal.maxIterations", "999999");
        p.setProperty("render.stepScale", "NaN");
        p.setProperty("color.palette", "NOT_A_PALETTE");
        p.setProperty("unknown.key", "1");
        Scene s = new Scene();
        SettingsCodec.read(s, "", p);
        assertEquals(8.0, s.fractal.power, "unparsable value ignored");
        assertEquals(1000, s.fractal.maxIterations, "clamped");
        assertEquals(0.85, s.render.stepScale, "NaN replaced by default");
        assertEquals(Palette.GOLD, s.color.palette);
    }

    @Test
    void deepCopyIsIndependent() {
        Scene s = new Scene();
        Scene c = s.copy();
        c.lighting.key.intensity = 99;
        c.fractal.power = 3;
        assertNotEquals(99, s.lighting.key.intensity);
        assertEquals(8.0, s.fractal.power);
        assertNotSame(s.lighting.key, c.lighting.key);
    }
}
