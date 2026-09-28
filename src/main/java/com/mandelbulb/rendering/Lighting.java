package com.mandelbulb.rendering;

import com.mandelbulb.math.Color3;
import com.mandelbulb.math.Vector3;
import com.mandelbulb.scene.Light;
import com.mandelbulb.scene.LightingSettings;

/**
 * Surface shading and environment.
 *
 * <p>Shading is physically motivated and done in linear RGB:
 * <ul>
 *   <li>Diffuse: Lambert, albedo / pi, weighted by (1 - F) so energy reflected specularly is not
 *       also diffused.</li>
 *   <li>Specular: Cook-Torrance microfacet BRDF with the GGX normal distribution, Schlick-GGX
 *       geometry term and Schlick's Fresnel approximation (F0 = 0.04, a typical dielectric).
 *       Roughness controls highlight size.</li>
 *   <li>Ambient: hemispherical sky/ground light by world-space normal height, times AO.</li>
 *   <li>Reflection: the environment in the mirror direction, weighted by Fresnel.</li>
 *   <li>Rim: extra light at grazing view angles, emphasising the fractal's silhouette.</li>
 * </ul>
 * Lights and the view are expressed in the fractal's object space (precomputed once per frame),
 * so the renderer never transforms anything inside the march loop. Only environment lookups are
 * rotated back to world space.
 */
public final class Lighting {

    private static final double INV_PI = 1.0 / Math.PI;
    private static final double F0 = 0.04;

    private final ShadowRenderer shadows;
    private final boolean shadowsEnabled;
    private final double shadowStrength;
    /** Object -&gt; world rotation, row-major 3x3. */
    private final double[] rot;

    // Directional lights: object-space direction towards the light and linear radiance.
    private final boolean keyOn, keyShadows;
    private final double kx, ky, kz, kr, kg, kb;
    private final boolean fillOn;
    private final double fx, fy, fz, fr, fg, fb;
    // Point light: object-space position and radiance.
    private final boolean pointOn, pointShadows;
    private final double px, py, pz, pr, pg, pb;

    private final double skyR, skyG, skyB, groundR, groundG, groundB;
    private final double specular, alpha2, geomK, fresnel, reflectivity;
    private final double rimStrength, rimPower, rimR, rimG, rimB;

    // Environment (world space).
    private final double topR, topG, topB, horR, horG, horB, botR, botG, botB;
    private final boolean stars;
    private final double sunGlow, sunX, sunY, sunZ, sunR, sunG, sunB;
    private final double glowStrength, glowR, glowG, glowB;
    private final double fogDensity;

    public Lighting(LightingSettings l, RenderSettings rs, double[] objectToWorld, ShadowRenderer shadows) {
        this.shadows = shadows;
        this.shadowsEnabled = rs.shadowsEnabled;
        this.shadowStrength = rs.shadowStrength;
        this.rot = objectToWorld.clone();

        Vector3 kd = toObject(l.key.direction());
        keyOn = l.key.enabled && l.key.intensity > 0;
        keyShadows = l.key.castShadows;
        kx = kd.x(); ky = kd.y(); kz = kd.z();
        Color3 kc = radiance(l.key);
        kr = kc.r(); kg = kc.g(); kb = kc.b();

        Vector3 fd = toObject(l.fill.direction());
        fillOn = l.fill.enabled && l.fill.intensity > 0;
        fx = fd.x(); fy = fd.y(); fz = fd.z();
        Color3 fc = radiance(l.fill);
        fr = fc.r(); fg = fc.g(); fb = fc.b();

        Vector3 pp = toObject(l.point.position());
        pointOn = l.point.enabled && l.point.intensity > 0;
        pointShadows = l.point.castShadows;
        px = pp.x(); py = pp.y(); pz = pp.z();
        Color3 pc = radiance(l.point);
        pr = pc.r(); pg = pc.g(); pb = pc.b();

        Color3 sky = l.skyColor.toLinear().scale(l.ambientIntensity);
        Color3 ground = l.groundColor.toLinear().scale(l.ambientIntensity);
        skyR = sky.r(); skyG = sky.g(); skyB = sky.b();
        groundR = ground.r(); groundG = ground.g(); groundB = ground.b();

        specular = l.specularStrength;
        double a = l.roughness * l.roughness;
        alpha2 = a * a;
        geomK = (l.roughness + 1) * (l.roughness + 1) / 8.0;
        fresnel = l.fresnelStrength;
        reflectivity = l.reflectivity;
        rimStrength = l.rimStrength;
        rimPower = l.rimPower;
        Color3 rc = l.rimColor.toLinear();
        rimR = rc.r(); rimG = rc.g(); rimB = rc.b();

        Color3 top = l.backgroundTop.toLinear(), hor = l.backgroundHorizon.toLinear(), bot = l.backgroundBottom.toLinear();
        topR = top.r(); topG = top.g(); topB = top.b();
        horR = hor.r(); horG = hor.g(); horB = hor.b();
        botR = bot.r(); botG = bot.g(); botB = bot.b();
        stars = l.stars;
        sunGlow = l.key.enabled ? l.sunGlow : 0;
        Vector3 sd = l.key.direction();
        sunX = sd.x(); sunY = sd.y(); sunZ = sd.z();
        sunR = kc.r(); sunG = kc.g(); sunB = kc.b();
        glowStrength = l.glowStrength;
        Color3 gc = l.glowColor.toLinear();
        glowR = gc.r(); glowG = gc.g(); glowB = gc.b();
        fogDensity = l.fogDensity;
    }

