package com.mandelbulb.fractal;

/** Geometric primitive that an orbit trap measures the orbit's closest approach to. */
public enum TrapType {
    X_PLANE("Plane x = 0  (min |x|)", 0, 0.5),
    Y_PLANE("Plane y = 0  (min |y|)", 0, 0.5),
    Z_PLANE("Plane z = 0  (min |z|)", 0, 0.5),
    ORIGIN("Origin  (min r)", 0.6, 1.0),
    POINT("Point (0.5, 0.5, 0.5)", 0.2, 1.4),
    SHELL("Sphere shell r = 1", 0, 0.07),
    AXES("Coordinate axes", 0.1, 0.5),
    PLANES("Nearest coordinate plane", 0, 0.17);

    private final String label;
    private final double typicalMin;
    private final double typicalMax;

    /**
     * typicalMin/typicalMax are the approximate 10th/90th percentiles of the trap value over the
     * surface of the power-8 bulb (measured); they spread the value over the whole palette.
     */
    TrapType(String label, double typicalMin, double typicalMax) {
        this.label = label;
        this.typicalMin = typicalMin;
        this.typicalMax = typicalMax;
    }

    /** Maps a raw trap distance to roughly [0,1] over the values that occur on the surface. */
    public double normalize(double value) {
        return (value - typicalMin) / (typicalMax - typicalMin);
    }

    @Override
    public String toString() {
        return label;
    }
}
