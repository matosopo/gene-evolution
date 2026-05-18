package sk.blueai.evolution.model;

public record Replicator(
        long id,
        double spawnProbability,
        double deathProbability,
        double replicationProbability,
        double mutationProbability,
        double variation
) {
    public Replicator {
        requireProbability("spawnProbability", spawnProbability);
        requireProbability("deathProbability", deathProbability);
        requireProbability("replicationProbability", replicationProbability);
        requireProbability("mutationProbability", mutationProbability);
        requireProbability("variation", variation);
    }

    private static void requireProbability(String name, double value) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0,1] but was " + value);
        }
    }
}
