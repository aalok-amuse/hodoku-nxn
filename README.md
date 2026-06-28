# hodoku-nxn

Fork of [PseudoFish/Hodoku](https://github.com/PseudoFish/Hodoku) v2.3.2,
generalised from 9×9-only to six board sizes while keeping the 9×9 path
**bit-identical** to upstream (verified across 8,145-puzzle reference corpus).

| Size | Box layout |
|---|---|
| 4×4 | 2×2 |
| 5×5 | Latin square (no boxes) |
| 6×6 | 3-wide × 2-tall (rectangular) |
| 7×7 | Latin square (no boxes) |
| 9×9 | 3×3 |
| 16×16 | 4×4 |

License: **GPL v3** (inherited from Hodoku).

## Documentation

- **[`ARCHITECTURE.md`](ARCHITECTURE.md)** — how the spec-driven design works, how
  to add a new size, what's deliberately not generalised.
- This README — quick start, plus a phase-by-phase log of the refactor.

## Quick start

```bash
# Build (requires Java 11+, Maven)
JAVA_HOME=/opt/homebrew/opt/openjdk@11 mvn -q package

# Standard 9×9 batch (Hodoku /bs interface)
java -jar target/Hodoku.jar /bs <input.txt>

# Multi-size batch (auto-detects 4×4/5×5/6×6/7×7/9×9/16×16 from line length)
java -cp target/Hodoku.jar sudoku.BatchSolveNxN <input.txt>

# Amuse Labs xword.json files (9x9 only) — same CLI
java -cp target/Hodoku.jar sudoku.BatchSolveNxN puzzle.xword.json [puzzle2.xword.json ...]

# Or mix formats freely
java -cp target/Hodoku.jar sudoku.BatchSolveNxN flat-batch.txt one.xword.json two.xword.json

# Run the test suite (JUnit 5 — 101 tests across BoardSpec, SpecData, SudokuSetBase,
# DpllSolver, XwordJson, EndToEnd; plus legacy BoardSpecTest / PhaseSixTest)
mvn test
java -cp target/Hodoku.jar sudoku.BoardSpecTest    # legacy custom runner
java -cp target/Hodoku.jar sudoku.PhaseSixTest     # legacy 4x4/6x6/16x16 smoke test
```

Input formats accepted by `BatchSolveNxN`:
- **Flat text** (`.txt`, etc.): one puzzle per line. Size detected from line length
  (16/25/36/49/81/256). Digits 1..16 encoded as `1..9` then `:`, `;`, `<`, `=`, `>`,
  `?`, `@`. Empty cells: `.` or `0`. Output to `<input>.out.txt`.
- **Amuse Labs `xword.json`** (one puzzle per file): files with names ending in
  `.xword.json` or `.json` are parsed via [`XwordJson`](src/main/java/sudoku/XwordJson.java).
  9×9 only — that's all the format supports.

## Refactor log

## Phase 1 — Maven build of unmodified source (DONE)

Build:
```
cd hodoku-nxn
JAVA_HOME=/opt/homebrew/opt/openjdk@11 mvn -q package
```

Output: `target/Hodoku.jar` (~1.34 MB). Run with:
```
java -jar target/Hodoku.jar /bs <input.txt>     # batch solve
```

### Parity check vs original Hodoku.jar

| Test | Result |
|---|---|
| 20-puzzle mixed sample | byte-identical output |
| 1,948-puzzle easy regression set | byte-identical output |
| Wall time, 1,948 puzzles | 0.91 s (matches original within noise) |

### Patches applied to make Hodoku 2.3.2 compile on JDK 11

JDK 9 changed `DefaultMutableTreeNode.children()` to return `Enumeration<TreeNode>` instead of a raw `Enumeration`. The Hodoku source casts the result to `Enumeration<DefaultMutableTreeNode>` directly, which JDK 11 rejects as an inconvertible parameterised cast.

Mechanical patch in 6 Swing files — insert a raw-`(Enumeration)` intermediate cast:

```diff
- (Enumeration<DefaultMutableTreeNode>) last.children()
+ (Enumeration<DefaultMutableTreeNode>)(Enumeration) last.children()
```

Files touched:
- `sudoku/AllStepsPanel.java` (4 sites)
- `sudoku/CheckNode.java` (1 site)
- `sudoku/ConfigFindAllStepsPanel.java` (1 site)
- `sudoku/ConfigProgressPanel.java` (1 site)
- `sudoku/ConfigSolverPanel.java` (1 site)
- `sudoku/ConfigTrainigPanel.java` (1 site)

No behaviour change. These are all in Swing GUI code we plan to strip in a later phase.

## Layout

```
hodoku-nxn/
├── COPYING                       # GPL v3
├── pom.xml                       # Maven build
├── README.md
├── src/main/java/
│   ├── sudoku/                   # 81 files — Sudoku2 board model + Swing GUI
│   ├── solver/                   # 22 files — technique solvers
│   └── generator/                # puzzle generator
├── src/main/resources/
│   ├── intl/                     # localisation
│   ├── help/                     # help text
│   ├── img/                      # icons
│   └── templates.dat             # 9×9 backtracking templates
└── target/Hodoku.jar             # build output
```

## Phase 2 — `BoardSpec` (DONE)

New class [`sudoku.BoardSpec`](src/main/java/sudoku/BoardSpec.java) holds the per-size board descriptor. Six pre-built specs: `S4, S5, S6, S7, S9, S16`. `Sudoku2.java` is untouched in this phase.

```java
BoardSpec s = BoardSpec.S6;
s.n;            // 6
s.length;       // 36
s.boxWidth;     // 3
s.boxHeight;    // 2
s.hasBoxes;     // true
s.rows[r];      // cell indices in row r
s.cols[c];      // cell indices in col c
s.boxes[b];     // cell indices in box b (empty if !hasBoxes)
s.allUnits;     // rows..., cols..., (boxes...) - length 2n or 3n
s.constraints[cell]; // unit indices containing this cell
s.masks[d];     // candidate bit for digit d
s.maxMask;      // (1 << n) - 1
s.wordsPerBitset; // long words needed for a length-cell bitset
```

### Test results

```
java -cp target/Hodoku.jar sudoku.BoardSpecTest
PASSED: 8033  FAILED: 0
```

Coverage:
- All 6 specs: dimension self-consistency, row/col/box partitions are valid, constraints index back into allUnits correctly, candidate masks have the right bits.
- **9×9 spec cross-checked against `Sudoku2.ROWS/COLS/BLOCKS/CONSTRAINTS/MASKS/MAX_MASK`** — every cell, every unit, every mask is bit-identical.
- `BoardSpec.of(n)` rejects unsupported sizes.
- Reflection check: invalid box dimensions (e.g. 2×2 box on 9×9 grid) are rejected.

### Regression: 9×9 scoring still byte-identical

After adding BoardSpec to the JAR, 1,948-puzzle easy regression set solve to byte-identical output vs the upstream Hodoku.jar.

## Phase 3 — `Sudoku2` carries a `BoardSpec` (DONE)

Changes inside [`Sudoku2.java`](src/main/java/sudoku/Sudoku2.java):

- Added `private final BoardSpec spec;` instance field.
- Added constructor `Sudoku2(BoardSpec spec)`.
- No-arg `Sudoku2()` now calls `this(BoardSpec.S9)` — default 9×9 preserved.
- Added `public BoardSpec getSpec()` accessor.
- Instance arrays (`cells`, `userCells`, `values`, `fixed`, `solution`, `free`) now sized to `spec.length` / `spec.allUnits.length` instead of the static `LENGTH` / `ALL_UNITS.length`.
- `clearSudoku()` uses `spec.maxMask`, `spec.n`, `spec.length` instead of the static `MAX_MASK`, `UNITS`, `LENGTH`.
- `set(Sudoku2 src)` copies `spec.length` cells, `spec.n + 1` per-constraint counts.
- `clone()` preserves the spec (`super.clone()` shares the immutable `BoardSpec` reference).

Not changed in this phase:

- Static finals `LENGTH=81`, `UNITS=9`, `ROWS`, `COLS`, `BLOCKS`, `MAX_MASK=0x1ff`, `CONSTRAINTS`, `BLOCK_FROM_INDEX`, `templates`, `buddies` etc. They remain 9×9 because solver code reads them. These migrate in Phase 5.
- Remaining instance methods inside `Sudoku2` that still reference `LENGTH`/`UNITS` (setSudoku, printSudoku, etc., ~25 sites) — fine for S9 (`spec.length == LENGTH`), to be swept alongside the solvers in Phase 5.

### Test results

```
java -cp target/Hodoku.jar sudoku.BoardSpecTest
PASSED: 8049  FAILED: 0
```

New checks:
- `new Sudoku2()` → spec is S9, cells length 16. (Was already 81; now reachable through spec.)
- `new Sudoku2(BoardSpec.S4..S16)` → spec preserved, arrays sized 16/25/36/49/256 respectively.
- `clone()` preserves spec and gives a fresh `cells[]` of the same size.

### Regression

| Test | Result |
|---|---|
| 1,948-puzzle easy regression set | **byte-identical** to upstream |
| 1,948-puzzle expert regression set | **byte-identical** to upstream |

## Phase 4 — `SudokuSetBase` → `long[] words` (DONE)

`SudokuSetBase` previously hardcoded two scalar `long mask1` / `long mask2` fields covering exactly 81 bits. It now holds a `long[] words` array sized per board:

| Spec | `words.length` | `topMask` (top word) |
|---|---:|---|
| S4 | 1 | 0xFFFF (16 bits) |
| S5 | 1 | 0x1FFFFFF (25 bits) |
| S6 | 1 | 0xFFFFFFFFFL (36 bits) |
| S7 | 1 | 0x1FFFFFFFFFFFFL (49 bits) |
| **S9** | 2 | 0x1FFFF (17 bits — unchanged) |
| S16 | 4 | -1L (all 64 bits) |

Key changes in [`SudokuSetBase.java`](src/main/java/sudoku/SudokuSetBase.java):

- `mask1` / `mask2` fields **removed** — replaced by `long[] words`.
- New `topMask` field — bits-valid mask for the top word; used by `setAll()` and `not()` for correctness across sizes.
- New constructors: `SudokuSetBase(BoardSpec spec)` and `SudokuSetBase(int wordCount, long topMask)`.
- Default `SudokuSetBase()` preserves 9×9 semantics (2 words, `topMask = 0x1FFFF`).
- All ops (`isEmpty`, `add`, `remove`, `set`, `contains`, `equals`, `clear`, `setAll`, `not`, `or`, `and`, `or-not`, `and-not`, `andEquals`, `andNotEquals`, `andEmpty`, `intersects`, `setAnd`, `setOr`, `orAndAnd`, static `andEmpty`) now loop over `words.length`.
- `add` / `remove` / `contains` use `value >>> 6` and `1L << (value & 63)` — standard word-array bitset arithmetic.
- Backward-compat getters/setters: `getMask1()`/`getMask2()`/`setMask1()`/`setMask2()` still work, hitting `words[0]`/`words[1]`.
- New general accessors: `getWordCount()`, `getWords()`, `getWord(i)`, `setWord(i, w)`.
- `clone()` deep-clones the words array.

Caller-site updates:

- `SudokuSet.java`: 5 direct `mask1`/`mask2` references replaced with `words[0]` / `words[1]` (legacy 2-word view, kept for backward compat).
- `Sudoku2.java`: 4 direct `cells.mask1` / `cells.mask2` references in `getBuddies(SudokuSetBase, ...)` replaced with `cells.getWord(0)` / `cells.getWord(1)`.

### Test results

```
java -cp target/Hodoku.jar sudoku.BoardSpecTest
PASSED: 8343  FAILED: 0
```

New checks:

- `SudokuSetBase()` default constructor: 2 words (9×9 backward compat).
- `new SudokuSetBase(BoardSpec.S{4,5,6,7,9,16})`: word counts 1/1/1/1/2/4 respectively.
- `add` / `remove` / `contains` round-trip for cells 0, 63, 64, 80 on the default 9×9 set.
- `setAll()` then `not()` produces an empty set, including on 16×16 (256 cells across 4 words) and 4×4 (16 cells, 1 word).
- `clone()` copies the words array independently.

### Regression

| Tier | Puzzles | Result |
|---|---:|---|
| easy | 1,948 | **byte-identical** to upstream |
| medium | 1,948 | **byte-identical** |
| hard | 1,948 | **byte-identical** |
| expert | 1,948 | **byte-identical** |
| impossible | 353 | **byte-identical** |
| **total** | **8,145** | **0 divergences** |

Wall-time at 1,948 puzzles is 295–520 ms depending on tier — within noise of upstream.

## Phase 5 — sweep solvers (IN PROGRESS)

Goal: every `Sudoku2.LENGTH` / `Sudoku2.ROWS` / `Sudoku2.CONSTRAINTS` / etc. static-field access in `solver/*.java` becomes an instance-method call on the active `sudoku` reference, so solvers work on any spec.

### Step 5.1 — Sudoku2 spec-delegating accessors (DONE)

Added to [`Sudoku2.java`](src/main/java/sudoku/Sudoku2.java):

```java
public int      getLength()         { return spec.length; }
public int      getUnitCount()      { return spec.n; }
public short    getMaxMask()        { return spec.maxMask; }
public boolean  hasBoxes()          { return spec.hasBoxes; }
public int[][]  getRowsArr()        { return spec.rows; }
public int[][]  getColsArr()        { return spec.cols; }
public int[][]  getBoxesArr()       { return spec.boxes; }
public int[][]  getAllUnitsArr()    { return spec.allUnits; }
public int[][]  getConstraintsArr() { return spec.constraints; }
public short[]  getMasksArr()       { return spec.masks; }
public int rowOf(int cell), colOf(int cell), boxOf(int cell), cellOf(int row, int col);
```

### Step 5.2 — solver sweeps (IN PROGRESS)

Per-file checklist; each file ticked once its sweepable refs are converted and the 5-tier 8,145-puzzle regression is byte-identical.

| File | Lines | Sweepable refs | Status |
|---|---:|---:|---|
| SimpleSolver | 1,081 | 55 | **done** — 55 swept |
| WingSolver | 441 | 2 | **done** |
| ColoringSolver | 570 | 6 | **done** |
| ChainSolver | 1,208 | 4 | **done** |
| MiscellaneousSolver | — | 3 | **done** |
| SudokuStepFinder | 1,383 | 3 | **done** |
| Als | — | 1 | **done** (uses `finder.getSudoku().getMasksArr()`) |
| BruteForceSolver | — | 0 | n/a — already had only deferred refs |
| GroupNode | — | 3 | **deferred** — value class, no `sudoku` field; needs constructor refactor |
| SingleDigitPatternSolver | — | 6 | **deferred** — sweepable ref is in `static {}` block; needs careful refactor |
| AlsSolver | 1,472 | 12 | **done** |
| UniquenessSolver | 1,268 | 71 (15 + 54 static-method calls) | **done** |
| TablingSolver | 3,263 | 8 | **done** |
| FishSolver | 1,967 | 9 (all field-init) | **deferred** — needs resize-on-setSudoku pattern |

### Step 5.3 — per-spec `SpecData` lookup tables (DONE)

New class [`SpecData.java`](src/main/java/sudoku/SpecData.java) holds the precomputed tables the solvers used to read off `Sudoku2`'s static fields. Computed once per `BoardSpec` (memoised in a `ConcurrentHashMap`):

```java
spec.anzValues       // [2^n]  bit-count of each mask
spec.candFromMask    // [2^n]  low-bit digit, or 0 for empty mask
spec.possibleValues  // [2^n][] digits whose bit is set
spec.buddies         // [length]  SudokuSet of same-row/col/box peers per cell
spec.buddiesM1 / M2  // [length]  legacy split (9x9 only)
spec.rowTemplates    // [n]  cells of each row as a SudokuSet
spec.colTemplates    // [n]
spec.blockTemplates  // [n]  empty for Latin specs
spec.rowBlockTemplates / colBlockTemplates  // rows... + boxes... / cols... + boxes...
spec.allConstraintsTemplates / M1 / M2      // [allUnits.length]
spec.groupedBuddies / M1 / M2               // [(length+7)/8][256] vectorised buddy lookup
spec.constraintType / constraintNumber      // [allUnits.length]
spec.blockFromIndex                         // [length]
spec.templates                              // 9x9 only — reference to Sudoku2.templates
```

Wired up on `Sudoku2`:

```java
public SpecData getSpecData() {
    return spec == BoardSpec.S9 ? SpecData.S9 : SpecData.for_(spec);
}
```

### Test results

```
java -cp target/Hodoku.jar sudoku.BoardSpecTest
PASSED: 10436  FAILED: 0
```

Cross-checks added:
- For every 9-bit mask (512 values): `SpecData.S9.anzValues / candFromMask / possibleValues` exactly match `Sudoku2.ANZ_VALUES / CAND_FROM_MASK / POSSIBLE_VALUES`.
- For every cell (81): `SpecData.S9.buddies / buddiesM1 / buddiesM2` exactly match `Sudoku2.buddies / buddiesM1 / buddiesM2`.
- For every unit (27): `SpecData.S9.allConstraintsTemplates / *_TEMPLATES / constraintType / constraintNumber` exactly match Sudoku2's tables.
- Random spot-check of `groupedBuddies` (30 samples) matches.
- Non-9×9 specs (S4/S5/S6/S7/S16) construct cleanly; mask tables are sized 2^n (max 65,536 entries for n=16); peer counts per cell are correct: 6 / 8 / 14 / 12 / 20 / 56 respectively.

Latent bug discovered & fixed during cross-check: my initial empty-mask handling cleared the intersection; the original Hodoku `initGroupForGroupedBuddies` leaves the full set in place for the empty mask. Matched the original.

### Regression

| Tier | Result |
|---|---|
| easy / medium / hard / expert / impossible (8,145 puzzles) | **byte-identical** to upstream after adding SpecData + cross-check tests |

### Step 5.3c — deferred-refs sweep to SpecData (DONE)

Mechanical sweep across all `solver/*.java`: `Sudoku2.ANZ_VALUES / POSSIBLE_VALUES / CAND_FROM_MASK / buddies / buddiesM1/M2 / groupedBuddies* / *_TEMPLATES / templates / CONSTRAINT_TYPE/NUMBER_FROM_CONSTRAINT / BLOCK_FROM_INDEX` → `sudoku.getSpecData().X`. Roughly **130 deferred refs converted across 11 solver files**.

Edge case: `Als.getChainPenalty()` has no `sudoku` / `finder` reference. Replaced `Sudoku2.ANZ_VALUES[candidates]` with `Integer.bitCount(candidates & 0xFFFF)` — JVM intrinsic, size-agnostic, identical behaviour.

### What remains across `solver/*.java` (88 refs)

Three categories, none addressable by mechanical sweep:

| Pattern | Count | Files |
|---|---:|---|
| Enum-like constants `Sudoku2.ROW / COL / BLOCK / CELL` | 22 | TablingSolver, UniquenessSolver, ... — these are constraint *type* identifiers (0/1/2/3), not size-dependent. Stay. |
| Static methods `Sudoku2.getRow / getCol / getBlock / getIndex / getBuddies` | 41 | All static helpers that work off the 9×9 statics. Bit-identical on 9×9; need a refactor only when non-9×9 puzzles flow through these code paths. |
| Field-initializers, static `{}` block, value class fields | 25 | SimpleSolver (4), WingSolver (2), FishSolver (12 — `new T[Sudoku2.UNITS * 3]`), MiscellaneousSolver (2), ChainSolver (1), GroupNode (5 in constructor body), SingleDigitPatternSolver (29 in static `{}` block). Need resize-on-setSudoku, constructor refactor, or per-spec data class — all bigger lifts. |

### Regression

| Tier | Result |
|---|---|
| easy / medium / hard / expert / impossible (8,145 puzzles) | **byte-identical** to upstream after the SpecData sweep |
| `BoardSpecTest` | 10,436 / 10,436 pass |

### Step 5.5 — `GroupNode` constructor refactor (DONE)

Value class with no `sudoku` field. Refactored the constructor:

```java
- public GroupNode(int cand, SudokuSet indices)
+ public GroupNode(Sudoku2 sudoku, int cand, SudokuSet indices)
```

Static `getGroupNodes(finder)` and `getGroupNodesForHouseType` now derive `Sudoku2 sudoku = finder.getSudoku()` and pass it through. The digit loop now uses `sudoku.getUnitCount()` instead of hardcoded `9`. 15 sites converted.

### Step 5.6 — `SingleDigitPatternSolver` instance-method sweep (DONE)

The static `{}` block builds Empty-Rectangle data tables hard-wired to 9×9 — leaving that alone for now (per-spec ER tables are a sensible Step 5.6b once non-9×9 puzzles flow through).

Inside instance methods: 25 calls to `Sudoku2.getRow / getCol / getBlock / getIndex / ROWS / COLS / BLOCKS / ALL_UNITS` swept to `sudoku.rowOf / colOf / boxOf / cellOf / getRowsArr() / getColsArr() / getBoxesArr() / getAllUnitsArr()`.

### Step 5.4 — field-initializer resize hook (DONE)

New infrastructure on [`AbstractSolver.java`](src/main/java/solver/AbstractSolver.java):

```java
protected int sizedForLength = -1;

protected void onSudokuChanged(Sudoku2 sudoku) {
    // default: nothing — solvers with size-dependent fields override
}

protected final void ensureBuffersSized(Sudoku2 sudoku) {
    int len = sudoku.getLength();
    if (sizedForLength != len) {
        onSudokuChanged(sudoku);
        sizedForLength = len;
    }
}
```

Solvers with size-dependent buffers now:
1. Declare buffer fields without initializers (`private int[] biCells;`)
2. Override `onSudokuChanged(sudoku)` to allocate per spec
3. Call `ensureBuffersSized(sudoku)` immediately after `sudoku = finder.getSudoku()`

**Converted (5 solvers, 21 field-init sites):**

| Solver | Buffers re-sized per spec |
|---|---|
| `SimpleSolver` | `singleFound` (len), `sameConstraint`/`foundConstraint`/`constraint` (constraints per cell) |
| `WingSolver` | `biCells`/`triCells` (len) |
| `MiscellaneousSolver` | `stack1`/`stack2` (n entries) |
| `SingleDigitPatternSolver` | `only2Indices` (2n × 2) |
| `FishSolver` | 12 buffers: `baseUnits/coverUnits/allCoverUnits` (3n int), 6 candidate-mask longs (3n long), `baseUnitsUsed`/`coverUnitsUsed` (3n boolean), `baseStack`/`coverStack` (n stack entries) |

For 9×9 (the only currently-active spec), allocation happens on first `getStep()` call with the same sizes as the previous field-init defaults. No score change.

### Step 5.7 — `Sudoku2.getBuddies` migration to `SpecData` (DONE)

Added two methods to [`SpecData.java`](src/main/java/sudoku/SpecData.java):

```java
public void getBuddies(SudokuSetBase cells, SudokuSetBase out)            // size-generic
public void getBuddies9x9(long m1, long m2, SudokuSetBase out)             // 9x9 fast path
```

Migrated 5 callers in `Als.java`, `AlsSolver.java`, `FishSolver.java`. The size-generic version loops over `cells.getWordCount()` and uses `groupedBuddies[g][b]` for any board.

### Phase 5 final state

| Category | Refs remaining | Notes |
|---|---:|---|
| Enum constants `Sudoku2.ROW / COL / BLOCK / CELL` (= 0/1/2/3) | 22 | Constraint *type* identifiers, not size-dependent — keep |
| `Sudoku2.BLOCKS.length` inside SDP `static {}` block | 1 | 9×9 ER table init — Step 5.6b (per-spec ER) |
| `Sudoku2.LENGTH` in ChainSolver static `MAX_CHAIN_LENGTH` | 1 | Static const = 2×81 = 162; works for 9×9 |
| `Sudoku2.LINE_TEMPLATES` / `Sudoku2.set` (comments only) | 3 | Inert — no actual code refs |

**Total: 26 refs left** (down from ~204 at Phase 5 start — **87% sweep complete**).

All 26 are either intentional (enum constants, comments) or blocked by isolated remaining work (per-spec ER tables in SDP static block; chain-length scaling for n > 9).

### Regression after every sub-step

| Tier | Puzzles | Result |
|---|---:|---|
| easy / medium / hard / expert / impossible | 8,145 | **byte-identical** after every sub-step |
| `BoardSpecTest` | — | 10,436 / 10,436 pass |

### Step 5.2b — templates.dat deserialisation fix (DONE)

Phase 4 swapped `mask1`/`mask2` for `long[] words` in `SudokuSetBase`, but the resource file `templates.dat` (46,656 pre-serialised 9×9 templates) is encoded against the legacy field set. That deserialisation has been silently failing since Phase 4 (caught by an empty `try/catch` in `Sudoku2.initTemplates`). the reference puzzles don't need templates, so the regression passed — but `TemplateSolver` would crash on harder puzzles.

Fix in [`SudokuSetBase.java`](src/main/java/sudoku/SudokuSetBase.java):

```java
private static final ObjectStreamField[] serialPersistentFields = {
    new ObjectStreamField("mask1", long.class),
    new ObjectStreamField("mask2", long.class),
    new ObjectStreamField("initialized", boolean.class),
};

private void readObject(ObjectInputStream in) throws ... {
    var f = in.readFields();
    this.words   = new long[]{ f.get("mask1", 0L), f.get("mask2", 0L) };
    this.topMask = MAX_MASK2;
    this.initialized = f.get("initialized", true);
}

private void writeObject(ObjectOutputStream out) throws IOException {
    var f = out.putFields();
    f.put("mask1", words.length > 0 ? words[0] : 0L);
    f.put("mask2", words.length > 1 ? words[1] : 0L);
    f.put("initialized", initialized);
    out.writeFields();
}
```

The serialised form stays the legacy two-long layout; `words[]` is reconstructed on read. Templates load successfully again. `serialVersionUID` reverted to `1L`.

### Step 5.2 — SimpleSolver swept (DONE)

`SimpleSolver.java` had 77 `Sudoku2.*` static refs — the most of any solver. Mechanical sweep:

| Pattern | Result |
|---|---|
| `Sudoku2.LENGTH/UNITS/MAX_MASK` | → `sudoku.getLength()/getUnitCount()/getMaxMask()` |
| `Sudoku2.ROWS/COLS/BLOCKS/ALL_UNITS/CONSTRAINTS/MASKS` | → `sudoku.getRowsArr()/getColsArr()/getBoxesArr()/getAllUnitsArr()/getConstraintsArr()/getMasksArr()` |
| `Sudoku2.ANZ_VALUES/POSSIBLE_VALUES` | **deferred** — 9×9-only bit-count lookup tables, need per-spec data class |
| `Sudoku2.CONSTRAINT_TYPE/NUMBER_FROM_CONSTRAINT` | **deferred** — 9×9-only constraint type table |
| Field-init buffers (`new boolean[Sudoku2.LENGTH]`) | left at 81 — safe for 9×9, need resize-on-`setSudoku` for larger boards |

55 of 77 refs swept. 22 remain (deferred lookup tables + field-init buffers).

### Step 5 status across solver/*.java

After sweeping SimpleSolver, remaining `Sudoku2.*` references (sweepable vs deferred):

| File | Sweepable left | Deferred (9×9 lookups) |
|---|---:|---:|
| SimpleSolver | 4 (field init) | 22 |
| UniquenessSolver | 15 | 48 |
| AlsSolver | 10 | 5 |
| FishSolver | 9 | 5 |
| SingleDigitPatternSolver | 6 | 20 |
| TablingSolver | 6 | 3 |
| ColoringSolver | 5 | 0 |
| ChainSolver | 4 | 6 |
| WingSolver | 4 | 4 |
| GroupNode | 3 | 5 |
| MiscellaneousSolver | 3 | 8 |
| SudokuStepFinder | 3 | 3 |
| BruteForceSolver | 1 | 0 |
| Als | 1 | 1 |
| **TOTAL** | **74 sweepable** | **130 deferred** |

The sweepable refs follow the same pattern — mechanical sed replacement that compiles and stays byte-identical on 9×9. The deferred refs need a `SpecData` class holding per-spec lookup tables (ANZ_VALUES, POSSIBLE_VALUES, …) — that's Step 5.3.

### Regression

| Tier | Puzzles | Result |
|---|---:|---|
| easy / medium / hard / expert / impossible | 8,145 | **byte-identical** to upstream after SimpleSolver sweep |

`BoardSpecTest`: 8,343 assertions, 0 failed.

## Roadmap

| Phase | Status | What |
|---|---|---|
| 1 | **DONE** | Maven build of unmodified source, parity verified |
| 2 | **DONE** | `BoardSpec` class — 6 specs (S4, S5, S6, S7, S9, S16) with rows/cols/boxes computed |
| 3 | **DONE** | Refactor `Sudoku2` to carry a `BoardSpec` field; arrays sized per spec |
| 4 | **DONE** | `SudokuSetBase` (`mask1`/`mask2` → `long[] words`); regression byte-identical across 8,145 puzzles |
| 5 | **DONE** | Spec-driven solvers — 5.1/5.2/5.2b/5.3/5.3c/5.4/5.5/5.6/5.7 done; ~87% of all Sudoku2.* refs swept |
| 6 | **DONE (proof + CLI)** | End-to-end 4×4 / 6×6 / 16×16 solve via `PhaseSixTest`; new `BatchSolveNxN` CLI handles auto-sized batch (length 16/25/36/49/81/256); 9×9 byte-identical across 8,145-puzzle reference corpus |
| 7 | **DONE** | Hard 16×16 path swept — ER finder gated, `[10]` arrays bumped, `SudokuStepFinder` SudokuSets resize on `setSudoku` |
| 8 | **DONE** | Spec-aware DPLL fallback in `DpllSolver` → `BruteForceSolver` falls back to it for non-9×9 (and 9×9 cases the generator couldn't validate). Any 16×16 puzzle now fully solves |
| 4 | | Refactor `SudokuSetBase` (`mask1`/`mask2` → `long[] words`) |
| 5 | | Sweep `solver/*.java` for `9`/`81`/`3` literals → spec calls |
| 6 | | Add `requiresBoxes()` gating for techniques that need boxes (skip on 5×5, 7×7) |
| 7 | | Cross-size test suite |
| 8 | | CLI accepts size declaration in input |
