package com.mandelbulb.ui;

import com.mandelbulb.app.ApplicationState;
import com.mandelbulb.fractal.FractalParameters;
import com.mandelbulb.fractal.Mandelbulb;
import com.mandelbulb.fractal.OrbitTrap;
import com.mandelbulb.math.Vector3;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Locale;

import static com.mandelbulb.ui.BoundControls.hint;
import static com.mandelbulb.ui.BoundControls.section;

/**
 * Educational panel: the equations behind the current image, with live parameter values, and a
 * step-by-step evaluation of the iteration at a chosen point (e.g. the surface point at the centre
 * of the screen).
 */
final class MathematicsPanel extends SettingsPanel {

    private final Label parameters = math("");
    private final Label transform = math("");
    private final Label orbit = math("");
    private final TextField px = new TextField("0.5");
    private final TextField py = new TextField("-0.4");
    private final TextField pz = new TextField("0.6");
    private String lastKey = "";

    MathematicsPanel(ApplicationState state, Runnable onChange) {
        super(state, onChange);

        content.getChildren().add(section("Mandelbulb", parameters));

        content.getChildren().add(section("Iteration",
                math("""
                        z[0]   = c        (Julia mode: z[0] = p, c = k)
                        z[k+1] = f(z[k]) + c
                        escape when |z[k]| > R"""),
                hint("The 3D analogue of z² + c: 'raising a point to the n-th power' multiplies its spherical angles by n "
                        + "and raises its length to the n-th power.")));

        content.getChildren().add(section("Spherical transformation f(x, y, z)",
                math("""
                        r = √(x² + y² + z²)
                        θ = acos(z / r)
                        φ = atan2(y, x)

                        x' = rⁿ · sin(nθ) · cos(nφ)
                        y' = rⁿ · sin(nθ) · sin(nφ)
                        z' = rⁿ · cos(nθ)""")));

        Button centre = new Button("Use screen-centre surface point");
        centre.setOnAction(e -> {
            int[] size = state.renderSize();
            Vector3 p = state.pick(size[0] * 0.5, size[1] * 0.5);
            if (p != null) {
                Vector3 o = objectSpace(p);
                px.setText(String.format(Locale.ROOT, "%.6f", o.x()));
                py.setText(String.format(Locale.ROOT, "%.6f", o.y()));
                pz.setText(String.format(Locale.ROOT, "%.6f", o.z()));
                lastKey = "";
                pulse();
            }
        });
        for (TextField f : new TextField[] {px, py, pz}) {
            f.setPrefColumnCount(7);
            HBox.setHgrow(f, Priority.ALWAYS);
            f.setOnAction(e -> {
                lastKey = "";
                pulse();
            });
        }
        content.getChildren().add(section("Worked example at point p",
                new HBox(4, new Label("p ="), px, py, pz), centre, transform, orbit));

        content.getChildren().add(section("Distance estimation",
                math("""
                        dr[0]   = 1
                        dr[k+1] = n · r[k]^(n−1) · dr[k] + 1
                        DE(p)   = ½ · log(r) · r / dr"""),
                hint("dr tracks |dz/dc| by the chain rule. From the Hubbard–Douady potential G = lim log|z[k]| / n^k, "
                        + "the distance to the set is approximately G / |∇G| = ½·r·log r / dr. "
                        + "It is a (nearly) safe lower bound: a sphere of that radius around p contains no surface.")));

        content.getChildren().add(section("Ray marching (sphere tracing)",
                math("""
                        t[0]   = entry into bounding sphere
                        t[i+1] = t[i] + s · DE(o + t[i]·d)
                        hit when DE < ε(t) = t · pixel angle / detail"""),
                hint("Steps are large in empty space and shrink near the surface. s is the DE scale (fudge factor). "
                        + "With over-relaxation the step is multiplied by ω while consecutive safe spheres overlap.")));

        content.getChildren().add(section("Normals, occlusion and shadows",
                math("""
                        N ≈ normalize( DE(p+εx)−DE(p−εx),
                                     DE(p+εy)−DE(p−εy),
                                     DE(p+εz)−DE(p−εz) )
                        AO = 1 − k · Σ w[i]·(h[i] − DE(p + h[i]·N)) / Σ w[i]·h[i]
                        shadow = min over t of  k · DE(p + t·L) / t"""),
                hint("The surface is the level set DE = 0, so its normal is the DE gradient. AO measures how much the "
                        + "distance field around p falls short of an open half-space; soft shadows use how closely a "
                        + "shadow ray grazes other geometry.")));
        pulse();
    }

    private static Label math(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("math");
        l.setWrapText(true);
        return l;
    }

