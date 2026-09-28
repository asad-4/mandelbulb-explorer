package com.mandelbulb.rendering;

/** Which mathematical property of the surface point drives the palette lookup. */
public enum ColorMode {
    COMBINED("Combined (iteration + trap)"),
    ORBIT_TRAP("Orbit trap"),
    TRAP_RGB("Orbit trap RGB (|x|,|y|,|z|)"),
    ITERATION("Smooth iteration count"),
    HEIGHT("Height (z)"),
    RADIAL("Radial distance |p|"),
    CURVATURE("Curvature (DE Laplacian)"),
    NORMAL("Surface normal"),
    POSITION("World position"),
    STEPS("Ray-march step count"),
    SOLID("Solid colour");

    private final String label;

    ColorMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
