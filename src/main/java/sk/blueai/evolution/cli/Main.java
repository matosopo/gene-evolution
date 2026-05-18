package sk.blueai.evolution.cli;

import java.nio.file.Path;

import sk.blueai.evolution.config.OutputConfig;
import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.config.ConfigLoader;
import sk.blueai.evolution.engine.SimulationEngine;
import sk.blueai.evolution.output.CompositeListener;
import sk.blueai.evolution.output.CsvLogger;
import sk.blueai.evolution.output.PngGraphRecorder;

public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar gene-evolution-<version>.jar <config.json>");
            System.exit(2);
        }

        Path configPath = Path.of(args[0]);
        SimulationConfig config = ConfigLoader.load(configPath);
        OutputConfig out = config.output();

        CompositeListener listeners = new CompositeListener();
        if (out.csv() != null) listeners.add(new CsvLogger(out.csv()));
        if (out.png() != null) listeners.add(new PngGraphRecorder(out.png()));

        long t0 = System.nanoTime();
        SimulationEngine engine = new SimulationEngine(config, listeners);
        engine.run();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        System.out.printf(
                "Simulation finished: steps=%d totalN=%d activeSpecies=%d elapsedMs=%d%n",
                config.finalStepCount(),
                engine.population().totalN(),
                engine.population().activeSpeciesCount(),
                elapsedMs);
        if (out.csv() != null) System.out.println("CSV: " + out.csv().toAbsolutePath());
        if (out.png() != null) System.out.println("PNG: " + out.png().toAbsolutePath());
    }
}
