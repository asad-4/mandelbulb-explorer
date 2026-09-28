package com.mandelbulb.scene;

import com.mandelbulb.math.Color3;
import com.mandelbulb.math.MathUtils;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.util.Settings;

/**
 * A light source. Directional lights are described by azimuth/elevation (degrees; elevation 90 =
 * straight down from +Z), which is far easier to manipulate interactively than a raw vector.
 */
public final class Light extends Settings {

    public LightType type = LightType.DIRECTIONAL;
    public boolean enabled = true;
    public double azimuth = 195;
    public double elevation = 42;
    public double posX = 1.6;
    public double posY = -1.2;
    public double posZ = 1.4;
    public Color3 color = Color3.WHITE;
    public double intensity = 3.0;
    public boolean castShadows = true;

    public Light() {
    }

    public static Light directional(double azimuth, double elevation, Color3 color, double intensity, boolean shadows) {
        Light l = new Light();
        l.type = LightType.DIRECTIONAL;
        l.azimuth = azimuth;
        l.elevation = elevation;
        l.color = color;
        l.intensity = intensity;
        l.castShadows = shadows;
        return l;
    }

    public static Light point(Vector3 position, Color3 color, double intensity, boolean shadows) {
        Light l = new Light();
        l.type = LightType.POINT;
        l.posX = position.x();
        l.posY = position.y();
        l.posZ = position.z();
        l.color = color;
        l.intensity = intensity;
        l.castShadows = shadows;
        return l;
    }

    /** Unit vector pointing from the scene towards a directional light (world space). */
    public Vector3 direction() {
        double az = Math.toRadians(azimuth), el = Math.toRadians(elevation);
        return new Vector3(Math.cos(el) * Math.cos(az), Math.cos(el) * Math.sin(az), Math.sin(el));
    }

    public Vector3 position() {
        return new Vector3(posX, posY, posZ);
    }

    public Light copy() {
        return deepCopy(this);
    }

    @Override
    public void validate() {
        if (type == null) type = LightType.DIRECTIONAL;
        if (color == null) color = Color3.WHITE;
        color = color.sanitized();
        azimuth = MathUtils.sanitize(azimuth, -360, 360, 195);
        elevation = MathUtils.sanitize(elevation, -89.9, 89.9, 45);
        posX = MathUtils.sanitize(posX, -20, 20, 1.6);
        posY = MathUtils.sanitize(posY, -20, 20, -1.2);
        posZ = MathUtils.sanitize(posZ, -20, 20, 1.4);
        intensity = MathUtils.sanitize(intensity, 0, 50, 1);
    }
}
