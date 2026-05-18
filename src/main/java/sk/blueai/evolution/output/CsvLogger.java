package sk.blueai.evolution.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.model.Replicator;
import sk.blueai.evolution.model.SpeciesSnapshot;

public final class CsvLogger implements SimulationListener {

    private final Path path;
    private BufferedWriter writer;

    public CsvLogger(Path path) {
        this.path = path;
    }

    @Override
    public void onStart(SimulationConfig config) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            writer = Files.newBufferedWriter(path);
            writer.write("step,speciesId,count,spawn,death,repl,mut,var");
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void onStep(int step, List<SpeciesSnapshot> species, long totalN) {
        try {
            for (SpeciesSnapshot s : species) {
                Replicator r = s.replicator();
                writer.write(String.format(
                        Locale.ROOT,
                        "%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f",
                        step,
                        r.id(),
                        s.count(),
                        r.spawnProbability(),
                        r.deathProbability(),
                        r.replicationProbability(),
                        r.mutationProbability(),
                        r.variation()));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void onEnd() {
        try {
            if (writer != null) writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
