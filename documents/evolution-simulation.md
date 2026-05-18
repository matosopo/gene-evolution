# Simple Gene Evolution Simulation

## Idea

Náhodným vznikom molekuly, ktorá dokázala samu seba skopírovať zo zdrojov v jej okolí, sa začali preteky o to, aká jej verzia sa dokáže namnožiť najviac.

Skúsime ich zápolenie nasimulovať a zistiť:

- ktorá verzia prevláda po danom „čase“
- či sa vývoj na nejakej verzii stabilizuje

---

# Input Parameters

## Core Parameters

### Spawn Rate (`s`)
Ako často vznikne molekula schopná samoreplikácie.

### Death Rate (`d`)
Ako často sa molekula rozpadne na nefunkčné diely.

### Replication Rate (`r`)
Ako často molekula vytvorí kópiu samej seba.

#### Resource Factor

Potenciál replikácie je obmedzený dostupnými zdrojmi:

```text
r = r × (1 - N / C)
```

Where:

- `N` = aktuálny počet existujúcich molekúl
- `C` = arbitrary crowding factor

### Mutation Rate
Pravdepodobnosť, s akou bude nová kópia obsahovať mierne odlišné štatistiky.

### Variation Rate
Určuje, ako veľmi budú nové štatistiky kolísať pri mutácii.

### Number of Steps / End Condition
Počet krokov simulácie alebo podmienka ukončenia.

---

# Rules

- Nové mutácie dedia pôvodné štatistiky, ale s miernou randomizáciou.
- Sekundárne mutácie nemajú spawn rate (`spawn rate = 0`).

---

# Output

## Simulation Log

Záznam o počte jednotlivých „druhov“ v každom časovom kroku vrátane:

- spawn rate
- death rate
- replication rate
- mutation rate
- ID druhu

### Possible Output Formats

- text file
- database
- graph

### Graph Representation

- `x` = time step
- `y` = number of existing replicators per species

### Additional Export

- graph image (`.png`)

---

# Additional Ideas

- Vývoj každej mutácie môže bežať v samostatnom vlákne.
- Niekoľko simulácií môže bežať paralelne.
- Web server:
  - accepts simulation requests
  - returns:
    - live state
    - resulting dataset
    - resulting graph/image

---

# Simulation Parameters

## Thesis

Nature may allow emergence of only ONE type of spontaneous replicator.

We need to define:

1. What are its stats?
2. How often does it emerge?
3. How abundant are resources required for emergence?

Crowding factor should affect emergence probability.

---

# Predefined Parameters

```java
float spawnRate;
int crowdingFactor;
float defaultDeathProbability;
float defaultReplicationProbability;
float defaultMutationProbability;
float defaultVariationProbability;
int finalStepCount;
```

---

# Monitored Variables

```java
int S; // current step
```

## Population Table

```text
key   -> species
value -> species count
```

### Total Population

```java
int N = sum of all species counts;
```

---

# Pseudocode

```java
public class Simulation {
    public int finalStepCount;
    public int crowdingFactor; // resource abundance
    public float spawnProbability;
    public Replicator spontaneousReplicator;
}

public class Replicator {
    public float deathProbability;
    public float replicationProbability;
    public float mutationProbability;
    public float variation;
}
```

---

# Simulation Loop

```java
public static void simulation(String simulationParameters) {

    // parse JSON into Simulation object
    Simulation simulation = new Simulation();

    int step = 0;
    int population = 0;
    double chance;

    int replicationFactor =
        1 - population / simulation.crowdingFactor;

    DynamicArray<Replicator> species =
        new DynamicArray<Replicator>();

    while (step < simulation.finalStepCount) {

        chance = Math.random();

        if (chance >
            simulation.spawnProbability * replicationFactor) {

            // create spontaneous replicator
        }

        /*
        foreach species from previous steps:
            foreach member of species:

                calculate:
                - death
                - replication
                - mutation

                if mutation:
                    apply variation

                adjust:
                - population
                - replicationFactor
        */
    }
}
```
