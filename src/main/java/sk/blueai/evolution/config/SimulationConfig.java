package sk.blueai.evolution.config;

import sk.blueai.evolution.model.Replicator;

public record SimulationConfig(
        int finalStepCount,
        int crowdingFactor,
        long randomSeed,
        int threadCount,
        Replicator spontaneousTemplate,
        OutputConfig output
) {
    public SimulationConfig {
        if (finalStepCount < 0) {
            throw new IllegalArgumentException("finalStepCount must be >= 0 but was " + finalStepCount);
        }
        if (crowdingFactor <= 0) {
            throw new IllegalArgumentException("crowdingFactor must be > 0 but was " + crowdingFactor);
        }
        if (threadCount < 0) {
            throw new IllegalArgumentException("threadCount must be >= 0 but was " + threadCount);
        }
        if (spontaneousTemplate == null) {
            throw new IllegalArgumentException("spontaneousTemplate is required");
        }
        if (output == null) {
            output = OutputConfig.empty();
        }
    }

    public SimulationConfig(
            int finalStepCount,
            int crowdingFactor,
            long randomSeed,
            Replicator spontaneousTemplate,
            OutputConfig output) {
        this(finalStepCount, crowdingFactor, randomSeed, 1, spontaneousTemplate, output);
    }

    public int resolvedThreadCount() {
        return threadCount == 0 ? Runtime.getRuntime().availableProcessors() : threadCount;
    }
}
