package com.mandelbulb.util;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.math.Color3;
import com.mandelbulb.math.Quaternion;
import com.mandelbulb.math.Vector3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serialises {@link Settings} trees to flat {@link Properties} ({@code render.maxSteps=200},
 * {@code lighting.key.intensity=3.0}, ...). Used for custom presets and saved camera views.
 *
 * <p>Reading is lenient: unknown keys are ignored, missing keys keep their current value and
 * malformed values are logged and skipped, so presets saved by older versions still load.
 */
public final class SettingsCodec {

    private static final Logger LOG = Logger.getLogger(SettingsCodec.class.getName());

    private SettingsCodec() {
    }

    public static void write(Settings settings, String prefix, Properties out) {
        try {
            for (Field f : settings.getClass().getFields()) {
                if (skip(f)) continue;
                String key = prefix.isEmpty() ? f.getName() : prefix + "." + f.getName();
                Object v = f.get(settings);
                if (v == null) continue;
                if (v instanceof Settings nested) {
                    write(nested, key, out);
                } else if (v instanceof Camera cam) {
                    writeCamera(cam, key, out);
                } else if (v instanceof Color3 c) {
                    out.setProperty(key, c.toHex());
                } else if (v instanceof Enum<?> e) {
                    out.setProperty(key, e.name());
                } else if (v instanceof Double d) {
                    out.setProperty(key, Double.toString(d));
                } else {
                    out.setProperty(key, String.valueOf(v));
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void read(Settings settings, String prefix, Properties in) {
        for (Field f : settings.getClass().getFields()) {
            if (skip(f)) continue;
            String key = prefix.isEmpty() ? f.getName() : prefix + "." + f.getName();
            try {
                Class<?> type = f.getType();
                if (Settings.class.isAssignableFrom(type)) {
                    read((Settings) f.get(settings), key, in);
                    continue;
                }
                if (type == Camera.class) {
                    Camera cam = readCamera(key, in, (Camera) f.get(settings));
                    f.set(settings, cam);
                    continue;
                }
                String s = in.getProperty(key);
                if (s == null) continue;
                s = s.trim();
                if (type == double.class) f.setDouble(settings, Double.parseDouble(s));
                else if (type == int.class) f.setInt(settings, Integer.parseInt(s));
                else if (type == long.class) f.setLong(settings, Long.parseLong(s));
                else if (type == boolean.class) f.setBoolean(settings, Boolean.parseBoolean(s));
                else if (type == String.class) f.set(settings, s);
                else if (type == Color3.class) f.set(settings, Color3.fromHex(s));
                else if (type.isEnum()) f.set(settings, Enum.valueOf((Class<? extends Enum>) type, s));
            } catch (RuntimeException | IllegalAccessException e) {
                LOG.log(Level.WARNING, "Ignoring malformed setting " + key + ": " + e.getMessage());
            }
        }
        settings.validate();
    }

    public static void writeCamera(Camera c, String key, Properties out) {
        put(out, key + ".target.x", c.target().x());
        put(out, key + ".target.y", c.target().y());
        put(out, key + ".target.z", c.target().z());
        put(out, key + ".orientation.w", c.orientation().w());
        put(out, key + ".orientation.x", c.orientation().x());
        put(out, key + ".orientation.y", c.orientation().y());
        put(out, key + ".orientation.z", c.orientation().z());
        put(out, key + ".distance", c.distance());
        put(out, key + ".fov", c.fovDegrees());
        put(out, key + ".near", c.near());
        put(out, key + ".far", c.far());
    }

    /** Reads a camera; returns {@code fallback} if the keys are absent or invalid. */
    public static Camera readCamera(String key, Properties in, Camera fallback) {
        if (in.getProperty(key + ".distance") == null) {
            return fallback;
        }
        try {
            Vector3 target = new Vector3(get(in, key + ".target.x"), get(in, key + ".target.y"), get(in, key + ".target.z"));
            Quaternion q = new Quaternion(get(in, key + ".orientation.w"), get(in, key + ".orientation.x"),
                    get(in, key + ".orientation.y"), get(in, key + ".orientation.z"));
            Camera c = new Camera(target, q, get(in, key + ".distance"), get(in, key + ".fov"),
                    get(in, key + ".near"), get(in, key + ".far"));
            return c.sanitized();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Ignoring malformed camera " + key + ": " + e.getMessage());
            return fallback;
        }
    }

    private static void put(Properties p, String k, double v) {
        p.setProperty(k, String.format(Locale.ROOT, "%.17g", v));
    }

    private static double get(Properties p, String k) {
        String s = p.getProperty(k);
        if (s == null) throw new IllegalArgumentException("missing " + k);
        return Double.parseDouble(s.trim());
    }

    private static boolean skip(Field f) {
        int mod = f.getModifiers();
        return Modifier.isStatic(mod) || Modifier.isFinal(mod);
    }
}
