package sk.blueai.evolution.engine;

import java.util.Random;

import sk.blueai.evolution.model.Replicator;

public final class Mutator {

    private Mutator() {
    }

    public static Replicator mutate(Replicator parent, long newId, Random rng) {
        MutantSpec spec = perturb(parent, rng);
        return new Replicator(
                newId,
                0.0,
                spec.deathProbability(),
                spec.replicationProbability(),
                spec.mutationProbability(),
                spec.variation()
        );
    }

    public static MutantSpec perturb(Replicator parent, Random rng) {
        double v = parent.variation();
        return new MutantSpec(
                perturb(parent.deathProbability(), v, rng),
                perturb(parent.replicationProbability(), v, rng),
                perturb(parent.mutationProbability(), v, rng),
                parent.variation()
        );
    }

    private static double perturb(double value, double variation, Random rng) {
        double delta = (rng.nextDouble() * 2.0 - 1.0) * variation;
        double next = value + delta;
        if (next < 0.0) return 0.0;
        if (next > 1.0) return 1.0;
        return next;
    }
}
