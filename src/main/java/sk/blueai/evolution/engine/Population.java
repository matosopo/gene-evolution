package sk.blueai.evolution.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sk.blueai.evolution.model.Replicator;
import sk.blueai.evolution.model.Species;
import sk.blueai.evolution.model.SpeciesSnapshot;

public final class Population {

    private final LinkedHashMap<Long, Species> species = new LinkedHashMap<>();
    private long totalN = 0L;
    private long nextSpeciesId;

    public Population(long firstSpeciesId) {
        this.nextSpeciesId = firstSpeciesId;
    }

    public Species register(Replicator replicator, long initialCount) {
        if (species.containsKey(replicator.id())) {
            throw new IllegalArgumentException("Species id already registered: " + replicator.id());
        }
        Species s = new Species(replicator, initialCount);
        species.put(replicator.id(), s);
        totalN += initialCount;
        nextSpeciesId = Math.max(nextSpeciesId, replicator.id() + 1);
        return s;
    }

    public Species get(long id) {
        return species.get(id);
    }

    public long nextSpeciesId() {
        return nextSpeciesId++;
    }

    public long totalN() {
        return totalN;
    }

    public void adjustTotal(long delta) {
        totalN += delta;
    }

    public List<Species> activeSnapshot() {
        return new ArrayList<>(species.values());
    }

    public List<SpeciesSnapshot> allSnapshots() {
        List<SpeciesSnapshot> out = new ArrayList<>(species.size());
        for (Species s : species.values()) {
            out.add(SpeciesSnapshot.of(s));
        }
        return out;
    }

    public void removeExtinct() {
        Iterator<Map.Entry<Long, Species>> it = species.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().count() <= 0) it.remove();
        }
    }

    public int activeSpeciesCount() {
        return species.size();
    }
}
