package com.mandelbulb.scene;

public enum LightType {
    /** Infinitely distant light (sun): constant direction, no falloff. */
    DIRECTIONAL,
    /** Point light at a position, inverse-square falloff. */
    POINT
}
