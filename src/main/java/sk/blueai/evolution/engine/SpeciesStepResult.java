package sk.blueai.evolution.engine;

import java.util.List;

public record SpeciesStepResult(
        long speciesId,
        long deaths,
        long clones,
        List<MutantSpec> mutants
) {
}
