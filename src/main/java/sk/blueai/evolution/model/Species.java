package sk.blueai.evolution.model;

public final class Species {
    private final Replicator replicator;
    private long count;

    public Species(Replicator replicator, long count) {
        this.replicator = replicator;
        this.count = count;
    }

    public Replicator replicator() {
        return replicator;
    }

    public long count() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public void add(long delta) {
        this.count += delta;
    }
}
