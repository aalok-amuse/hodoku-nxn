# hodoku-nxn — Architecture

A fork of [PseudoFish/Hodoku](https://github.com/PseudoFish/Hodoku) 2.3.2
generalised from 9×9-only to six board sizes (4×4, 5×5, 6×6, 7×7, 9×9, 16×16)
while keeping the 9×9 path **bit-identical** to upstream.

Audience: anyone modifying the solver or adding a new size.

---

## 1. Why this exists

The original Hodoku is hardwired to 9×9 in many small ways: `LENGTH = 81`,
`UNITS = 9`, `for (j = 1; j <= 9; j++)`, `cells[index] & MASKS[value]` with
`short` arithmetic, `Sudoku2.buddies` as a static 81-entry array, and the
brute-force fallback that delegates to a 9×9-only DPLL inside the generator.
None of those choices are wrong — they're fine for 9×9. They just have to
unwind cleanly to support other sizes.

This fork sweeps each layer in turn, gated by a 8,145-puzzle byte-identical
regression on the reference corpus. After every commit, the same `/bs` batch on the
same Hodoku.jar produces the same output as upstream, byte-for-byte.

---

## 2. Layer cake

```
┌────────────────────────────────────────────────────────────────┐
│ BatchSolveNxN, PhaseSixTest               ← CLI / acceptance   │
├────────────────────────────────────────────────────────────────┤
│ SudokuSolver, SudokuStepFinder            ← scheduling         │
├────────────────────────────────────────────────────────────────┤
│ Per-technique solvers                                          │
│   SimpleSolver, FishSolver, ChainSolver,                       │
│   AlsSolver, ColoringSolver, …                                 │
│   BruteForceSolver  ──▶  DpllSolver       ← spec-aware fallback│
├────────────────────────────────────────────────────────────────┤
│ Sudoku2          — per-puzzle mutable state                    │
│ SpecData         — per-spec precomputed lookup tables          │
│ BoardSpec        — per-spec dimensions (immutable)             │
├────────────────────────────────────────────────────────────────┤
│ SudokuSet / SudokuSetBase   — cell-membership bitsets          │
│   (long[] words, sized per spec)                               │
└────────────────────────────────────────────────────────────────┘
```

Anything above the line knows about the active sudoku via `Sudoku2`.
Anything below has no concept of "puzzle" — just dimensions and bits.

---

## 3. The three new types

### `BoardSpec` — immutable board descriptor

Six pre-built instances; lookup via `BoardSpec.of(n)`.

```java
BoardSpec.S4   // 4×4, 2×2 boxes
BoardSpec.S5   // 5×5, Latin square (no boxes)
BoardSpec.S6   // 6×6, 3-wide × 2-tall boxes (rectangular)
BoardSpec.S7   // 7×7, Latin square
BoardSpec.S9   // 9×9, 3×3 boxes (standard)
BoardSpec.S16  // 16×16, 4×4 boxes
```

Each carries:
- `n` (side length / number of digits)
- `length` = `n * n`
- `boxWidth`, `boxHeight`, `hasBoxes`
- `rows[][]`, `cols[][]`, `boxes[][]` — unit decomposition
- `allUnits[][]` — flattened rows∥cols∥boxes (length `2n` for Latin, `3n` with boxes)
- `constraints[][]` — for each cell, the unit indices containing it
- `masks[]` (short) — `masks[d] = 1 << (d-1)` for `d ∈ 1..n`
- `maxMask` (short) — `(1 << n) - 1`
- `wordsPerBitset` — `(length + 63) >>> 6`
- `rowOf(c)`, `colOf(c)`, `boxOf(c)`, `cellOf(r, c)` — accessors

The bitset width drops out from `n`:

| n  | length | words | maxMask  |
|---:|-------:|------:|---------:|
| 4  | 16     | 1     | 0x000F   |
| 5  | 25     | 1     | 0x001F   |
| 6  | 36     | 1     | 0x003F   |
| 7  | 49     | 1     | 0x007F   |
| 9  | 81     | 2     | 0x01FF   |
| 16 | 256    | 4     | 0xFFFF   |

### `SpecData` — per-spec precomputed lookup tables

What `Sudoku2`'s 9×9 static finals were, plus a few new lookups. Computed once
per spec and cached in a `ConcurrentHashMap`. Acquire via `sudoku.getSpecData()`.

```java
spec.anzValues       // [2^n]   bit count per mask
spec.candFromMask    // [2^n]   lowest digit (0 if mask is 0)
spec.possibleValues  // [2^n][] digits whose bit is set
spec.buddies         // [length] SudokuSet of peers per cell
spec.rowTemplates    // [n]     cells of each row as a SudokuSet
spec.colTemplates    // [n]
spec.blockTemplates  // [n], empty for Latin specs
spec.allConstraintsTemplates / M1 / M2  // [allUnits.length]
spec.groupedBuddies / M1 / M2           // [(length+7)/8][256] vectorised buddy lookup
spec.constraintType / constraintNumber
spec.blockFromIndex
spec.templates                          // 9×9 only — reference to Sudoku2.templates
```

Plus two methods for fast peer set computation:

```java
specData.getBuddies(SudokuSetBase cells, SudokuSetBase out);     // generic
specData.getBuddies9x9(long m1, long m2, SudokuSetBase out);     // 9x9 fast path
```

The 9×9 `SpecData` reads its tables directly from `Sudoku2`'s static finals.
This is what keeps the 9×9 path bit-identical: solver code that used to read
`Sudoku2.ANZ_VALUES[mask]` now reads `sudoku.getSpecData().anzValues[mask]`,
which for `S9` is the same `int[]` reference.

### `XwordJson` — minimal Amuse Labs `.xword.json` reader

Standalone parser, no third-party JSON dependency. Extracts only the two
fields `BatchSolveNxN` needs (`box` and `preRevealIdxs`) using a small
bracket-matching scanner that respects string quoting. Outputs the 81-char
Hodoku puzzle string in row-major order, with col-major→row-major flip
(Amuse Labs uses `box[c][r]` whereas Hodoku reads row by row).

```java
String puzzle = XwordJson.fileTo81(Paths.get("puzzle.xword.json"));
// puzzle is now an 81-char string ready for BatchSolveNxN.solveOne()
```

`xword.json` is inherently 9×9 (Amuse Labs Sudoku is fixed-size); the parser
throws if either field isn't a 9×9 nested array. Used automatically by
`BatchSolveNxN` whenever a file argument ends in `.xword.json` or `.json`.

### `DpllSolver` — spec-aware backtracking fallback

Forty lines of recursive MRV-driven DPLL that takes a `Sudoku2` and returns a
solved `int[length]` array, or `null` if the puzzle is unsolvable. Used by
`BruteForceSolver` when the generator's 9×9-only `validSolution()` declines.

The point isn't to replace logical-technique solving — those are still tried
first. The DPLL is the fallback for puzzles the logical pipeline can't finish,
and the *only* path for 4×4 / 5×5 / 6×6 / 7×7 / 16×16 brute force.

---

## 4. How `Sudoku2` carries its spec

`Sudoku2` was the original board representation, with everything sized 81 via
static finals. Now it carries a `final BoardSpec spec` field, and all its
mutable arrays are sized to `spec.length` / `spec.allUnits.length` / `spec.n+1`.

```java
new Sudoku2()                     // defaults to BoardSpec.S9
new Sudoku2(BoardSpec.S16)        // 16×16 instance
```

A handful of instance accessors delegate to spec:

```java
sudoku.getLength()           // spec.length
sudoku.getUnitCount()        // spec.n
sudoku.getMaxMask()          // spec.maxMask
sudoku.getRowsArr() / getColsArr() / getBoxesArr() / getAllUnitsArr() /
       getConstraintsArr() / getMasksArr()
sudoku.rowOf(cell), colOf(cell), boxOf(cell), cellOf(r, c)
sudoku.getSpec()
sudoku.getSpecData()
```

These are what solver code reads. The static finals `Sudoku2.LENGTH` /
`Sudoku2.UNITS` / `Sudoku2.ROWS` / `Sudoku2.BLOCKS` etc. all still exist with
their 9×9 values — needed for 9×9 backward compatibility (some code paths still
go through them) and for `Sudoku2`'s own static helpers (`getRow`, `getCol`,
`getBlock`, `getIndex`) which divide by `UNITS=9`. Those static helpers are
9×9-specific; non-9×9 code uses `sudoku.rowOf()` instead.

