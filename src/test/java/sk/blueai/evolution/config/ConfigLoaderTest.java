package sk.blueai.evolution.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sk.blueai.evolution.model.Replicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigLoaderTest {

    @Test
    void parsesFullConfig(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, """
                {
                  "finalStepCount": 1234,
                  "crowdingFactor": 567,
                  "randomSeed": 89,
                  "spontaneous": {
                    "spawnProbability": 0.011,
                    "deathProbability": 0.022,
                    "replicationProbability": 0.033,
                    "mutationProbability": 0.044,
                    "variation": 0.055
                  },
                  "output": {
                    "csv": "out/x.csv",
                    "png": "out/x.png"
                  }
                }
                """);

        SimulationConfig c = ConfigLoader.load(file);
        assertEquals(1234, c.finalStepCount());
        assertEquals(567, c.crowdingFactor());
        assertEquals(89L, c.randomSeed());

        Replicator t = c.spontaneousTemplate();
        assertEquals(1L, t.id());
        assertEquals(0.011, t.spawnProbability());
        assertEquals(0.022, t.deathProbability());
        assertEquals(0.033, t.replicationProbability());
        assertEquals(0.044, t.mutationProbability());
        assertEquals(0.055, t.variation());

        assertEquals(Path.of("out/x.csv"), c.output().csv());
        assertEquals(Path.of("out/x.png"), c.output().png());
    }

    @Test
    void missingOutputBlockGivesEmpty(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("config.json");
        Files.writeString(file, """
                {
                  "finalStepCount": 10,
                  "crowdingFactor": 100,
                  "spontaneous": {
                    "spawnProbability": 0.1,
                    "deathProbability": 0.1,
                    "replicationProbability": 0.1,
                    "mutationProbability": 0.1,
                    "variation": 0.1
                  }
                }
                """);
        SimulationConfig c = ConfigLoader.load(file);
        assertNull(c.output().csv());
        assertNull(c.output().png());
        assertEquals(0L, c.randomSeed());
    }
}
