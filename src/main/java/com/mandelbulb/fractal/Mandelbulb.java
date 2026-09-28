package com.mandelbulb.fractal;

import com.mandelbulb.math.MathUtils;

/**
 * The Mandelbulb (White &amp; Nylander) and its distance estimator.
 *
 * <h2>Iteration</h2>
 * The Mandelbulb generalises the Mandelbrot iteration z -&gt; z^2 + c to three dimensions by
 * defining a "power" of a 3D point through spherical coordinates. For v = (x, y, z):
 * <pre>
 *   r     = sqrt(x^2 + y^2 + z^2)
 *   theta = acos(z / r)          polar angle, measured from +Z, in [0, pi]
 *   phi   = atan2(y, x)          azimuth in (-pi, pi]
 *
 *   v^n   = r^n * ( sin(n theta) cos(n phi),  sin(n theta) sin(n phi),  cos(n theta) )
 * </pre>
 * i.e. raise the length to the n-th power and multiply both angles by n - exactly what complex
 * exponentiation does to a 2D number. The set is all c for which v_{k+1} = v_k^n + c (with
 * v_0 = c) stays bounded; in Julia mode c is a fixed constant and v_0 is the sample point.
 *
 * <h2>Distance estimation</h2>
 * Ray marching needs, for every point p, a lower bound on the distance to the surface. For
 * escape-time fractals this comes from the Hubbard-Douady potential G(c) = lim log|v_k| / n^k.
 * Near the set, G ≈ 0 and |grad G| is large; the distance to the set is approximately
 * <pre>
 *   d ≈ G / |grad G| = 0.5 * log(r) * r / dr        (with the conventional factor 0.5)
 * </pre>
 * where r = |v_k| after escape and dr = |dv_k/dc| is the running derivative. Differentiating
 * v_{k+1} = v_k^n + c with the chain rule and taking magnitudes (the power map scales lengths by
 * n r^(n-1) locally) gives the scalar recurrence
 * <pre>
 *   dr_{k+1} = n * r_k^(n-1) * dr_k + 1           (Mandelbrot mode, dr_0 = 1)
 *   dr_{k+1} = n * r_k^(n-1) * dr_k               (Julia mode: c does not depend on p)
 * </pre>
 * The estimate is not an exact Euclidean distance (the power map is not conformal in 3D), so the
 * renderer additionally scales the step by a user-controlled factor &lt;= 1. It is also only
 * meaningful outside the set: interior points whose bounded orbit keeps |z| &gt; 1 report a
 * positive value (about 0.5% of random samples near the bulb). Rays never reach those points
 * because they stop at the surface first, so this is harmless for rendering.
 *
 * <h2>Performance</h2>
 * For integer powers the trigonometric form is replaced by an algebraically identical one:
 * cos(theta) = z/r, sin(theta) = rho/r and cos(phi), sin(phi) = x/rho, y/rho (rho = |(x,y)|),
 * and by de Moivre (cos a + i sin a)^n = cos(na) + i sin(na). Raising those unit complex numbers
 * to the n-th power by repeated squaring needs no acos/atan2/sin/cos/pow at all and is several
 * times faster. Non-integer powers use the direct trigonometric formula.
 *
 * <p>Instances are immutable and thread-safe; the per-thread {@link OrbitTrap} is passed in.
 */
public final class Mandelbulb implements DistanceEstimator {

    /** Below this radius the spherical angles are undefined; v^n is taken as exactly 0. */
    private static final double TINY = 1e-150;
    /** dr beyond this means the point is numerically on the boundary; stop before overflow. */
    private static final double DR_LIMIT = 1e200;

    private final double power;
    private final boolean integerPower;
    private final int intPower;
    private final int maxIterations;
    private final double bailout;
    private final double logBailout;
    private final double logPower;
    private final boolean julia;
    private final double juliaX, juliaY, juliaZ;
    private final double boundingRadius;

