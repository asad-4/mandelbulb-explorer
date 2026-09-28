package com.mandelbulb.math;

/** Immutable 2D vector, used for screen-space positions (e.g. projected light indicator). */
public record Vector2(double x, double y) {

    public static final Vector2 ZERO = new Vector2(0, 0);

    public Vector2 add(Vector2 o) {
        return new Vector2(x + o.x, y + o.y);
    }

    public Vector2 sub(Vector2 o) {
        return new Vector2(x - o.x, y - o.y);
    }

    public Vector2 mul(double s) {
        return new Vector2(x * s, y * s);
    }

    public double dot(Vector2 o) {
        return x * o.x + y * o.y;
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public Vector2 normalize() {
        double len = length();
        return len > 1e-300 ? new Vector2(x / len, y / len) : ZERO;
    }
}
