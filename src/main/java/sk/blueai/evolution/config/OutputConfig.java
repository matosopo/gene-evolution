package sk.blueai.evolution.config;

import java.nio.file.Path;

public record OutputConfig(Path csv, Path png) {
    public static OutputConfig empty() {
        return new OutputConfig(null, null);
    }
}