---

## 5. How `SudokuSetBase` carries its size

The original used two scalar `long` fields, `mask1` for cells 0–63 and `mask2`
for cells 64–80. The new version uses:

```java
protected long[] words;       // length = (length + 63) >>> 6
protected long topMask;       // valid bits in the top word
```

For 9×9: `words.length = 2`, `topMask = 0x1FFFF` (17 valid bits in the second
word). For 16×16: `words.length = 4`, `topMask = -1L` (all 64 bits valid).

Two constructors carry this:

```java
new SudokuSetBase()                        // 9×9 (backward compat)
new SudokuSetBase(BoardSpec spec)          // sized per spec
new SudokuSetBase(int wordCount, long topMask)  // raw
```

Backward-compat getters `getMask1()` / `getMask2()` are preserved. They hit
`words[0]` / `words[1]`. New code uses `getWord(i)` / `getWordCount()`.

Serialisation back-compat: the legacy `templates.dat` file holds 46,656
serialised `SudokuSetBase` entries written when the class had `mask1` and
`mask2` as the persistent fields. Custom `readObject` / `writeObject` decode
the legacy form and reconstruct the new `words[]`.

---

## 6. Per-solver buffer resizing (`onSudokuChanged`)

Solvers carry size-dependent work buffers (e.g. `SimpleSolver.singleFound[]`,
`FishSolver.baseUnits[]`). These can't be sized at field-init time because the
active sudoku isn't known yet. The pattern: declare uninitialised, override
`onSudokuChanged(Sudoku2)`, allocate per `sudoku.getLength()` / `getUnitCount()`.

