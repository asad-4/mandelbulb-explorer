package com.mandelbulb.rendering;

/** Whether the interactive render follows the viewport size or uses a fixed resolution. */
public enum ResolutionMode {
    VIEWPORT("Match viewport"),
    FIXED("Fixed resolution");

    private final String label;

    ResolutionMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
