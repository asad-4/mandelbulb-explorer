package com.mandelbulb.rendering;

/** Result of a blocking (offline) render: packed ARGB pixels plus statistics. */
public record RenderResult(int[] pixels, int width, int height, RenderStats stats) {
}
