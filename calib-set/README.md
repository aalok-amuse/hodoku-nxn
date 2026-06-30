# Calibration set

Nine Sudoku puzzles in Amuse-Labs-style JSON, hand-selected so every puzzle
receives a **distinct score** under this fork's scoring system. Useful for
verifying a scoring change hasn't silently flattened or reordered the band.

| # | File | Variant | Size | Score |
|---|---|---|---|---:|
| 01 | `01-vanilla-4x4-s16.json` | vanilla | 4×4 | 16 |
| 02 | `02-vanilla-4x4-s32.json` | vanilla | 4×4 | 32 |
| 03 | `03-vanilla-4x4-s162.json` | vanilla | 4×4 | 162 |
| 04 | `04-killer-9x9-s1032.json` | killer | 9×9 | 1,032 |
| 05 | `05-killer-9x9-s6106.json` | killer | 9×9 | 6,106 |
| 06 | `06-killer-9x9-s6590.json` | killer | 9×9 | 6,590 |
| 07 | `07-vanilla-16x16-s64.json` | vanilla | 16×16 | 64 |
| 08 | `08-vanilla-16x16-s144.json` | vanilla | 16×16 | 144 |
| 09 | `09-vanilla-16x16-s224.json` | vanilla | 16×16 | 224 |

All nine scores are pairwise distinct: {16, 32, 64, 144, 162, 224, 1032,
6106, 6590}.

### Notes on the Killer scores

The Killer scorer reports two numbers: `logicalScore` (sum of weights of
techniques applied) and `score` (logical + 5000 if brute force fired). The
filename `sNNNN` is the total `score`.

| File | logical | total | brute-forced? |
|---|---:|---:|:-:|
| 04-killer-9x9-s1032 | 1,032 | 1,032 | no — every cell is a singleton cage |
| 05-killer-9x9-s6106 | 1,106 | 6,106 | yes |
| 06-killer-9x9-s6590 | 1,590 | 6,590 | yes |

### Notes on the 16×16 scores

The upstream Hodoku per-technique scorer is 9×9-calibrated. At 16×16 it
returns a small number of distinct scores (60–250 range, often saturating
to 0 for sparse puzzles). The three picks here (64, 144, 224) correspond to
generated puzzles with 220, 240, and 200 givens respectively — denser
puzzles are the only ones the scorer reliably handles. This is consistent
with the calibration gap documented in the top-level README ("non-9×9
vanilla weights are inherited and unvalidated").

## Format

Vanilla:
```json
{
  "name": "Sudoku",
  "game_data": {
    "rows": <box height>,
    "cols": <box width>,
    "puzzle": "<n*n chars, 0 = empty, 1..n or A..G otherwise>",
    "solmap": "<n*n chars, the solution>"
  }
}
```

Killer:
```json
{
  "name": "Killer Sudoku",
  "game_data": {
    "rows": <box height>,
    "cols": <box width>,
    "solmap": "<n*n chars, the solution>",
    "plevel": <difficulty hint>,
    "clusters": [
      { "sum": <int>, "cells": [<1-based row-major cell indices>] }
    ]
  }
}
```

## Reproducing the scores

Vanilla (uses upstream Hodoku scorer):
```bash
java -cp target/Hodoku.jar sudoku.ScoreEachLine <puzzles.txt>
# prints "<score>\t<puzzle>" per line
```

Killer (uses this fork's `KillerScorer`):
```bash
java -cp target/Hodoku.jar sudoku.BatchSolveKiller calib-set/04*.json ... calib-set/06*.json
# prints "<file>  #<seq>  <Level>[/BF]  logical=<X>  total=<Y>  ..."
```
