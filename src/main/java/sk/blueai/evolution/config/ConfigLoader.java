package sk.blueai.evolution.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;

import sk.blueai.evolution.model.Replicator;

public final class ConfigLoader {

    private static final long SPONTANEOUS_ID = 1L;

    private ConfigLoader() {
    }

    public static SimulationConfig load(Path file) throws IOException {
        String text = Files.readString(file);
        JSONObject root = new JSONObject(text);

        int finalStepCount = root.getInt("finalStepCount");
        int crowdingFactor = root.getInt("crowdingFactor");
        long randomSeed = root.optLong("randomSeed", 0L);
        int threadCount = root.optInt("threadCount", 1);

        JSONObject sp = root.getJSONObject("spontaneous");
        Replicator template = new Replicator(
                SPONTANEOUS_ID,
                sp.getDouble("spawnProbability"),
                sp.getDouble("deathProbability"),
                sp.getDouble("replicationProbability"),
                sp.getDouble("mutationProbability"),
                sp.getDouble("variation")
        );

        OutputConfig output;
        if (root.has("output")) {
            JSONObject o = root.getJSONObject("output");
            Path csv = o.has("csv") && !o.isNull("csv") ? Path.of(o.getString("csv")) : null;
            Path png = o.has("png") && !o.isNull("png") ? Path.of(o.getString("png")) : null;
            output = new OutputConfig(csv, png);
        } else {
            output = OutputConfig.empty();
        }

        return new SimulationConfig(finalStepCount, crowdingFactor, randomSeed, threadCount, template, output);
    }
}
