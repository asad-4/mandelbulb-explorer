package com.mandelbulb.benchmark;

import com.mandelbulb.rendering.RenderBackend;
import com.mandelbulb.rendering.RenderResult;
import com.mandelbulb.rendering.RenderStats;
import com.mandelbulb.scene.Scene;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Deterministic render benchmarks.
 *
 * <p>A benchmark renders a fixed configuration without progressive passes and reports the engine
 * counters (rays, DE evaluations, march steps) together with wall time and process CPU
 * utilisation. A short warm-up render first lets the JIT compile the hot path so results are
 * comparable between runs.
 */
public final class Benchmark {

    /** A named configuration: resolution, supersampling and a tweak applied to a scene copy. */
    public record Config(String name, int width, int height, int supersampling, Consumer<Scene> tweak) {
        public static Config current(int width, int height, int supersampling) {
            return new Config("Current settings", width, height, supersampling, s -> { });
        }
    }

    public record Result(String name, String time, RenderStats stats, double cpuUtilization, String settingsSummary) {

        public String format() {
            RenderStats s = stats;
            return String.format(Locale.ROOT,
                    "%s  [%s]%n  Resolution:      %d × %d  (%d×%d SS)%n  Render time:     %.2f s%n"
                            + "  Pixels:          %,d%n  Rays cast:       %,d primary, %,d shadow%n"
                            + "  DE evaluations:  %,d  (%.1f M/s)%n  Average steps:   %.1f%n  Maximum steps:   %d%n"
                            + "  Throughput:      %.2f M rays/s%n  CPU utilisation: %s  (%d threads)%n  Settings:        %s%n",
                    name, time, s.width(), s.height(), s.supersampling(), s.supersampling(), s.seconds(),
                    s.pixels(), s.primaryRays(), s.shadowRays(), s.deEvaluations(), s.deEvaluationsPerSecond() / 1e6,
                    s.averageSteps(), s.maxMarchSteps(), s.raysPerSecond() / 1e6,
                    cpuUtilization < 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", cpuUtilization * 100),
                    s.threads(), settingsSummary);
        }
    }

    private Benchmark() {
    }

    public static List<Config> standardSuite() {
        return List.of(
                new Config("Fast (no AO, no shadows)", 640, 480, 1, s -> {
                    s.render.aoEnabled = false;
                    s.render.shadowsEnabled = false;
                    s.render.normalMethod = com.mandelbulb.rendering.NormalMethod.TETRAHEDRAL;
                }),
                new Config("Balanced (defaults)", 640, 480, 1, s -> { }),
                new Config("Quality (2× SS, detail 2)", 640, 480, 2, s -> {
                    s.render.detail = 2;
                    s.render.aoSamples = 8;
                    s.render.shadowSteps = 128;
                }),
                new Config("Full HD (defaults)", 1920, 1080, 1, s -> { }));
    }

    public static Result run(RenderBackend backend, Scene base, Config config, boolean warmUp,
                             DoubleConsumer progress, BooleanSupplier cancelled) {
        Scene scene = base.copy();
        config.tweak().accept(scene);
        scene.validate();
        if (warmUp) {
            backend.render(scene, 160, 120, 1, f -> { }, cancelled);
        }
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        long cpuBefore = processCpuTime(os);
        long wallBefore = System.nanoTime();
        RenderResult r = backend.render(scene, config.width(), config.height(), config.supersampling(), progress, cancelled);
        long wall = System.nanoTime() - wallBefore;
        long cpuAfter = processCpuTime(os);
        double cpu = cpuBefore < 0 || cpuAfter < 0 ? -1
                : (cpuAfter - cpuBefore) / (double) wall / Runtime.getRuntime().availableProcessors();
        String summary = String.format(Locale.ROOT, "power %.2f, %d iterations, %d max steps, detail %.2f, AO %s, shadows %s",
                scene.fractal.power, scene.fractal.maxIterations, scene.render.maxSteps, scene.render.detail,
                scene.render.aoEnabled ? scene.render.aoSamples + " samples" : "off",
                scene.render.shadowsEnabled ? scene.render.shadowSteps + " steps" : "off");
        return new Result(config.name(), LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                r.stats(), Math.min(1.0, cpu), summary);
    }

    private static long processCpuTime(OperatingSystemMXBean os) {
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getProcessCpuTime();
        }
        return -1;
    }
}
