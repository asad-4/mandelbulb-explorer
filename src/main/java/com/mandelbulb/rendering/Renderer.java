package com.mandelbulb.rendering;

import com.mandelbulb.camera.Camera;
import com.mandelbulb.fractal.Mandelbulb;
import com.mandelbulb.math.Color3;
import com.mandelbulb.math.MathUtils;
import com.mandelbulb.math.Matrix4;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.scene.Scene;

/**
 * The per-pixel rendering kernel: camera ray -&gt; march -&gt; normal -&gt; colour -&gt; lighting -&gt;
 * tone mapping.
 *
 * <p>A Renderer is built from a scene snapshot for one frame. The constructor does all the
 * per-frame work (camera basis, object rotation, light transforms, palette tables) so that the
 * per-pixel code only touches final primitive fields. It is immutable and shared by every render
 * thread; each thread supplies its own {@link RenderContext}.
 *
 * <p>Everything is computed in the fractal's object space: the eye and camera basis are rotated
 * into object space once here, which makes object rotation free inside the march loop.
 */
public final class Renderer {

    private static final int GAMMA_LUT_SIZE = 4096;
    private static final double[] GAMMA_LUT = buildGammaLut();

    private final int width;
    private final int height;
    private final Scene scene;
    private final Mandelbulb fractal;
    private final RayMarcher marcher;
    private final SurfaceNormal normals;
    private final AmbientOcclusion occlusion;
    private final ColorMapper colors;
    private final Lighting lighting;
    private final boolean aoEnabled;
    private final int maxSteps;
    private final double exposure;
    private final double near;
    private final double far;
    private final double pixelAngle;
    private final double boundRadius2;
    private final double glowFadeStart2;
    private final double[] rot = new double[9];

    // Camera in object space.
    private final double eyeX, eyeY, eyeZ;
    private final double fwdX, fwdY, fwdZ, rightX, rightY, rightZ, upX, upY, upZ;
    private final double tanHalfFov, aspect;

    public Renderer(Scene sceneSnapshot, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid image size " + width + "x" + height);
        }
        Scene s = sceneSnapshot.copy();
        s.validate();
        this.scene = s;
        this.width = width;
        this.height = height;
        RenderSettings rs = s.render;

        this.fractal = new Mandelbulb(s.fractal);
        Matrix4 objectToWorld = objectRotation(s);
        objectToWorld.copyRotation(rot);

        Camera cam = s.camera;
        this.tanHalfFov = Math.tan(Math.toRadians(cam.fovDegrees()) * 0.5);
        this.aspect = (double) width / height;
        // Angular size of one pixel; the hit threshold is t * pixelAngle / detail.
        this.pixelAngle = 2 * tanHalfFov / height / rs.detail;

        Vector3 eye = toObject(cam.position());
        Vector3 f = toObject(cam.forward()), r = toObject(cam.right()), u = toObject(cam.up());
        eyeX = eye.x(); eyeY = eye.y(); eyeZ = eye.z();
        fwdX = f.x(); fwdY = f.y(); fwdZ = f.z();
        rightX = r.x(); rightY = r.y(); rightZ = r.z();
        upX = u.x(); upY = u.y(); upZ = u.z();
        this.near = Math.max(0, cam.near());
        this.far = Math.min(cam.far(), rs.maxDistance);

