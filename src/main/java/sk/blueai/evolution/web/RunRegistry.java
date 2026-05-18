package sk.blueai.evolution.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import sk.blueai.evolution.config.SimulationConfig;

public final class RunRegistry {

    private static final int HARD_CAP = 32;
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final ConcurrentMap<String, Run> runs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService gc;

    public RunRegistry() {
        this.gc = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "run-gc");
            t.setDaemon(true);
            return t;
        });
        gc.scheduleAtFixedRate(this::evict, 60, 60, TimeUnit.SECONDS);
    }

    public Run create(String id, SimulationConfig config) {
        Run run = new Run(id, config);
        runs.put(id, run);
        evict();
        return run;
    }

    public Run get(String id) {
        return runs.get(id);
    }

    public int activeCount() {
        int n = 0;
        for (Run r : runs.values()) {
            Run.Status s = r.status();
            if (s == Run.Status.RUNNING || s == Run.Status.PENDING) n++;
        }
        return n;
    }

    public void shutdown() {
        gc.shutdownNow();
    }

    private void evict() {
        Instant cutoff = Instant.now().minus(MAX_AGE);
        runs.entrySet().removeIf(e -> {
            Instant f = e.getValue().finishedAt();
            return f != null && f.isBefore(cutoff);
        });
        if (runs.size() > HARD_CAP) {
            List<Map.Entry<String, Run>> finished = new ArrayList<>();
            for (Map.Entry<String, Run> e : runs.entrySet()) {
                if (e.getValue().finishedAt() != null) finished.add(e);
            }
            finished.sort(Comparator.comparing(e -> e.getValue().finishedAt()));
            int over = runs.size() - HARD_CAP;
            for (int i = 0; i < over && i < finished.size(); i++) {
                runs.remove(finished.get(i).getKey());
            }
        }
    }
}
