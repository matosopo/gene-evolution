package sk.blueai.evolution.output;

import java.util.List;

import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.SpeciesSnapshot;

public interface SimulationListener {
    default void onStart(SimulationConfig config) {}

    void onStep(int step, List<SpeciesSnapshot> species, long totalN);

    default void onEnd() {}
}
