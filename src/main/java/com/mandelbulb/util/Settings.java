package com.mandelbulb.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Base class for the plain parameter bags (fractal, render, lighting, colour, animation).
 *
 * <p>Settings objects are deliberately simple mutable data holders with public fields: the UI
 * edits them on the FX thread, and before every render the whole scene is deep-copied into an
 * immutable-by-convention snapshot that the render workers own exclusively. That snapshot
 * boundary is what makes the renderer thread-safe without any locking in the pixel loop.
 *
 * <p>Allowed field types: primitives, String, enums, immutable records (e.g. {@code Color3},
 * {@code Camera}) and nested {@code Settings} (which are deep-copied).
 */
public abstract class Settings {

    /** Clamps every field into its valid range and replaces NaN/infinite values by defaults. */
    public abstract void validate();

    /** Copies every public field of {@code src} (same concrete class) into this object. */
    public void copyFrom(Settings src) {
        if (src.getClass() != getClass()) {
            throw new IllegalArgumentException("Cannot copy " + src.getClass() + " into " + getClass());
        }
        copyFields(src, this);
    }

    /** Deep copy; nested settings are copied recursively. */
    @SuppressWarnings("unchecked")
    public static <T extends Settings> T deepCopy(T src) {
        try {
            T dst = (T) src.getClass().getDeclaredConstructor().newInstance();
            copyFields(src, dst);
            return dst;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Settings class needs a public no-arg constructor: " + src.getClass(), e);
        }
    }

    private static void copyFields(Settings src, Settings dst) {
        try {
            for (Field f : src.getClass().getFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) {
                    continue;
                }
                Object value = f.get(src);
                if (value instanceof Settings nested) {
                    Settings existing = (Settings) f.get(dst);
                    if (existing != null && existing != nested) {
                        existing.copyFrom(nested);
                    } else {
                        f.set(dst, deepCopy(nested));
                    }
                } else {
                    f.set(dst, value);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