    public Mandelbulb(FractalParameters parameters) {
        FractalParameters p = parameters.copy();
        p.validate();
        this.power = p.power;
        this.intPower = (int) Math.round(power);
        this.integerPower = Math.abs(power - intPower) < 1e-9;
        this.maxIterations = p.maxIterations;
        this.bailout = p.bailout;
        this.logBailout = Math.log(bailout);
        this.logPower = Math.log(power);
        this.julia = p.juliaMode;
        this.juliaX = p.juliaX;
        this.juliaY = p.juliaY;
        this.juliaZ = p.juliaZ;
        this.boundingRadius = computeBoundingRadius();
    }

    public Mandelbulb(double power, int maxIterations, double bailout) {
        this(params(power, maxIterations, bailout));
    }

    private static FractalParameters params(double power, int maxIterations, double bailout) {
        FractalParameters p = new FractalParameters();
        p.power = power;
        p.maxIterations = maxIterations;
        p.bailout = bailout;
        return p;
    }

    public double power() {
        return power;
    }

    public int maxIterations() {
        return maxIterations;
    }

    public double bailout() {
        return bailout;
    }

    public boolean usesIntegerFastPath() {
        return integerPower;
    }

    @Override
    public double boundingRadius() {
        return boundingRadius;
    }

    @Override
    public double distance(double x, double y, double z) {
        return estimate(x, y, z, null);
    }

    @Override
    public double distance(double x, double y, double z, OrbitTrap trap) {
        return estimate(x, y, z, trap);
    }

    /** Number of iterations before escape (maxIterations if the point never escapes). */
    public int escapeIterations(double x, double y, double z) {
        OrbitTrap trap = new OrbitTrap();
        estimate(x, y, z, trap);
        return trap.iterations;
    }

    /** True if the orbit of (x,y,z) stays within the bailout radius for all iterations. */
    public boolean contains(double x, double y, double z) {
        OrbitTrap trap = new OrbitTrap();
        estimate(x, y, z, trap);
        return !trap.escaped;
    }

    private double estimate(final double px, final double py, final double pz, final OrbitTrap trap) {
        final double cx, cy, cz;
        if (julia) {
            cx = juliaX;
            cy = juliaY;
            cz = juliaZ;
        } else {
            cx = px;
            cy = py;
            cz = pz;
        }
        final double n = power;
        final double dcTerm = julia ? 0.0 : 1.0;

        double x = px, y = py, z = pz;
        double dr = 1.0;
        double r = 0.0;
        int i = 0;
        boolean escaped = false;
        if (trap != null) {
            trap.reset();
        }

        for (; i < maxIterations; i++) {
            r = Math.sqrt(x * x + y * y + z * z);
            if (trap != null) {
                trap.accumulate(x, y, z, r);
            }
            if (r > bailout) {
                escaped = true;
                break;
            }

            double xn, yn, zn, rPowNm1;
            if (r < TINY) {
                // v = 0: v^n = 0 and the derivative factor n r^(n-1) = 0 for n >= 2.
                xn = yn = zn = 0;
                rPowNm1 = 0;
            } else if (integerPower) {
                double rho = Math.sqrt(x * x + y * y);
                // cos/sin of theta. theta is in [0, pi] so sin(theta) = rho / r >= 0.
                double ct = z / r;
                double st = rho / r;
                // cos/sin of phi. On the z-axis phi is undefined; atan2(0,0) = 0 convention.
                double cp, sp;
                if (rho > TINY) {
                    cp = x / rho;
                    sp = y / rho;
                } else {
                    cp = 1;
                    sp = 0;
                }
                // (ct + i st)^n = cos(n theta) + i sin(n theta), by binary exponentiation.
                double cnt = 1, snt = 0, bre = ct, bim = st;
                // (cp + i sp)^n = cos(n phi) + i sin(n phi).
                double cnp = 1, snp = 0, pre = cp, pim = sp;
                int e = intPower;
                while (true) {
                    if ((e & 1) != 0) {
                        double t = cnt * bre - snt * bim;
                        snt = cnt * bim + snt * bre;
                        cnt = t;
                        t = cnp * pre - snp * pim;
                        snp = cnp * pim + snp * pre;
                        cnp = t;
                    }
                    e >>= 1;
                    if (e == 0) break;
                    double t = bre * bre - bim * bim;
                    bim = 2 * bre * bim;
                    bre = t;
                    t = pre * pre - pim * pim;
                    pim = 2 * pre * pim;
                    pre = t;
                }
                rPowNm1 = MathUtils.ipow(r, intPower - 1);
                double rn = rPowNm1 * r;
                double s = rn * snt;
                xn = s * cnp;
                yn = s * snp;
                zn = rn * cnt;
            } else {
                double theta = Math.acos(MathUtils.clamp(z / r, -1, 1)) * n;
                double phi = Math.atan2(y, x) * n;
                rPowNm1 = Math.pow(r, n - 1);
                double rn = rPowNm1 * r;
                double s = rn * Math.sin(theta);
                xn = s * Math.cos(phi);
                yn = s * Math.sin(phi);
                zn = rn * Math.cos(theta);
            }

            // Running derivative |dv/dc|: chain rule through v^n (+1 for the "+ c" term).
            dr = n * rPowNm1 * dr + dcTerm;
            x = xn + cx;
            y = yn + cy;
            z = zn + cz;
            if (dr > DR_LIMIT) {
                i++;
                break;
            }
        }
        if (!escaped) {
            r = Math.sqrt(x * x + y * y + z * z);
            escaped = r > bailout;
        }

        if (trap != null) {
            trap.iterations = i;
            trap.finalRadius = r;
            trap.escaped = escaped;
            if (escaped && r > 1.0) {
                // Continuous escape count; log(log r)/log n measures how far past the bailout
                // the orbit jumped, which removes the integer banding.
                double mu = i + 1 - Math.log(Math.log(r) / logBailout) / logPower;
                trap.smoothIteration = MathUtils.clamp(MathUtils.finiteOr(mu, i), 0, maxIterations);
            } else {
                trap.smoothIteration = maxIterations;
            }
        }

        if (r < TINY || !(dr > 0)) {
            return 0.0;
        }
        double d = 0.5 * Math.log(r) * r / dr;
        // NaN / infinity can only arise from overflow at the boundary: treat as "on the surface".
        return Double.isFinite(d) ? d : 0.0;
    }