        this.maxSteps = rs.maxSteps;
        this.marcher = new RayMarcher(fractal, rs.maxSteps, rs.stepScale, rs.relaxation, rs.hitEpsilon, rs.minStep, pixelAngle);
        // Normals are sampled at half a pixel footprint (see SurfaceNormal).
        this.normals = new SurfaceNormal(fractal, rs.normalMethod, rs.normalEpsilon);
        this.aoEnabled = rs.aoEnabled && rs.aoStrength > 0;
        this.occlusion = new AmbientOcclusion(fractal, rs.aoSamples, rs.aoRadius, rs.aoStrength);
        ShadowRenderer shadows = new ShadowRenderer(fractal, marcher, rs.shadowSteps, rs.shadowSoftness, rs.stepScale);
        this.lighting = new Lighting(s.lighting, rs, rot, shadows);
        this.colors = new ColorMapper(s.color, s.fractal.maxIterations, fractal.boundingRadius(), rs.maxSteps);
        this.exposure = s.lighting.exposure;
        double br = fractal.boundingRadius();
        this.boundRadius2 = br * br;
        this.glowFadeStart2 = 0.55 * br * br;
    }

    /** Object -&gt; world rotation: rotate about X, then about the vertical Z axis. */
    public static Matrix4 objectRotation(Scene s) {
        return Matrix4.rotationZ(Math.toRadians(s.fractal.rotationZ))
                .multiply(Matrix4.rotationX(Math.toRadians(s.fractal.rotationX)));
    }

    private Vector3 toObject(Vector3 w) {
        return new Vector3(
                rot[0] * w.x() + rot[3] * w.y() + rot[6] * w.z(),
                rot[1] * w.x() + rot[4] * w.y() + rot[7] * w.z(),
                rot[2] * w.x() + rot[5] * w.y() + rot[8] * w.z());
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Mandelbulb fractal() {
        return fractal;
    }

    /** Sets up the primary ray through image position (px, py) (pixel units, y down). */
    private void primaryRay(double px, double py, Ray ray) {
        double sx = (2 * px / width - 1) * aspect * tanHalfFov;
        double sy = (1 - 2 * py / height) * tanHalfFov;
        ray.set(eyeX, eyeY, eyeZ,
                fwdX + rightX * sx + upX * sy,
                fwdY + rightY * sx + upY * sy,
                fwdZ + rightZ * sx + upZ * sy);
        ray.normalizeDirection();
    }

    /**
     * Renders one sample at image position (px, py) and leaves the display-referred colour
     * (tone-mapped, sRGB-encoded, [0,1]) in ctx.r/g/b.
     *
     * @param epsScale hit-threshold multiplier (the block size for coarse preview passes)
     */
    public void renderSample(double px, double py, double epsScale, RenderContext ctx) {
        Ray ray = ctx.ray;
        primaryRay(px, py, ray);
        HitResult hit = ctx.hit;
        marcher.march(ray, near, far, epsScale, hit, ctx);

        if (!hit.hit) {
            // Background: environment lookup in world space plus silhouette glow.
            double wx = rot[0] * ray.dx + rot[1] * ray.dy + rot[2] * ray.dz;
            double wy = rot[3] * ray.dx + rot[4] * ray.dy + rot[5] * ray.dz;
            double wz = rot[6] * ray.dx + rot[7] * ray.dy + rot[8] * ray.dz;
            lighting.sky(wx, wy, wz, true, ctx);
            // The DE underestimates distance away from the surface (strongly so for high
            // powers), so fade the glow out before the bounding sphere where marching stops;
            // otherwise its edge would show as a hard circle.
            double b = ray.ox * ray.dx + ray.oy * ray.dy + ray.oz * ray.dz;
            double closest2 = ray.ox * ray.ox + ray.oy * ray.oy + ray.oz * ray.oz - b * b;
            double fade = 1 - MathUtils.smoothstep(glowFadeStart2, boundRadius2, closest2);
            lighting.addGlow(hit.minAngularDistance, fade, ctx);
        } else if (hit.inside) {
            ctx.r = ctx.g = ctx.b = 0.002;
        } else {
            shadeHit(ray, hit, ctx);
        }
        toneMap(ctx);
    }

    private void shadeHit(Ray ray, HitResult hit, RenderContext ctx) {
        double eps = hit.epsilon;
        normals.compute(hit.x, hit.y, hit.z, eps * 0.5, hit.distance, ctx);
        double nx = ctx.nx, ny = ctx.ny, nz = ctx.nz;
        // A normal facing away from the viewer is a numerical artefact at silhouettes; flip it.
        if (nx * ray.dx + ny * ray.dy + nz * ray.dz > 0) {
            double d = nx * ray.dx + ny * ray.dy + nz * ray.dz;
            nx -= 2 * d * ray.dx;
            ny -= 2 * d * ray.dy;
            nz -= 2 * d * ray.dz;
            ctx.nx = nx; ctx.ny = ny; ctx.nz = nz;
        }

        fractal.distance(hit.x, hit.y, hit.z, ctx.trap);
        ctx.deEvaluations++;
        double curvature = ctx.laplacian * hit.t;
        colors.albedo(ctx.trap, hit.x, hit.y, hit.z, nx, ny, nz, curvature, hit.steps, ctx);

        double ao = aoEnabled ? occlusion.compute(hit.x, hit.y, hit.z, nx, ny, nz, hit.t, eps, ctx) : 1.0;
        lighting.shade(ctx, -ray.dx, -ray.dy, -ray.dz, ao);
    }

    /**
     * Exposure, ACES filmic tone curve (Narkowicz fit) and sRGB encoding. The filmic curve
     * compresses bright highlights smoothly instead of clipping them.
     */
    private void toneMap(RenderContext ctx) {
        ctx.r = gamma(aces(ctx.r * exposure));
        ctx.g = gamma(aces(ctx.g * exposure));
        ctx.b = gamma(aces(ctx.b * exposure));
    }

    private static double aces(double x) {
        if (!(x > 0)) return 0; // also maps NaN to black
        double v = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
        return v > 1 ? 1 : v;
    }

    private static double gamma(double linear) {
        double pos = linear * (GAMMA_LUT_SIZE - 1);
        int i = (int) pos;
        if (i >= GAMMA_LUT_SIZE - 1) return GAMMA_LUT[GAMMA_LUT_SIZE - 1];
        double w = pos - i;
        return GAMMA_LUT[i] + (GAMMA_LUT[i + 1] - GAMMA_LUT[i]) * w;
    }

    private static double[] buildGammaLut() {
        double[] lut = new double[GAMMA_LUT_SIZE];
        for (int i = 0; i < GAMMA_LUT_SIZE; i++) {
            lut[i] = Color3.linearToSrgb(i / (double) (GAMMA_LUT_SIZE - 1));
        }
        return lut;
    }

    /**
     * Renders pixel (x, y) with {@code ss x ss} stratified samples and returns packed ARGB.
     * Samples are averaged after tone mapping, which keeps anti-aliased edges against bright
     * highlights smooth.
     */
    public int renderPixel(int x, int y, int ss, double epsScale, RenderContext ctx) {
        ctx.pixels++;
        if (ss <= 1) {
            renderSample(x + 0.5, y + 0.5, epsScale, ctx);
            return pack(ctx.r, ctx.g, ctx.b);
        }
        double r = 0, g = 0, b = 0;
        double inv = 1.0 / ss;
        for (int sy = 0; sy < ss; sy++) {
            for (int sx = 0; sx < ss; sx++) {
                renderSample(x + (sx + 0.5) * inv, y + (sy + 0.5) * inv, epsScale, ctx);
                r += ctx.r;
                g += ctx.g;
                b += ctx.b;
            }
        }
        double n = inv * inv;
        return pack(r * n, g * n, b * n);
    }

    /** One sample at the centre of a {@code block x block} preview block with origin (x, y). */
    public int renderBlock(int x, int y, int block, double epsScale, RenderContext ctx) {
        ctx.pixels++;
        renderSample(x + block * 0.5, y + block * 0.5, epsScale, ctx);
        return pack(ctx.r, ctx.g, ctx.b);
    }

    static int pack(double r, double g, double b) {
        int ir = (int) (r * 255 + 0.5), ig = (int) (g * 255 + 0.5), ib = (int) (b * 255 + 0.5);
        ir = ir < 0 ? 0 : (ir > 255 ? 255 : ir);
        ig = ig < 0 ? 0 : (ig > 255 ? 255 : ig);
        ib = ib < 0 ? 0 : (ib > 255 ? 255 : ib);
        return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
    }

    /**
     * Casts a single ray through image position (px, py) and returns the world-space hit point,
     * or null on a miss. Used for click-to-focus and the mathematics panel.
     */
    public Vector3 pick(double px, double py) {
        RenderContext ctx = new RenderContext();
        primaryRay(px, py, ctx.ray);
        marcher.march(ctx.ray, near, far, 1.0, ctx.hit, ctx);
        if (!ctx.hit.hit || ctx.hit.inside) {
            return null;
        }
        HitResult h = ctx.hit;
        return new Vector3(
                rot[0] * h.x + rot[1] * h.y + rot[2] * h.z,
                rot[3] * h.x + rot[4] * h.y + rot[5] * h.z,
                rot[6] * h.x + rot[7] * h.y + rot[8] * h.z);
    }

    /** Object-space coordinates of a world point (for evaluating the DE at arbitrary points). */
    public Vector3 worldToObject(Vector3 w) {
        return toObject(w);
    }

    /** Distance estimate at a world-space point. */
    public double distanceAt(Vector3 world) {
        Vector3 p = toObject(world);
        return fractal.distance(p.x(), p.y(), p.z());
    }

    public Scene scene() {
        return scene;
    }

    public int maxSteps() {
        return maxSteps;
    }
}
