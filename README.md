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

## Run — serve mode (web UI)

```powershell
pwsh scripts/mvn.ps1 package   # once
pwsh scripts/serve.ps1         # then open http://localhost:8080
```

Browser form lets you tweak every config field and watch the lineage chart populate live (Server-Sent Events). The container binds port 8080; pass a different host port as the script arg, e.g. `pwsh scripts/serve.ps1 9090`. In serve mode any `output.csv` / `output.png` keys in posted configs are ignored — the chart is the only sink.

Note: the page loads Chart.js from a public CDN, so the browser needs internet access (the server itself does not).

## Config

JSON shape (see [examples/default.json](examples/default.json)):

| key | type | meaning |
| --- | --- | --- |
| `finalStepCount` | int | number of simulation steps to run |
| `crowdingFactor` | int | `C` in `r = r × (1 − N/C)` — the resource ceiling |
| `randomSeed` | long | reproducibility seed (default 0) |
| `threadCount` | int | worker threads for per-species step compute (default 1; `0` = `Runtime.availableProcessors()`). Output is byte-identical across thread counts for a fixed seed. |
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

## Parallelism

When `threadCount > 1`, per-species step work (death sampling, replication sampling, mutation sampling, mutant probability perturbation) runs concurrently on a fixed-size pool. The contract:

- The **crowding factor** `factor = max(0, 1 − N/C)` and the **total population N** that feeds it are computed once at step start and shared read-only by all worker threads — the user-asked "shared context".
- Workers write **nothing** to shared state. Each task returns a `SpeciesStepResult` (deaths, clones, list of mutant specs); a single-threaded commit phase folds results back into `Population` in `activeSnapshot()` order and assigns new species IDs sequentially.
- Each task gets its **own `Random`** seeded deterministically from `(randomSeed, step, speciesId)`. Same seed → byte-identical output regardless of `threadCount`. The `sameSeedSameOutputAcrossThreadCounts` test guards this.
- Spontaneous spawn stays single-threaded (it's one main-RNG draw per step) and runs before the parallel phase.
- Listeners are still notified once per step on the main thread; they don't need to be thread-safe.

Switching from the pre-threading single-RNG engine to per-species seeding changes the exact byte sequence of output for existing configs — same statistical behavior, different draws. Pin your expected outputs accordingly.

## Non-goals

The optional ideas in the spec that remain out of scope for this build:

- Per-mutation-lineage threading (per-*species* threading is supported — see "Parallelism")
- Custom end-condition predicates beyond `finalStepCount`
- Persistent run history (the in-memory `RunRegistry` evicts finished runs after ~10 min)

## Project layout

```
src/main/java/sk/blueai/evolution/
├── cli/        Main — argv → ConfigLoader → SimulationEngine.run, or --serve → WebServer
├── config/     SimulationConfig, OutputConfig, ConfigLoader (org.json)
├── model/      Replicator (immutable record), Species, SpeciesSnapshot
├── engine/     SimulationEngine, Population, Binomial, Mutator
├── output/     SimulationListener, CompositeListener, CsvLogger, PngGraphRecorder
└── web/        WebServer (Javalin), RunRegistry, Run, WebSseListener, SseMessage

src/main/resources/web/
└── index.html, style.css, app.js   (served by Javalin from the classpath)
```
