package sk.blueai.evolution.engine;

public record MutantSpec(
        double deathProbability,
        double replicationProbability,
        double mutationProbability,
        double variation
) {
}
