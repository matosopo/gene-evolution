# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Java 17 / Maven implementation of the "Simple Gene Evolution Simulation" specified in [documents/evolution-simulation.md](documents/evolution-simulation.md) (Slovak/English). The spec is the source of truth for domain rules; [README.md](README.md) documents the public-facing semantics, config schema, and the deliberate deviations from the spec.

## Build / test / run — Docker only

The host has no JDK or Maven. All Maven invocations go through Docker via the wrappers in `scripts/`.

```powershell
pwsh scripts/mvn.ps1 test       # unit tests (Surefire)
pwsh scripts/mvn.ps1 package    # builds target/gene-evolution-1.0.0.jar (shaded fat jar)
pwsh scripts/run.ps1 examples/default.json   # smoke run, writes out/sim.csv + out/sim.png
```

To run a single test class: `pwsh scripts/mvn.ps1 -Dtest=BinomialTest test`. Bash mirrors: `scripts/mvn.sh`, `scripts/run.sh`.

Images: build container is `maven:3.9-eclipse-temurin-17`; runtime is `eclipse-temurin:17-jre` (slim, no Maven). The Maven cache is persisted in the named volume `gene-evolution-m2` — do not recreate it gratuitously, it costs ~1 min to repopulate.

## Architecture

```
sk.blueai.evolution
├── cli       Main — CLI entry, builds CompositeListener, invokes SimulationEngine.run
├── config    SimulationConfig + OutputConfig records, ConfigLoader (org.json)
├── model     Replicator (immutable record), Species (mutable holder), SpeciesSnapshot
├── engine    SimulationEngine (the loop), Population, Binomial sampler, Mutator
└── output    SimulationListener (interface), CompositeListener, CsvLogger, PngGraphRecorder
```

Data flow: `Main` parses a JSON config into `SimulationConfig` → constructs `SimulationEngine` with a `CompositeListener` wrapping the configured sinks → `engine.run()` iterates `finalStepCount` steps, emitting `SpeciesSnapshot` lists per step. The engine is pure (no I/O). Listeners are the only side-effect surface — adding a new output format (database, websocket, …) is "implement `SimulationListener`, add to the composite in `Main`."

`Population` keys species by id in a `LinkedHashMap`, so CSV/PNG output is deterministic for a given seed. Extinct species are dropped from the active map after each step but their full timeseries lives in `PngGraphRecorder` so the chart still shows historical lineages.

## Rules that are easy to get wrong (mirrored from README — and enforced by tests)

- **Resource factor `factor = max(0, 1 − N/C)`** gates BOTH the per-step spontaneous spawn AND every species' per-individual replication probability. It is computed at step start and held constant for the step — do not recompute mid-step or you reintroduce species-ordering effects.
- **Secondary mutations have `spawnProbability = 0`** — `Mutator.mutate` enforces this; `MutatorTest` asserts it. Don't add a code path that produces a mutant with a non-zero spawn rate.
- **`variation` is inherited unchanged** (NOT itself mutated). Mutating it under additive+clamp gives absorbing boundaries at 0 and 1. `MutatorTest.mutantInheritsVariationUnchanged` is the guard.
- **Death evaluated before replication** in each step (a molecule cannot both die and reproduce in the same step). If you reorder, update the docstring on `SimulationEngine.run` and the README.
- **Binomial sampler** switches between exact Bernoulli loop (when `n*p < 20 && n*(1-p) < 20`) and a normal approximation. Edits here need the `BinomialTest` mean-within-tolerance assertions to keep passing.

## Spec-vs-implementation deviations (full list in README, "Engine semantics")

The reference pseudocode in the spec has bugs — inverted spawn comparison, `replicationFactor` computed outside the loop, `spawnProbability` placed on `Simulation` not `Replicator`. The implementation corrects all of these; if a future change appears to "fix" something to match the literal spec, check README's deviations list first.

## Non-goals (intentionally not built)

HTTP server, per-mutation-lineage threading, end-condition predicates beyond `finalStepCount`. If you add one, structure it as a new module that consumes `SimulationListener` events — do not push I/O or threading into the engine.
