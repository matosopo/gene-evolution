package sk.blueai.evolution.web;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.Replicator;
import sk.blueai.evolution.model.SpeciesSnapshot;
import sk.blueai.evolution.output.SimulationListener;

public final class WebSseListener implements SimulationListener {

    private final Run run;

    public WebSseListener(Run run) {
        this.run = run;
    }

    @Override
    public void onStart(SimulationConfig config) {
        JSONObject d = new JSONObject();
        d.put("runId", run.id());
        d.put("finalStepCount", config.finalStepCount());
        d.put("crowdingFactor", config.crowdingFactor());
        d.put("randomSeed", config.randomSeed());
        Replicator t = config.spontaneousTemplate();
        JSONObject sp = new JSONObject();
        sp.put("spawnProbability", t.spawnProbability());
        sp.put("deathProbability", t.deathProbability());
        sp.put("replicationProbability", t.replicationProbability());
        sp.put("mutationProbability", t.mutationProbability());
        sp.put("variation", t.variation());
        d.put("spontaneous", sp);
        run.emit(new SseMessage("start", d.toString()));
    }

    @Override
    public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
        JSONObject d = new JSONObject();
        d.put("step", step);
        d.put("totalN", totalN);
        JSONArray arr = new JSONArray();
        for (SpeciesSnapshot s : species) {
            JSONObject o = new JSONObject();
            o.put("id", s.replicator().id());
            o.put("count", s.count());
            o.put("spawn", s.replicator().spawnProbability());
            o.put("death", s.replicator().deathProbability());
            o.put("repl", s.replicator().replicationProbability());
            o.put("mut", s.replicator().mutationProbability());
            o.put("var", s.replicator().variation());
            arr.put(o);
        }
        d.put("species", arr);
        run.emit(new SseMessage("step", d.toString()));
    }

    @Override
    public void onEnd() {
        // The "end" event with summary fields is emitted by the worker thread
        // after engine.run() returns, since it needs elapsed time + final population stats.
    }
}
