package com.mandelbulb.animation;

/** Easing curves mapping linear time u in [0,1] to eased progress in [0,1]. */
public enum Easing {
    LINEAR("Linear") {
        @Override
        public double apply(double u) {
            return u;
        }
    },
    SMOOTHSTEP("Smoothstep") {
        @Override
        public double apply(double u) {
            return u * u * (3 - 2 * u);
        }
    },
    EASE_IN_OUT_SINE("Ease in-out (sine)") {
        @Override
        public double apply(double u) {
            return 0.5 - 0.5 * Math.cos(Math.PI * u);
        }
    },
    EASE_IN_OUT_CUBIC("Ease in-out (cubic)") {
        @Override
        public double apply(double u) {
            return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
        }
    },
    EASE_IN_QUAD("Ease in (quadratic)") {
        @Override
        public double apply(double u) {
            return u * u;
        }
    },
    EASE_OUT_QUAD("Ease out (quadratic)") {
        @Override
        public double apply(double u) {
            return 1 - (1 - u) * (1 - u);
        }
    };

    private final String label;

    Easing(String label) {
        this.label = label;
    }

    public abstract double apply(double u);

    /** Clamps u into [0,1] before easing. */
    public double ease(double u) {
        return apply(u < 0 ? 0 : (u > 1 ? 1 : u));
    }

    @Override
    public String toString() {
        return label;
    }
}
