package sk.blueai.evolution.output;

import java.util.ArrayList;
import java.util.List;

import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.SpeciesSnapshot;

public final class CompositeListener implements SimulationListener {

    private final List<SimulationListener> delegates = new ArrayList<>();

    public CompositeListener add(SimulationListener listener) {
        if (listener != null) delegates.add(listener);
        return this;
    }

    @Override
    public void onStart(SimulationConfig config) {
        for (SimulationListener l : delegates) l.onStart(config);
    }

    @Override
    public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
        for (SimulationListener l : delegates) l.onStep(step, species, totalN);
    }

    @Override
    public void onEnd() {
        for (SimulationListener l : delegates) l.onEnd();
    }
}
