package sk.blueai.evolution.engine;

import java.util.List;
import java.util.Random;

import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.Replicator;
import sk.blueai.evolution.model.Species;
import sk.blueai.evolution.output.SimulationListener;

public final class SimulationEngine {

    private final SimulationConfig config;
    private final SimulationListener listener;
    private final Random rng;
    private final Population population;

    public SimulationEngine(SimulationConfig config, SimulationListener listener) {
        this.config = config;
        this.listener = listener;
        this.rng = new Random(config.randomSeed());
        this.population = new Population(config.spontaneousTemplate().id() + 1);
        this.population.register(config.spontaneousTemplate(), 0L);
    }

    public void run() {
        listener.onStart(config);

        Replicator spontaneous = config.spontaneousTemplate();
        double C = config.crowdingFactor();

        for (int step = 0; step < config.finalStepCount(); step++) {
            double factor = resourceFactor(population.totalN(), C);

            if (rng.nextDouble() < spontaneous.spawnProbability() * factor) {
                Species spontaneousSpecies = population.get(spontaneous.id());
                if (spontaneousSpecies == null) {
                    spontaneousSpecies = population.register(spontaneous, 0L);
                }
                spontaneousSpecies.add(1L);
                population.adjustTotal(1L);
            }

            for (Species s : population.activeSnapshot()) {
                long count = s.count();
                if (count <= 0) continue;

                long deaths = Binomial.sample(count, s.replicator().deathProbability(), rng);
                if (deaths > 0) {
                    s.add(-deaths);
                    population.adjustTotal(-deaths);
                    count -= deaths;
                }
                if (count <= 0) continue;

                double effRepl = clampProb(s.replicator().replicationProbability() * factor);
                long reps = Binomial.sample(count, effRepl, rng);
                if (reps == 0) continue;

                long mutations = Binomial.sample(reps, s.replicator().mutationProbability(), rng);
                long clones = reps - mutations;
                if (clones > 0) {
                    s.add(clones);
                    population.adjustTotal(clones);
                }
                for (long m = 0; m < mutations; m++) {
                    Replicator mutant = Mutator.mutate(s.replicator(), population.nextSpeciesId(), rng);
                    population.register(mutant, 1L);
                }
            }

            listener.onStep(step, population.allSnapshots(), population.totalN());
            population.removeExtinct();
        }

        listener.onEnd();
    }

    private static double resourceFactor(long n, double c) {
        if (c <= 0.0) return 0.0;
        double f = 1.0 - (n / c);
        return f < 0.0 ? 0.0 : f;
    }

    private static double clampProb(double p) {
        if (p < 0.0) return 0.0;
        if (p > 1.0) return 1.0;
        return p;
    }

    public Population population() {
        return population;
    }
}
