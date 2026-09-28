package com.mandelbulb.rendering;

/** Standard output resolutions. */
public enum ResolutionPreset {
    VGA(640, 480),
    HD_720(1280, 720),
    FULL_HD_1080(1920, 1080),
    QHD_1440(2560, 1440),
    UHD_2160(3840, 2160);

    public final int width;
    public final int height;

    ResolutionPreset(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return width + " × " + height;
    }
}
