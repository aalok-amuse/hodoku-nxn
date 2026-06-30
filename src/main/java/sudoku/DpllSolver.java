/*
 * Spec-aware recursive-backtracking solver. Used as a fallback when the
 * 9x9-wired SudokuGenerator can't validate a non-9x9 solution.
 *
 * Reads the current cell values from the given Sudoku2, attempts to fill all
 * empty cells, and returns the completed values (or null if no solution exists
 * / multiple solutions found, depending on mode).
 *
 * Algorithm: pick the empty cell with the fewest remaining candidates (MRV),
 * try each in turn, recurse.
 *
 * Performance is fine for 9x9 (microseconds) and adequate for 16x16 (typically
 * milliseconds; pathological cases can blow up, but no worse than the legacy
 * generator).
 */
package sudoku;

public final class DpllSolver {

	/** Result of a counted solve — solution plus brute-force search-effort counters. */
	public static final class Result {
		public final int[] solution;   // null if unsolvable
		public final int branches;     // cells the search descended into
		public final int backtracks;   // cells the search backed out of
		Result(int[] solution, int branches, int backtracks) {
			this.solution = solution;
			this.branches = branches;
			this.backtracks = backtracks;
		}
	}

	/** Like {@link #solve} but also counts branches/backtracks for difficulty diagnostics. */
	public static Result solveCounted(Sudoku2 sudoku) {
		BoardSpec spec = sudoku.getSpec();
		int[] values = sudoku.getValues().clone();
		int[] cands = new int[spec.length];
		int allMask = spec.maxMask & 0xFFFF;
		for (int i = 0; i < spec.length; i++) {
			cands[i] = values[i] == 0 ? allMask : 0;
		}
		for (int i = 0; i < spec.length; i++) {
			if (values[i] != 0) {
				int bit = 1 << (values[i] - 1);
				for (int unitIdx : spec.constraints[i]) {
					for (int peer : spec.allUnits[unitIdx]) {
						if (peer != i) cands[peer] &= ~bit;
					}
				}
			}
		}
		for (int i = 0; i < spec.length; i++) {
			if (values[i] == 0 && cands[i] == 0) return new Result(null, 0, 0);
		}
		int[] counters = new int[2];
		boolean ok = solveRecCounted(spec, values, cands, counters);
		return new Result(ok ? values : null, counters[0], counters[1]);
	}

	private static boolean solveRecCounted(BoardSpec spec, int[] values, int[] cands, int[] counters) {
		int bestIdx = -1, bestCount = Integer.MAX_VALUE;
		for (int i = 0; i < spec.length; i++) {
			if (values[i] != 0) continue;
			int m = cands[i];
			if (m == 0) return false;
			int c = Integer.bitCount(m);
			if (c < bestCount) { bestCount = c; bestIdx = i; if (c == 1) break; }
		}
		if (bestIdx == -1) return true;
		int mask = cands[bestIdx];
		while (mask != 0) {
			int bit = Integer.lowestOneBit(mask);
			mask ^= bit;
			int digit = Integer.numberOfTrailingZeros(bit) + 1;
			int oldVal = values[bestIdx];
			values[bestIdx] = digit;
			int[] candsCopy = cands.clone();
			counters[0]++; // branch
			boolean ok = true;
			for (int unitIdx : spec.constraints[bestIdx]) {
				if (!ok) break;
				for (int peer : spec.allUnits[unitIdx]) {
					if (peer == bestIdx) continue;
					int prev = cands[peer];
					int next = prev & ~bit;
					if (prev != next) {
						cands[peer] = next;
						if (values[peer] == 0 && next == 0) { ok = false; break; }
					}
				}
			}
			cands[bestIdx] = 0;
			if (ok && solveRecCounted(spec, values, cands, counters)) return true;
			counters[1]++; // backtrack
			values[bestIdx] = oldVal;
			System.arraycopy(candsCopy, 0, cands, 0, cands.length);
		}
		return false;
	}

	/** @return solved values[length] array, or null if unsolvable. */
	public static int[] solve(Sudoku2 sudoku) {
		BoardSpec spec = sudoku.getSpec();
		int[] values = sudoku.getValues().clone();
		// Candidate mask per cell — start with all-allowed for empty cells, fixed
		// bit for filled cells (so MRV ignores them).
		int[] cands = new int[spec.length];
		int allMask = spec.maxMask & 0xFFFF;
		for (int i = 0; i < spec.length; i++) {
			cands[i] = values[i] == 0 ? allMask : 0;
		}
		// Propagate givens.
		for (int i = 0; i < spec.length; i++) {
			if (values[i] != 0) {
				int bit = 1 << (values[i] - 1);
				for (int unitIdx : spec.constraints[i]) {
					for (int peer : spec.allUnits[unitIdx]) {
						if (peer != i) cands[peer] &= ~bit;
					}
				}
			}
		}
		// Quick rejection: any empty cell with no candidates?
		for (int i = 0; i < spec.length; i++) {
			if (values[i] == 0 && cands[i] == 0) return null;
		}
		return solveRec(spec, values, cands) ? values : null;
	}

	/** Recursive DPLL with MRV cell selection. Mutates {@code values}/{@code cands} in place. */
	private static boolean solveRec(BoardSpec spec, int[] values, int[] cands) {
		// Find an empty cell with the smallest non-zero candidate mask (MRV).
		int bestIdx = -1, bestCount = Integer.MAX_VALUE;
		for (int i = 0; i < spec.length; i++) {
			if (values[i] != 0) continue;
			int m = cands[i];
			if (m == 0) return false;
			int c = Integer.bitCount(m);
			if (c < bestCount) {
				bestCount = c;
				bestIdx = i;
				if (c == 1) break; // can't do better
			}
		}
		if (bestIdx == -1) return true; // all filled
		int mask = cands[bestIdx];
		// Try each candidate in turn.
		while (mask != 0) {
			int bit = Integer.lowestOneBit(mask);
			mask ^= bit;
			int digit = Integer.numberOfTrailingZeros(bit) + 1;
			// Snapshot state for undo.
			int oldVal = values[bestIdx];
			values[bestIdx] = digit;
			int[] candsCopy = cands.clone(); // small enough to clone; keeps undo simple
			boolean ok = true;
			for (int unitIdx : spec.constraints[bestIdx]) {
				if (!ok) break;
				for (int peer : spec.allUnits[unitIdx]) {
					if (peer == bestIdx) continue;
					int prev = cands[peer];
					int next = prev & ~bit;
					if (prev != next) {
						cands[peer] = next;
						// Empty-cell + zero-candidates = contradiction.
						if (values[peer] == 0 && next == 0) { ok = false; break; }
					}
				}
			}
			cands[bestIdx] = 0;
			if (ok && solveRec(spec, values, cands)) return true;
			// Undo.
			values[bestIdx] = oldVal;
			System.arraycopy(candsCopy, 0, cands, 0, cands.length);
		}
		return false;
	}
}
