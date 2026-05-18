package sk.blueai.evolution.model;

public record SpeciesSnapshot(Replicator replicator, long count) {
    public static SpeciesSnapshot of(Species species) {
        return new SpeciesSnapshot(species.replicator(), species.count());
    }
}