    private static Color3 radiance(Light light) {
        return light.color.toLinear().scale(light.intensity);
    }

    private Vector3 toObject(Vector3 w) {
        // Inverse of a rotation is its transpose.
        return new Vector3(
                rot[0] * w.x() + rot[3] * w.y() + rot[6] * w.z(),
                rot[1] * w.x() + rot[4] * w.y() + rot[7] * w.z(),
                rot[2] * w.x() + rot[5] * w.y() + rot[8] * w.z());
    }

    /**
     * Shades a surface point. Inputs: hit in {@code ctx.hit}, unit normal in ctx.nx/ny/nz, albedo
     * in ctx.ar/ag/ab, (vx,vy,vz) = unit vector towards the viewer. Output: linear RGB in
     * ctx.r/g/b.
     */
    public void shade(RenderContext ctx, double vx, double vy, double vz, double ao) {
        final HitResult hit = ctx.hit;
        final double nx = ctx.nx, ny = ctx.ny, nz = ctx.nz;
        final double ar = ctx.ar, ag = ctx.ag, ab = ctx.ab;
        double ndv = nx * vx + ny * vy + nz * vz;
        if (ndv < 1e-4) ndv = 1e-4;

        // Offset the shadow-ray origin along the normal so it cannot re-hit its own surface.
        final double eps = hit.epsilon;
        final double sox = hit.x + nx * eps * 3, soy = hit.y + ny * eps * 3, soz = hit.z + nz * eps * 3;

        double r = 0, g = 0, b = 0;

        if (keyOn) {
            double vis = 1;
            if (shadowsEnabled && keyShadows && (nx * kx + ny * ky + nz * kz) > 0) {
                vis = 1 - shadowStrength * (1 - shadows.visibility(sox, soy, soz, kx, ky, kz, Double.MAX_VALUE, eps, ctx));
            }
            double w = brdf(nx, ny, nz, vx, vy, vz, kx, ky, kz, ndv, ar, ag, ab, ctx) * vis;
            r += ctx.r * kr * w;
            g += ctx.g * kg * w;
            b += ctx.b * kb * w;
        }
        if (fillOn) {
            double w = brdf(nx, ny, nz, vx, vy, vz, fx, fy, fz, ndv, ar, ag, ab, ctx);
            r += ctx.r * fr * w;
            g += ctx.g * fg * w;
            b += ctx.b * fb * w;
        }
        if (pointOn) {
            double lx = px - hit.x, ly = py - hit.y, lz = pz - hit.z;
            double dist2 = lx * lx + ly * ly + lz * lz;
            double dist = Math.sqrt(dist2);
            lx /= dist; ly /= dist; lz /= dist;
            // Inverse-square falloff, softened near the light to avoid a singularity.
            double atten = 1.0 / (1.0 + dist2);
            double vis = 1;
            if (shadowsEnabled && pointShadows && (nx * lx + ny * ly + nz * lz) > 0) {
                vis = 1 - shadowStrength * (1 - shadows.visibility(sox, soy, soz, lx, ly, lz, dist, eps, ctx));
            }
            double w = brdf(nx, ny, nz, vx, vy, vz, lx, ly, lz, ndv, ar, ag, ab, ctx) * vis * atten;
            r += ctx.r * pr * w;
            g += ctx.g * pg * w;
            b += ctx.b * pb * w;
        }

        // World-space normal height for the sky/ground hemisphere and reflection lookups.
        double wnz = rot[6] * nx + rot[7] * ny + rot[8] * nz;
        double skyMix = 0.5 + 0.5 * wnz;
        r += ar * (groundR + (skyR - groundR) * skyMix) * ao;
        g += ag * (groundG + (skyG - groundG) * skyMix) * ao;
        b += ab * (groundB + (skyB - groundB) * skyMix) * ao;

        double fv = 1 - ndv;
        double fv5 = fv * fv * fv * fv * fv;
        double fresnelView = F0 + fresnel * (1 - F0) * fv5;
        if (reflectivity > 0) {
            // Mirror direction R = -V + 2 (N.V) N, looked up in the world-space environment.
            double rx = -vx + 2 * ndv * nx, ry = -vy + 2 * ndv * ny, rz = -vz + 2 * ndv * nz;
            double wx = rot[0] * rx + rot[1] * ry + rot[2] * rz;
            double wy = rot[3] * rx + rot[4] * ry + rot[5] * rz;
            double wz = rot[6] * rx + rot[7] * ry + rot[8] * rz;
            double kr0 = ctx.r, kg0 = ctx.g, kb0 = ctx.b;
            sky(wx, wy, wz, false, ctx);
            double w = reflectivity * Math.min(1, fresnelView * 4) * ao;
            r += ctx.r * w;
            g += ctx.g * w;
            b += ctx.b * w;
            ctx.r = kr0; ctx.g = kg0; ctx.b = kb0;
        }
        if (rimStrength > 0) {
            double rim = rimStrength * Math.pow(fv, rimPower) * ao;
            r += rimR * rim;
            g += rimG * rim;
            b += rimB * rim;
        }

        if (fogDensity > 0) {
            double f = 1 - Math.exp(-hit.t * fogDensity);
            r += (horR - r) * f;
            g += (horG - g) * f;
            b += (horB - b) * f;
        }
        ctx.r = r;
        ctx.g = g;
        ctx.b = b;
    }

