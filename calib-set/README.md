# Calibration set

Ten Sudoku puzzles in Amuse-Labs-style JSON, for ranking-system calibration.

| # | File | Variant | Size | Notes |
|---|---|---|---|---|
| 01 | `01-vanilla-4x4-easy.json` | vanilla | 4×4 | 11 givens |
| 02 | `02-vanilla-4x4-medium.json` | vanilla | 4×4 | 8 givens |
| 03 | `03-vanilla-4x4-medium.json` | vanilla | 4×4 | 6 givens, alt solution |
| 04 | `04-vanilla-4x4-harder.json` | vanilla | 4×4 | 7 sparse givens |
| 05 | `05-killer-4x4-easy.json` | killer | 4×4 | 8 two-cell cages, alternating 3/7 |
| 06 | `06-killer-4x4-medium.json` | killer | 4×4 | 8 vertical-pair cages |
| 07 | `07-killer-4x4-mixed.json` | killer | 4×4 | mixed sizes incl. L-tromino + singletons |
| 08 | `08-killer-9x9-medium.json` | killer | 9×9 | 32 cages, standard 3×3 boxes |
| 09 | `09-vanilla-9x9-easy.json` | vanilla | 9×9 | low-tier puzzle from a labeled corpus |
| 10 | `10-vanilla-6x6-medium.json` | vanilla | 6×6 | 3-wide × 2-tall rectangular boxes |

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
      { "sum": <int>, "cells": [<1-based cell indices>] },
      ...
    ]
  }
}
```

Cell indices in `cells` are 1-based, row-major (cell 1 = row 0 col 0,
cell `n+1` = row 1 col 0, etc.). `rows` / `cols` are the box dimensions;
for a Latin-square-only variant (5×5, 7×7) both are 0.

## Sanity checks

All ten verified:

```
$ java -cp target/Hodoku.jar sudoku.BatchSolveKiller calib-set/05*.json ... calib-set/08*.json
05-killer-4x4-easy.json   EASY/BF    matches-solmap
06-killer-4x4-medium.json EASY/BF    matches-solmap
07-killer-4x4-mixed.json  EASY       matches-solmap
08-killer-9x9-medium.json HARD/BF    matches-solmap
```

Vanilla files are self-consistent: re-solving the `puzzle` field
reproduces `solmap`.
