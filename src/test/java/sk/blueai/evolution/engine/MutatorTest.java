package sk.blueai.evolution.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import sk.blueai.evolution.model.Replicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutatorTest {

    @Test
    void mutantHasZeroSpawnProbability() {
        Replicator parent = new Replicator(1, 0.5, 0.1, 0.2, 0.05, 0.1);
        Replicator child = Mutator.mutate(parent, 2, new Random(0));
        assertEquals(0.0, child.spawnProbability());
        assertEquals(2L, child.id());
    }

    @Test
    void mutantInheritsVariationUnchanged() {
        Replicator parent = new Replicator(1, 0.1, 0.1, 0.1, 0.1, 0.07);
        Random rng = new Random(123);
        for (int i = 0; i < 200; i++) {
            Replicator child = Mutator.mutate(parent, i + 2, rng);
            assertEquals(parent.variation(), child.variation(), 1e-12);
        }
    }

    @Test
    void perturbedStatsStayWithinVariationBand() {
        double v = 0.05;
        Replicator parent = new Replicator(1, 0.1, 0.30, 0.40, 0.20, v);
        Random rng = new Random(2);
        for (int i = 0; i < 500; i++) {
            Replicator c = Mutator.mutate(parent, i + 2, rng);
            assertWithinBand(parent.deathProbability(), c.deathProbability(), v);
            assertWithinBand(parent.replicationProbability(), c.replicationProbability(), v);
            assertWithinBand(parent.mutationProbability(), c.mutationProbability(), v);
        }
    }

    @Test
    void perturbationClampsAtBounds() {
        Replicator parent = new Replicator(1, 0.0, 0.99, 0.01, 0.5, 0.5);
        Random rng = new Random(3);
        for (int i = 0; i < 1000; i++) {
            Replicator c = Mutator.mutate(parent, i + 2, rng);
            assertTrue(c.deathProbability() >= 0.0 && c.deathProbability() <= 1.0);
            assertTrue(c.replicationProbability() >= 0.0 && c.replicationProbability() <= 1.0);
            assertTrue(c.mutationProbability() >= 0.0 && c.mutationProbability() <= 1.0);
        }
    }

    private static void assertWithinBand(double parent, double child, double v) {
        double low = Math.max(0.0, parent - v);
        double high = Math.min(1.0, parent + v);
        assertTrue(child >= low - 1e-12 && child <= high + 1e-12,
                "child=" + child + " outside [" + low + ", " + high + "]");
    }
}
