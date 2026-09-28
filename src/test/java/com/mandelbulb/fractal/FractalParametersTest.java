package com.mandelbulb.fractal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FractalParametersTest {

    @Test
    void validationClampsRanges() {
        FractalParameters p = new FractalParameters();
        p.power = 99;
        p.maxIterations = -5;
        p.bailout = 1e9;
        p.validate();
        assertEquals(FractalParameters.MAX_POWER, p.power);
        assertEquals(FractalParameters.MIN_ITERATIONS, p.maxIterations);
        assertEquals(FractalParameters.MAX_BAILOUT, p.bailout);
    }

    @Test
    void validationReplacesNonFiniteValues() {
        FractalParameters p = new FractalParameters();
        p.power = Double.NaN;
        p.bailout = Double.POSITIVE_INFINITY;
        p.juliaX = Double.NaN;
        p.validate();
        assertEquals(8.0, p.power);
        assertEquals(4.0, p.bailout);
        assertEquals(0, p.juliaX);
    }

    @Test
    void mandelbulbConstructorNeverSeesInvalidParameters() {
        FractalParameters p = new FractalParameters();
        p.power = Double.NaN;
        p.maxIterations = 1_000_000;
        Mandelbulb mb = new Mandelbulb(p);
        assertEquals(8.0, mb.power());
        assertEquals(FractalParameters.MAX_ITERATIONS, mb.maxIterations());
        // The caller's object is not modified.
        assertTrue(Double.isNaN(p.power));
    }

    @Test
    void copyIsDeep() {
        FractalParameters p = new FractalParameters();
        FractalParameters c = p.copy();
        c.power = 3;
        assertEquals(8.0, p.power);
    }
}
