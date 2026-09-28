package com.mandelbulb.app;

import com.mandelbulb.benchmark.Benchmark;
import com.mandelbulb.export.ImageExporter;
import com.mandelbulb.rendering.Palette;
import com.mandelbulb.rendering.RenderEngine;
import com.mandelbulb.rendering.RenderResult;
import com.mandelbulb.scene.FractalPreset;
import com.mandelbulb.scene.LightingPreset;
import com.mandelbulb.scene.Scene;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Command-line rendering without JavaFX.
 *
 * <pre>
 *   --render FILE        render to FILE (.png or .jpg)
 *   --benchmark          run the standard benchmark suite
 *   --width N --height N output size (default 1280x720)
 *   --ss N               supersampling 1..4
 *   --preset NAME        built-in preset, e.g. "Deep Zoom"
 *   --power X --iterations N --palette NAME --lighting NAME
 *   --threads N
 *   --set KEY=VALUE      any scene setting, e.g. color.mode=ORBIT_TRAP or render.detail=2
 * </pre>
 */
public final class HeadlessRunner {

    private HeadlessRunner() {
    }

    public static boolean handles(String[] args) {
        return Arrays.asList(args).contains("--render") || Arrays.asList(args).contains("--benchmark")
                || Arrays.asList(args).contains("--help");
    }

    public static int run(String[] args) {
        String out = null;
        boolean bench = false;
        int width = 1280, height = 720, ss = 1, threads = Runtime.getRuntime().availableProcessors();
        Scene scene = new Scene();
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--render" -> out = args[++i];
                    case "--benchmark" -> bench = true;
                    case "--width" -> width = Integer.parseInt(args[++i]);
                    case "--height" -> height = Integer.parseInt(args[++i]);
                    case "--ss" -> ss = Integer.parseInt(args[++i]);
                    case "--threads" -> threads = Integer.parseInt(args[++i]);
                    case "--preset" -> {
                        String name = args[++i];
                        FractalPreset p = FractalPreset.find(name);
                        if (p == null) throw new IllegalArgumentException("Unknown preset: " + name);
                        p.applyTo(scene);
                    }
                    case "--power" -> scene.fractal.power = Double.parseDouble(args[++i]);
                    case "--iterations" -> scene.fractal.maxIterations = Integer.parseInt(args[++i]);
                    case "--palette" -> scene.color.palette = Palette.valueOf(args[++i].toUpperCase(Locale.ROOT).replace(' ', '_'));
                    case "--lighting" -> LightingPreset.valueOf(args[++i].toUpperCase(Locale.ROOT).replace(' ', '_')).apply(scene.lighting);
                    case "--set" -> {
                        String kv = args[++i];
                        int eq = kv.indexOf('=');
                        if (eq <= 0) throw new IllegalArgumentException("--set expects key=value");
                        java.util.Properties props = new java.util.Properties();
                        props.setProperty(kv.substring(0, eq), kv.substring(eq + 1));
                        com.mandelbulb.util.SettingsCodec.read(scene, "", props);
                    }
                    case "--help" -> {
                        printHelp();
                        return 0;
                    }
                    default -> throw new IllegalArgumentException("Unknown option " + a);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            printHelp();
            return 2;
        }
        scene.validate();
        try (RenderEngine engine = new RenderEngine(threads)) {
            if (bench) {
                for (Benchmark.Config cfg : Benchmark.standardSuite()) {
                    Benchmark.Result r = Benchmark.run(engine, scene, cfg, true, f -> { }, () -> false);
                    System.out.println(r.format());
                }
            }
            if (out != null) {
                File file = new File(out);
                ImageExporter.Format format = ImageExporter.formatFor(file);
                final int[] lastPercent = {-1};
                RenderResult r = ImageExporter.renderAndSave(engine, scene, width, height, ss, file, format, f -> {
                    int p = (int) (f * 100);
                    synchronized (lastPercent) {
                        if (p / 10 > lastPercent[0] / 10) {
                            lastPercent[0] = p;
                            System.out.print("\rRendering... " + p + "%");
                        }
                    }
                }, () -> false);
                System.out.printf(Locale.ROOT, "%nWrote %s (%d x %d) in %.2f s, %,d DE evaluations, avg %.1f steps%n",
                        file, r.width(), r.height(), r.stats().seconds(), r.stats().deEvaluations(), r.stats().averageSteps());
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Render failed: " + e);
            return 1;
        }
    }

    private static void printHelp() {
        System.out.println("""
                Mandelbulb Explorer - headless mode
                  --render FILE         render to FILE (.png or .jpg)
                  --benchmark           run the standard benchmark suite
                  --width N --height N  output size (default 1280x720)
                  --ss N                supersampling 1..4
                  --preset NAME         e.g. "Classic Mandelbulb", "Deep Zoom", "Experimental"
                  --power X             fractal power (2..20)
                  --iterations N        max iterations
                  --palette NAME        e.g. GOLD, PLASMA, ELECTRIC_BLUE
                  --lighting NAME       STUDIO, CINEMATIC, SOFT, HIGH_CONTRAST, SPACE, NEON, SUNSET
                  --threads N           worker threads
                  --set KEY=VALUE       any scene setting, e.g. color.mode=ORBIT_TRAP,
                                        render.aoStrength=1.5, lighting.key.azimuth=120
                """);
    }
}
