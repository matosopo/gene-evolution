# Gene Evolution Simulation

Java implementation of the "Simple Gene Evolution Simulation" specified in [documents/evolution-simulation.md](documents/evolution-simulation.md). Spontaneously emerging self-replicating molecules ("replicators") die, copy themselves, and occasionally mutate, with replication throttled by a finite-resource crowding factor `C`.

The simulation outputs a per-step CSV log and a PNG line graph (one line per species).

## Prerequisites

**Docker only.** No JDK or Maven on the host. Builds and the runtime image are pulled on first use.

Optional one-time pre-pull:

```powershell
docker pull maven:3.9-eclipse-temurin-17
docker pull eclipse-temurin:17-jre
```

## Build and test

```powershell
pwsh scripts/mvn.ps1 test       # run unit tests
pwsh scripts/mvn.ps1 package    # build target/gene-evolution-1.0.0.jar (fat jar via shade)
```

Linux/Git Bash equivalents in `scripts/mvn.sh` and `scripts/run.sh`.

The Maven cache lives in the named volume `gene-evolution-m2`, so dependencies download once.

## Run

```powershell
pwsh scripts/run.ps1 examples/default.json
```

Outputs land in the host workspace at the paths declared in the config (default `out/sim.csv` and `out/sim.png`).

## Config

JSON shape (see [examples/default.json](examples/default.json)):

| key | type | meaning |
| --- | --- | --- |
| `finalStepCount` | int | number of simulation steps to run |
| `crowdingFactor` | int | `C` in `r = r × (1 − N/C)` — the resource ceiling |
| `randomSeed` | long | reproducibility seed (default 0) |
| `spontaneous.spawnProbability` | 0–1 | per-step chance the spontaneous replicator emerges (scaled by resource factor) |
| `spontaneous.deathProbability` | 0–1 | per-step per-molecule death chance |
| `spontaneous.replicationProbability` | 0–1 | per-step per-molecule replication chance (scaled by resource factor) |
| `spontaneous.mutationProbability` | 0–1 | of replications, the share that mutate |
| `spontaneous.variation` | 0–1 | magnitude of mutation drift; each child stat ∈ `[parent − v, parent + v]` clamped to `[0, 1]` |
| `output.csv` | path \| null | per-step CSV (header: `step,speciesId,count,spawn,death,repl,mut,var`); omit/null to skip |
| `output.png` | path \| null | PNG line graph; omit/null to skip |

## Engine semantics — deliberate deviations from the spec

The reference pseudocode in `documents/evolution-simulation.md` has a few bugs and ambiguities. The implementation makes these explicit choices:

1. **Spawn comparison** uses `rng < spawnProbability × factor`. The spec's `chance > spawn × factor` is inverted (high random ⇒ no spawn).
2. **Resource factor** `factor = max(0, 1 − N/C)` is recomputed at the **start of each step** and held constant for that step. This is order-independent across species and matches the intent of the spec (which mistakenly computes it once outside the loop).
3. **Death is evaluated before replication** within a step — a molecule cannot both die and reproduce in the same step. Standard ecological-simulation convention.
4. **`variation` is inherited unchanged** by mutants. Only `deathProbability`, `replicationProbability`, and `mutationProbability` are perturbed by `uniform(−variation, +variation)`. Mutating `variation` itself under additive+clamp would push it to 0 or 1 (absorbing boundaries).
5. **`spawnProbability` lives on `Replicator`** (not `Simulation`). The spontaneous template has it > 0; every mutant gets it set to 0 (rule: "secondary mutations have spawn rate = 0").
6. **Aggregate per-species binomial sampling** — each step computes `Binomial(count, p)` per species rather than rolling `Math.random()` per individual molecule. Statistically equivalent at the species level and orders of magnitude faster.

## Non-goals

The optional ideas in the spec are out of scope for this build:

- HTTP server (live state / dataset / image endpoints)
- Per-mutation threading
- Custom end-condition predicates beyond `finalStepCount`

## Project layout

```
src/main/java/sk/blueai/evolution/
├── cli/        Main — argv → ConfigLoader → SimulationEngine.run
├── config/     SimulationConfig, OutputConfig, ConfigLoader (org.json)
├── model/      Replicator (immutable record), Species, SpeciesSnapshot
├── engine/     SimulationEngine, Population, Binomial, Mutator
└── output/     SimulationListener, CompositeListener, CsvLogger, PngGraphRecorder
```
