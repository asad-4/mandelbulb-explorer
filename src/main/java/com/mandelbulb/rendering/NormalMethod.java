package com.mandelbulb.rendering;

/** Numerical gradient scheme used to obtain surface normals from the distance estimator. */
public enum NormalMethod {
    /** Central differences along x, y, z: 6 DE evaluations, second-order accurate. */
    CENTRAL("Central differences (6 DE)"),
    /**
     * Tetrahedral sampling: 4 DE evaluations. Only first-order accurate (the tetrahedron's
     * third moment sum k_x k_y k_z is non-zero, so an O(eps) error remains), which is
     * invisible at pixel-sized eps but makes it slightly less precise than central differences.
     */
    TETRAHEDRAL("Tetrahedral (4 DE)");

    private final String label;

    NormalMethod(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
