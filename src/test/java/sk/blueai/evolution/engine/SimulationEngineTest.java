package sk.blueai.evolution.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sk.blueai.evolution.config.OutputConfig;
import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.Replicator;
import sk.blueai.evolution.model.SpeciesSnapshot;
import sk.blueai.evolution.output.SimulationListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineTest {

    @Test
    void deathRateOneExtinguishesQuickly() {
        Replicator template = new Replicator(1, 1.0, 1.0, 0.0, 0.0, 0.0);
        SimulationConfig config = new SimulationConfig(20, 100, 1L, template, OutputConfig.empty());

        SimulationEngine engine = new SimulationEngine(config, new SimulationListener() {
            @Override public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {}
        });
        engine.run();

        assertEquals(0L, engine.population().totalN());
    }

    @Test
    void replicationOnlyGrowsAndPlateausUnderCrowding() {
        Replicator template = new Replicator(1, 1.0, 0.0, 1.0, 0.0, 0.0);
        int C = 200;
        SimulationConfig config = new SimulationConfig(500, C, 42L, template, OutputConfig.empty());

        long[] last = {0};
        boolean[] everDecreased = {false};
        SimulationEngine engine = new SimulationEngine(config, new SimulationListener() {
            @Override public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
                if (totalN < last[0]) everDecreased[0] = true;
                last[0] = totalN;
            }
        });
        engine.run();

        assertTrue(!everDecreased[0], "population should grow monotonically with deathProb=0");
        long finalN = engine.population().totalN();
        assertTrue(finalN > C * 0.7 && finalN <= C * 1.15,
                "expected plateau near C=" + C + " but got " + finalN);
    }

    @Test
    void heavyMutationProducesManyDistinctSpecies() {
        Replicator template = new Replicator(1, 1.0, 0.01, 1.0, 1.0, 0.05);
        SimulationConfig config = new SimulationConfig(100, 500, 99L, template, OutputConfig.empty());

        int[] maxObserved = {0};
        SimulationEngine engine = new SimulationEngine(config, new SimulationListener() {
            @Override public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
                if (species.size() > maxObserved[0]) maxObserved[0] = species.size();
            }
        });
        engine.run();

        assertTrue(maxObserved[0] >= 10,
                "expected >=10 species to appear with mutationProb=1, got " + maxObserved[0]);
    }

    @Test
    void sameSeedProducesIdenticalRuns() {
        Replicator template = new Replicator(1, 0.5, 0.1, 0.5, 0.1, 0.1);
        SimulationConfig config = new SimulationConfig(50, 200, 7L, template, OutputConfig.empty());

        List<long[]> a = capture(config);
        List<long[]> b = capture(config);

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            long[] ra = a.get(i);
            long[] rb = b.get(i);
            assertEquals(ra.length, rb.length, "row length differs at step " + i);
            for (int j = 0; j < ra.length; j++) {
                assertEquals(ra[j], rb[j], "value differs at step " + i + " col " + j);
            }
        }
    }

    @Test
    void crowdingFactorThrottlesGrowthNearCeiling() {
        Replicator template = new Replicator(1, 1.0, 0.0, 1.0, 0.5, 0.05);
        int C = 150;
        SimulationConfig config = new SimulationConfig(300, C, 11L, template, OutputConfig.empty());

        long[] peak = {0};
        SimulationEngine engine = new SimulationEngine(config, new SimulationListener() {
            @Override public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
                if (totalN > peak[0]) peak[0] = totalN;
            }
        });
        engine.run();

        // Step-start factor freeze means transient overshoot is expected; allow 15%.
        assertTrue(peak[0] <= C * 1.15, "peak " + peak[0] + " should be near C=" + C);
        assertTrue(peak[0] >= C * 0.7, "peak " + peak[0] + " should grow near C=" + C);
    }

    private static List<long[]> capture(SimulationConfig config) {
        List<long[]> rows = new ArrayList<>();
        SimulationEngine engine = new SimulationEngine(config, new SimulationListener() {
            @Override public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
                long[] row = new long[1 + species.size() * 2];
                row[0] = totalN;
                Set<Long> ids = new HashSet<>();
                int idx = 1;
                for (SpeciesSnapshot s : species) {
                    row[idx++] = s.replicator().id();
                    row[idx++] = s.count();
                    ids.add(s.replicator().id());
                }
                rows.add(row);
            }
        });
        engine.run();
        return rows;
    }
}