    /**
     * Radius guaranteed to enclose the set (with margin for the finite-iteration surface).
     *
     * <p>Mandelbrot mode: if |c| &gt; 2^(1/(n-1)) then |v_1| = |c^n + c| &gt;= |c|^n - |c| &gt; |c|
     * and the orbit grows monotonically, so c escapes. Julia mode: |v| &gt; R escapes when
     * R^n - R &gt; |k|; R is found by bisection.
     */
    private double computeBoundingRadius() {
        double n = power;
        double rEscape;
        if (!julia) {
            rEscape = Math.pow(2.0, 1.0 / (n - 1));
        } else {
            double k = Math.sqrt(juliaX * juliaX + juliaY * juliaY + juliaZ * juliaZ);
            double lo = 1.0, hi = 4.0;
            for (int it = 0; it < 60; it++) {
                double mid = 0.5 * (lo + hi);
                if (Math.pow(mid, n) - mid > k) hi = mid;
                else lo = mid;
            }
            rEscape = hi;
        }
        // With few iterations the "surface" is a slightly inflated version of the set, and the
        // DE is only approximate - generous margin, cheap because DE steps are large out there.
        return Math.min(Math.max(rEscape * 1.35, 1.5), bailout);
    }

    /**
     * Reference implementation of one spherical power step using the textbook trigonometric
     * formula. Used by the tests and the mathematics panel; not used in the hot path.
     *
     * @param out receives {x', y', z', r, theta, phi}
     */
    public static void sphericalPower(double x, double y, double z, double n, double[] out) {
        double r = Math.sqrt(x * x + y * y + z * z);
        double theta = r > 0 ? Math.acos(MathUtils.clamp(z / r, -1, 1)) : 0;
        double phi = Math.atan2(y, x);
        double rn = Math.pow(r, n);
        out[0] = rn * Math.sin(n * theta) * Math.cos(n * phi);
        out[1] = rn * Math.sin(n * theta) * Math.sin(n * phi);
        out[2] = rn * Math.cos(n * theta);
        out[3] = r;
        out[4] = theta;
        out[5] = phi;
    }
}