`AbstractSolver` provides the hook:

```java
protected void onSudokuChanged(Sudoku2 sudoku) {
    // default: nothing — solvers with size-dependent buffers override
}

protected final void ensureBuffersSized(Sudoku2 sudoku) {
    int len = sudoku.getLength();
    if (sizedForLength != len) {
        onSudokuChanged(sudoku);
        sizedForLength = len;
    }
}
```

Each entry point in a size-dependent solver calls `ensureBuffersSized(sudoku)`
after `sudoku = finder.getSudoku()`. The hook is called at most once per spec
change — typically once per JVM lifetime.

Applied to `SimpleSolver`, `WingSolver`, `MiscellaneousSolver`,
`SingleDigitPatternSolver`, `FishSolver`. Other solvers either have no
size-dependent buffers or use the `[17]`-sized per-candidate pattern (always
big enough).

`SudokuStepFinder` has a similar private `ensureSetsSizedForSpec()` for the
per-candidate `SudokuSet` arrays (`candidates[]`, `positions[]`,
`candidatesAllowed[]`, etc.). It's called from `setSudoku()`.

---

## 7. Bit-identical-to-upstream contract

Held by every commit:

```bash
# All 8,145-puzzle reference corpus across all 5 tiers
for tier in easy medium hard expert impossible; do
  java -jar target/Hodoku.jar /bs input_${tier}.txt
done

# byte-identical to upstream Hodoku.jar's *.out.txt
diff input_${tier}.txt.out.txt upstream/input_${tier}.txt.out.txt
```

This catches subtle divergences early: a missed `& 0xFFFF`, a wrong
constraint base, a propagation order change.

---

## 8. How to add a new size

Suppose you want 12×12 with 3-wide × 4-tall boxes. Two steps:

```java
// 1. Add the BoardSpec instance:
public static final BoardSpec S12 = new BoardSpec(12, 3, 4);

// Inside BoardSpec.of(n):
case 12: return S12;
```

That's it — `SpecData.for_(S12)` computes the per-spec lookup tables on first
use, `Sudoku2(S12)` allocates correctly, every swept solver iterates
`<= sudoku.getUnitCount() = 12`, `DpllSolver` handles the brute force.

Then add a length entry to `BatchSolveNxN.sizeOf()`:

```java
case 144: return 12;   // 12×12 puzzles flow in /bs-style
```

The harder constraints — for `n = 12`, `2^n = 4096` mask-lookup entries; for
larger `n` the table grows. We've capped at 16 because `2^16 = 65,536` is the
last manageable size for `anzValues`/`possibleValues`/`candFromMask` lookups.

A 16×16-style fast path could be opened to `n ≤ 16` for any rectangular box
that tiles. The unmodified `Sudoku2` static helpers (`getRow`, `getCol`,
`getBlock`, `getIndex`) divide by 9 and so don't generalise — but every solver
reads those through `sudoku.rowOf()` etc. now, so they're only used by 9×9
backward-compat code paths.

---

## 9. What we deliberately didn't generalise

Some things stayed 9×9-only because generalising them isn't cleanly
size-parameterised:

- **`SingleDigitPatternSolver.findEmptyRectangle()`** — the ER pattern table
  `erSets[9][9]` describes 9 ER configurations within a 3×3 box. For non-3×3
  boxes the configurations are different; we'd need a per-spec ER catalog.
  Gated off for `n != 9`.
- **`Sudoku2.templates[46,656]`** — pre-serialised single-digit placements.
  46,656 = 6^6 reflects 9×9 row-position combinatorics. Drop entirely for
  non-9×9; `BruteForceSolver` covers what `TemplateSolver` would have.
- **`Sudoku2.getRow / getCol / getBlock / getIndex`** static helpers — divide
  by 9. Used only by 9×9 backward-compat paths; non-9×9 code goes through
  `sudoku.rowOf()` etc. instead.
- **`Sudoku2.buddies` / `buddiesM1` / `buddiesM2`** static arrays — still 9×9.
  Non-9×9 code reads `sudoku.getSpecData().buddies` instead.

---

## 10. Performance notes

| Test | Result |
|---|---|
| 1,948-puzzle easy regression set (9×9) | 300–450 ms total (~0.2 ms each) |
| 4×4 trivial puzzle | ~15 ms (mostly JVM warmup) |
| 6×6 puzzle requiring locked candidates | ~10 ms |
| 9×9 regression-corpus easy | ~1 ms |
| 16×16 with chain reasoning + DPLL fallback | ~120 ms |

The 9×9 path is essentially identical to upstream after every refactor — the
spec-driven indirection adds no measurable cost because `BoardSpec.S9` is
referenced via a single final field and `SpecData.S9` is eager-initialised at
class load.

The only known performance cost: `SudokuSet` operations on 9×9 now loop over
`words.length = 2` instead of inlining `mask1`/`mask2` directly. Microbenchmarks
show a ~5–10% slowdown on synthetic bitset ops but no measurable change at
full-solve granularity.

---

## 11. The licence

GPL v3, inherited from Hodoku.
