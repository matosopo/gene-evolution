package sk.blueai.evolution.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.Replicator;
import sk.blueai.evolution.model.Species;
import sk.blueai.evolution.output.SimulationListener;

public final class SimulationEngine {

    private final SimulationConfig config;
    private final SimulationListener listener;
    private final Random rng;
    private final Population population;
    private final int threadCount;

    public SimulationEngine(SimulationConfig config, SimulationListener listener) {
        this.config = config;
        this.listener = listener;
        this.rng = new Random(config.randomSeed());
        this.population = new Population(config.spontaneousTemplate().id() + 1);
        this.population.register(config.spontaneousTemplate(), 0L);
        this.threadCount = config.resolvedThreadCount();
    }

    public void run() {
        listener.onStart(config);

        Replicator spontaneous = config.spontaneousTemplate();
        double C = config.crowdingFactor();

        ExecutorService executor = threadCount > 1
                ? Executors.newFixedThreadPool(threadCount)
                : null;
        try {
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

                List<Species> active = population.activeSnapshot();
                List<SpeciesStepResult> results = compute(active, factor, step, executor);
                commit(active, results);

                listener.onStep(step, population.allSnapshots(), population.totalN());
                population.removeExtinct();
            }
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }

        listener.onEnd();
    }

    private List<SpeciesStepResult> compute(List<Species> active, double factor, int step,
                                            ExecutorService executor) {
        if (active.isEmpty()) return Collections.emptyList();
        if (executor == null) {
            List<SpeciesStepResult> out = new ArrayList<>(active.size());
            for (Species s : active) {
                out.add(computeOne(s, factor, step));
            }
            return out;
        }
        List<Callable<SpeciesStepResult>> tasks = new ArrayList<>(active.size());
        for (Species s : active) {
            tasks.add(() -> computeOne(s, factor, step));
        }
        try {
            List<Future<SpeciesStepResult>> futures = executor.invokeAll(tasks);
            List<SpeciesStepResult> out = new ArrayList<>(futures.size());
            for (Future<SpeciesStepResult> f : futures) {
                out.add(f.get());
            }
            return out;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation step interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Per-species computation failed", cause);
        }
    }

    private SpeciesStepResult computeOne(Species s, double factor, int step) {
        long speciesId = s.replicator().id();
        long count = s.count();
        if (count <= 0) {
            return new SpeciesStepResult(speciesId, 0L, 0L, List.of());
        }
        Random taskRng = new Random(seedFor(step, speciesId));

        long deaths = Binomial.sample(count, s.replicator().deathProbability(), taskRng);
        long alive = count - deaths;
        if (alive <= 0) {
            return new SpeciesStepResult(speciesId, deaths, 0L, List.of());
        }

        double effRepl = clampProb(s.replicator().replicationProbability() * factor);
        long reps = Binomial.sample(alive, effRepl, taskRng);
        if (reps == 0) {
            return new SpeciesStepResult(speciesId, deaths, 0L, List.of());
        }

        long mutations = Binomial.sample(reps, s.replicator().mutationProbability(), taskRng);
        long clones = reps - mutations;

        List<MutantSpec> mutants;
        if (mutations == 0) {
            mutants = List.of();
        } else {
            int cap = (int) Math.min(mutations, (long) Integer.MAX_VALUE);
            mutants = new ArrayList<>(cap);
            for (long m = 0; m < mutations; m++) {
                mutants.add(Mutator.perturb(s.replicator(), taskRng));
            }
        }
        return new SpeciesStepResult(speciesId, deaths, clones, mutants);
    }

    private void commit(List<Species> active, List<SpeciesStepResult> results) {
        for (int i = 0; i < active.size(); i++) {
            Species s = active.get(i);
            SpeciesStepResult r = results.get(i);
            long delta = -r.deaths() + r.clones();
            if (delta != 0L) {
                s.add(delta);
                population.adjustTotal(delta);
            }
            for (MutantSpec spec : r.mutants()) {
                long newId = population.nextSpeciesId();
                Replicator mutant = new Replicator(
                        newId,
                        0.0,
                        spec.deathProbability(),
                        spec.replicationProbability(),
                        spec.mutationProbability(),
                        spec.variation()
                );
                population.register(mutant, 1L);
            }
        }
    }

    private long seedFor(int step, long speciesId) {
        long h = config.randomSeed();
        h = h * 0x9E3779B97F4A7C15L + step;
        h = h * 0x9E3779B97F4A7C15L + speciesId;
        return h;
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
