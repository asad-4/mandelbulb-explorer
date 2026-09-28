package com.mandelbulb.app;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.camera.CameraController;
import com.mandelbulb.scene.Scene;
import com.mandelbulb.util.SettingsCodec;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Persists custom presets and camera bookmarks as {@code .properties} files under
 * {@code ~/.mandelbulb-explorer/}. Human-readable and diff-able; unknown keys are ignored.
 */
public final class PresetStore {

    private static final Logger LOG = Logger.getLogger(PresetStore.class.getName());
    private static final String EXT = ".properties";

    private final Path root;
    private final Path presetDir;

    public PresetStore() {
        this(Path.of(System.getProperty("user.home"), ".mandelbulb-explorer"));
    }

    public PresetStore(Path root) {
        this.root = root;
        this.presetDir = root.resolve("presets");
    }

    public Path directory() {
        return presetDir;
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(presetDir)) return names;
        try (Stream<Path> files = Files.list(presetDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(EXT)).sorted().forEach(p -> {
                String n = p.getFileName().toString();
                names.add(n.substring(0, n.length() - EXT.length()));
            });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot list presets", e);
        }
        return names;
    }

    public void save(String name, Scene scene) throws IOException {
        String file = fileName(name);
        Files.createDirectories(presetDir);
        Properties p = new Properties();
        SettingsCodec.write(scene, "", p);
        try (Writer w = Files.newBufferedWriter(presetDir.resolve(file + EXT), StandardCharsets.UTF_8)) {
            p.store(w, "Mandelbulb Explorer preset: " + name);
        }
    }

    public Scene load(String name) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(presetDir.resolve(fileName(name) + EXT), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        Scene s = new Scene();
        SettingsCodec.read(s, "", p);
        return s;
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(presetDir.resolve(fileName(name) + EXT));
    }

    /** Restricts preset names to a safe file-name alphabet. */
    static String fileName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9 _.-]", "_");
        if (n.isEmpty() || n.startsWith(".")) {
            throw new IllegalArgumentException("Please enter a preset name (letters, digits, space, _ . -)");
        }
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    public void saveBookmarks(CameraController camera) {
        Properties p = new Properties();
        for (int i = 0; i < camera.bookmarkCount(); i++) {
            Camera c = camera.bookmark(i);
            if (c != null) SettingsCodec.writeCamera(c, "bookmark" + i, p);
        }
        try {
            Files.createDirectories(root);
            try (Writer w = Files.newBufferedWriter(root.resolve("cameras" + EXT), StandardCharsets.UTF_8)) {
                p.store(w, "Mandelbulb Explorer camera bookmarks");
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot save camera bookmarks", e);
        }
    }

    public void loadBookmarks(CameraController camera) {
        Path file = root.resolve("cameras" + EXT);
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
            for (int i = 0; i < camera.bookmarkCount(); i++) {
                Camera c = SettingsCodec.readCamera("bookmark" + i, p, null);
                if (c != null) camera.setBookmark(i, c);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Cannot load camera bookmarks", e);
        }
    }
}