    private Vector3 objectSpace(Vector3 world) {
        FractalParameters f = state.scene().fractal;
        double az = Math.toRadians(-f.rotationZ), ax = Math.toRadians(-f.rotationX);
        double x1 = Math.cos(az) * world.x() - Math.sin(az) * world.y();
        double y1 = Math.sin(az) * world.x() + Math.cos(az) * world.y();
        double z1 = world.z();
        return new Vector3(x1, Math.cos(ax) * y1 - Math.sin(ax) * z1, Math.sin(ax) * y1 + Math.cos(ax) * z1);
    }

    @Override
    public void pulse() {
        FractalParameters f = state.scene().fractal;
        String key = f.power + "|" + f.maxIterations + "|" + f.bailout + "|" + f.juliaMode + "|" + f.juliaX + "|"
                + f.juliaY + "|" + f.juliaZ + "|" + px.getText() + "|" + py.getText() + "|" + pz.getText();
        if (key.equals(lastKey)) return;
        lastKey = key;

        Mandelbulb mb = new Mandelbulb(f);
        parameters.setText(String.format(Locale.ROOT,
                "Power:            n = %s%nEscape radius:    R = %s%nMax iterations:   %d%nMode:             %s%n"
                        + "Bounding radius:  %.4f%nEvaluation:       %s",
                trim(f.power), trim(f.bailout), f.maxIterations,
                f.juliaMode ? String.format(Locale.ROOT, "Julia, k = (%.3f, %.3f, %.3f)", f.juliaX, f.juliaY, f.juliaZ) : "Mandelbrot (c = p)",
                mb.boundingRadius(),
                mb.usesIntegerFastPath() ? "integer n → trig-free de Moivre fast path" : "fractional n → trigonometric form"));

        double x, y, z;
        try {
            x = Double.parseDouble(px.getText().trim());
            y = Double.parseDouble(py.getText().trim());
            z = Double.parseDouble(pz.getText().trim());
            if (!Double.isFinite(x + y + z)) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            transform.setText("Enter a valid point p = (x, y, z).");
            orbit.setText("");
            return;
        }
        double n = f.power;
        double cx = f.juliaMode ? f.juliaX : x, cy = f.juliaMode ? f.juliaY : y, cz = f.juliaMode ? f.juliaZ : z;
        double[] out = new double[6];
        Mandelbulb.sphericalPower(x, y, z, n, out);
        transform.setText(String.format(Locale.ROOT,
                "First step, z[0] = p:%n  r  = %.6f%n  θ  = %.6f rad  (%.2f°)%n  φ  = %.6f rad  (%.2f°)%n"
                        + "  rⁿ = %.6g%n  nθ = %.6f   nφ = %.6f%n"
                        + "  f(z[0]) = (%.6f, %.6f, %.6f)%n  z[1] = f(z[0]) + c = (%.6f, %.6f, %.6f)",
                out[3], out[4], Math.toDegrees(out[4]), out[5], Math.toDegrees(out[5]), Math.pow(out[3], n),
                n * out[4], n * out[5], out[0], out[1], out[2], out[0] + cx, out[1] + cy, out[2] + cz));

        StringBuilder sb = new StringBuilder("Orbit:\n   k          x          y          z        |z|\n");
        double ox = x, oy = y, oz = z;
        for (int k = 0; k <= Math.min(f.maxIterations, 10); k++) {
            double r = Math.sqrt(ox * ox + oy * oy + oz * oz);
            sb.append(String.format(Locale.ROOT, "  %2d %10.4g %10.4g %10.4g %10.4g%n", k, ox, oy, oz, r));
            if (r > f.bailout || !Double.isFinite(r)) {
                sb.append("  escaped (|z| > R)\n");
                break;
            }
            Mandelbulb.sphericalPower(ox, oy, oz, n, out);
            ox = out[0] + cx;
            oy = out[1] + cy;
            oz = out[2] + cz;
        }
        OrbitTrap trap = new OrbitTrap();
        double de = mb.distance(x, y, z, trap);
        sb.append(String.format(Locale.ROOT,
                "%nEscape iteration:   %s%nSmooth iteration:   %.4f%nDistance estimate:  DE(p) = %.6g%n"
                        + "Orbit traps:  min|x| %.4f  min|y| %.4f  min|z| %.4f  min r %.4f",
                trap.escaped ? Integer.toString(trap.iterations) : "never (inside, " + trap.iterations + " iterations)",
                trap.smoothIteration, de, trap.minAbsX, trap.minAbsY, trap.minAbsZ, trap.minRadius));
        orbit.setText(sb.toString());
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.3f", v);
    }
}