    /**
     * Evaluates diffuse + specular BRDF times N.L for one light. Writes the (unlit) colour
     * factor into ctx.r/g/b and returns the scalar weight; the caller multiplies by radiance.
     */
    private double brdf(double nx, double ny, double nz, double vx, double vy, double vz,
                        double lx, double ly, double lz, double ndv,
                        double ar, double ag, double ab, RenderContext ctx) {
        double ndl = nx * lx + ny * ly + nz * lz;
        if (ndl <= 0) {
            ctx.r = ctx.g = ctx.b = 0;
            return 0;
        }
        double hx = lx + vx, hy = ly + vy, hz = lz + vz;
        double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
        double spec = 0, f = F0;
        if (hl > 1e-12) {
            hx /= hl; hy /= hl; hz /= hl;
            double ndh = Math.max(0, nx * hx + ny * hy + nz * hz);
            double vdh = Math.max(0, vx * hx + vy * hy + vz * hz);
            // GGX / Trowbridge-Reitz normal distribution.
            double den = ndh * ndh * (alpha2 - 1) + 1;
            double dTerm = alpha2 / (Math.PI * den * den);
            // Smith geometry term with Schlick-GGX approximation.
            double gTerm = (ndl / (ndl * (1 - geomK) + geomK)) * (ndv / (ndv * (1 - geomK) + geomK));
            double fv = 1 - vdh;
            f = F0 + fresnel * (1 - F0) * fv * fv * fv * fv * fv;
            spec = specular * dTerm * gTerm * f / (4 * ndl * ndv + 1e-4);
            if (spec > 50) spec = 50;
        }
        double kd = (1 - f) * INV_PI;
        ctx.r = ar * kd + spec;
        ctx.g = ag * kd + spec;
        ctx.b = ab * kd + spec;
        return ndl;
    }

    /**
     * Environment colour for a world-space direction into ctx.r/g/b (linear). Vertical gradient,
     * a glow around the key light (the "sun") and optionally procedural stars.
     */
    public void sky(double dx, double dy, double dz, boolean withStars, RenderContext ctx) {
        double r, g, b;
        if (dz >= 0) {
            double t = Math.sqrt(dz);
            r = horR + (topR - horR) * t;
            g = horG + (topG - horG) * t;
            b = horB + (topB - horB) * t;
        } else {
            double t = Math.sqrt(-dz);
            r = horR + (botR - horR) * t;
            g = horG + (botG - horG) * t;
            b = horB + (botB - horB) * t;
        }
        if (sunGlow > 0) {
            double s = dx * sunX + dy * sunY + dz * sunZ;
            if (s > 0) {
                double s2 = s * s, s4 = s2 * s2, s8 = s4 * s4, s16 = s8 * s8, s64 = s16 * s16 * s16 * s16;
                double glow = sunGlow * (0.04 * s8 + 0.25 * s64 * s64);
                r += sunR * glow;
                g += sunG * glow;
                b += sunB * glow;
            }
        }
        if (withStars && stars) {
            double star = starField(dx, dy, dz);
            r += star;
            g += star;
            b += star * 1.1;
        }
        ctx.r = r;
        ctx.g = g;
        ctx.b = b;
    }

    /** Hash-based star field: sparse bright cells on a direction grid. */
    private static double starField(double dx, double dy, double dz) {
        final double scale = 380;
        long ix = (long) Math.floor(dx * scale), iy = (long) Math.floor(dy * scale), iz = (long) Math.floor(dz * scale);
        long h = ix * 73856093L ^ iy * 19349663L ^ iz * 83492791L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        h ^= (h >>> 15);
        double u = (h & 0xFFFFFF) / (double) 0xFFFFFF;
        if (u < 0.9965) {
            return 0;
        }
        double brightness = (u - 0.9965) / 0.0035;
        return brightness * brightness * 1.5;
    }

    /** Halo colour for rays that missed but passed close to the surface. */
    public void addGlow(double minAngularDistance, double fade, RenderContext ctx) {
        if (glowStrength <= 0 || fade <= 0 || minAngularDistance == Double.MAX_VALUE) return;
        double g = glowStrength * fade * Math.exp(-minAngularDistance * 60.0);
        ctx.r += glowR * g;
        ctx.g += glowG * g;
        ctx.b += glowB * g;
    }
}
