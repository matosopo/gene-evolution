package sk.blueai.evolution.engine;

import java.util.Random;

public final class Binomial {

    private static final double APPROX_THRESHOLD = 20.0;

    private Binomial() {
    }

    public static long sample(long n, double p, Random rng) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0 but was " + n);
        if (p < 0.0 || p > 1.0 || Double.isNaN(p)) {
            throw new IllegalArgumentException("p must be in [0,1] but was " + p);
        }
        if (n == 0 || p == 0.0) return 0L;
        if (p == 1.0) return n;

        double mean = n * p;
        double meanComplement = n * (1.0 - p);

        if (mean < APPROX_THRESHOLD || meanComplement < APPROX_THRESHOLD) {
            long successes = 0;
            for (long i = 0; i < n; i++) {
                if (rng.nextDouble() < p) successes++;
            }
            return successes;
        }

        double sd = Math.sqrt(mean * (1.0 - p));
        double draw = mean + sd * rng.nextGaussian();
        long rounded = Math.round(draw);
        if (rounded < 0) return 0L;
        if (rounded > n) return n;
        return rounded;
    }
}
