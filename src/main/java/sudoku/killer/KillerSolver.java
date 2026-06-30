/*
 * Killer Sudoku solver — backtracking with three propagation passes:
 *
 *   1. Standard Sudoku constraints: each row, column, and (if hasBoxes) box
 *      holds each digit exactly once.
 *   2. Cage uniqueness: no digit repeats within a cage.
 *   3. Cage sum: cells in a cage sum to the cage's target.
 *
 * The search uses MRV (pick the empty cell with the fewest candidates) and
 * propagates each placement by removing the placed digit from row/col/box/cage
 * peers and tightening the cage's remaining-sum window.
 *
 * Returns {@code int[length]} solution or {@code null} if unsolvable.
 *
 * Also reports the number of branches explored — a crude difficulty proxy
 * (more branches = more search = harder puzzle for a brute-force solver).
 */
package sudoku.killer;

import sudoku.BoardSpec;

public final class KillerSolver {

	/** A solve result with the filled grid and search-effort counters. */
	public static final class Result {
		public final int[] solution;   // null if unsolvable
		public final int branches;     // number of cells the search descended into
		public final int backtracks;   // number of cells the search backed out of
		public final long elapsedMs;

		Result(int[] solution, int branches, int backtracks, long elapsedMs) {
			this.solution = solution;
			this.branches = branches;
			this.backtracks = backtracks;
			this.elapsedMs = elapsedMs;
		}
	}

	public static Result solve(KillerPuzzle p) {
		long t0 = System.nanoTime();
		BoardSpec spec = p.spec;
		int n = spec.n, len = spec.length;
		int allMask = spec.maxMask & 0xFFFF;

		int[] values = p.givens.clone();
		int[] cands = new int[len];
		for (int i = 0; i < len; i++) {
			cands[i] = (values[i] == 0) ? allMask : 0;
		}

		// Per-cage tracker: remaining sum needed and remaining cells to fill.
		int[] cageSumRemaining = new int[p.cages.length];
		int[] cageCellsRemaining = new int[p.cages.length];
		int[] cageDigitsUsed = new int[p.cages.length];  // bitmask of digits already in cage
		for (int ci = 0; ci < p.cages.length; ci++) {
			cageSumRemaining[ci] = p.cages[ci].sum;
			cageCellsRemaining[ci] = p.cages[ci].size();
		}

		// Apply givens.
		for (int cell = 0; cell < len; cell++) {
			int v = values[cell];
			if (v != 0) {
				if (!applyPlacement(spec, p, cell, v, cands, cageSumRemaining,
				                    cageCellsRemaining, cageDigitsUsed)) {
					return new Result(null, 0, 0, (System.nanoTime() - t0) / 1_000_000);
				}
			}
		}

		// Propagate cage-sum constraints to candidate masks before the search.
		if (!propagateCageBounds(spec, p, values, cands, cageSumRemaining,
		                         cageCellsRemaining, cageDigitsUsed)) {
			return new Result(null, 0, 0, (System.nanoTime() - t0) / 1_000_000);
		}

		int[] counters = new int[2]; // [0] = branches, [1] = backtracks
		boolean ok = recurse(spec, p, values, cands, cageSumRemaining,
		                     cageCellsRemaining, cageDigitsUsed, counters);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		return new Result(ok ? values : null, counters[0], counters[1], ms);
	}

	// -----------------------------------------------------------------
	// Recursion + propagation
	// -----------------------------------------------------------------

	private static boolean recurse(BoardSpec spec, KillerPuzzle p, int[] values, int[] cands,
	                                int[] cageSumRemaining, int[] cageCellsRemaining,
	                                int[] cageDigitsUsed, int[] counters) {
		int bestIdx = -1, bestCount = Integer.MAX_VALUE;
		for (int i = 0; i < spec.length; i++) {
			if (values[i] != 0) continue;
			int m = cands[i];
			if (m == 0) return false;
			int c = Integer.bitCount(m);
			if (c < bestCount) {
				bestCount = c;
				bestIdx = i;
				if (c == 1) break;
			}
		}
		if (bestIdx == -1) return true;
		counters[0]++;
		int mask = cands[bestIdx];
		while (mask != 0) {
			int bit = Integer.lowestOneBit(mask);
			mask ^= bit;
			int digit = Integer.numberOfTrailingZeros(bit) + 1;

			int[] candsCopy = cands.clone();
			int[] cageSumCopy = cageSumRemaining.clone();
			int[] cageCellsCopy = cageCellsRemaining.clone();
			int[] cageDigitsCopy = cageDigitsUsed.clone();
			values[bestIdx] = digit;
			if (applyPlacement(spec, p, bestIdx, digit, cands,
			                   cageSumRemaining, cageCellsRemaining, cageDigitsUsed)
			    && propagateCageBounds(spec, p, values, cands,
			                           cageSumRemaining, cageCellsRemaining, cageDigitsUsed)
			    && recurse(spec, p, values, cands, cageSumRemaining,
			               cageCellsRemaining, cageDigitsUsed, counters)) {
				return true;
			}
			values[bestIdx] = 0;
			System.arraycopy(candsCopy, 0, cands, 0, cands.length);
			System.arraycopy(cageSumCopy, 0, cageSumRemaining, 0, cageSumRemaining.length);
			System.arraycopy(cageCellsCopy, 0, cageCellsRemaining, 0, cageCellsRemaining.length);
			System.arraycopy(cageDigitsCopy, 0, cageDigitsUsed, 0, cageDigitsUsed.length);
			counters[1]++;
		}
		return false;
	}

	/**
	 * Place a digit in a cell: enforce row/col/box uniqueness, cage uniqueness,
	 * and update the cage's running sum / cells-remaining trackers.
	 *
	 * @return false on immediate contradiction.
	 */
	private static boolean applyPlacement(BoardSpec spec, KillerPuzzle p, int cell, int digit,
	                                       int[] cands,
	                                       int[] cageSumRemaining,
	                                       int[] cageCellsRemaining,
	                                       int[] cageDigitsUsed) {
		int bit = 1 << (digit - 1);
		int cageIdx = p.cellCage[cell];
		if ((cageDigitsUsed[cageIdx] & bit) != 0) {
			return false; // cage uniqueness violated
		}
		cageDigitsUsed[cageIdx] |= bit;
		cageSumRemaining[cageIdx] -= digit;
		cageCellsRemaining[cageIdx]--;
		if (cageSumRemaining[cageIdx] < 0) return false;
		if (cageCellsRemaining[cageIdx] == 0 && cageSumRemaining[cageIdx] != 0) return false;

		// Remove digit from row/col/box peers.
		for (int unitIdx : spec.constraints[cell]) {
			for (int peer : spec.allUnits[unitIdx]) {
				if (peer == cell) continue;
				int prev = cands[peer];
				int next = prev & ~bit;
				if (prev != next) {
					cands[peer] = next;
					if (next == 0 && p.givens[peer] == 0) {
						// Note: we need values too, but defer to caller's loop.
					}
				}
			}
		}
		// Remove digit from cage peers (no duplicates inside a cage).
		for (int cageCell : p.cages[cageIdx].cells) {
			if (cageCell == cell) continue;
			cands[cageCell] &= ~bit;
		}
		cands[cell] = 0; // cell is now filled
		return true;
	}

	/**
	 * For each cage with empty cells remaining, derive sum-based bounds on its
	 * candidates: every cell's candidate digit must lie within
	 * [minPossible..maxPossible] given the remaining sum and remaining cells.
	 *
	 * Example: cage of 3 cells summing to 6, all cells empty → candidates are
	 * {1,2,3} (since 1+2+3=6). Knock out 4..9 from each cage cell.
	 */
	private static boolean propagateCageBounds(BoardSpec spec, KillerPuzzle p,
	                                            int[] values, int[] cands,
	                                            int[] cageSumRemaining,
	                                            int[] cageCellsRemaining,
	                                            int[] cageDigitsUsed) {
		int n = spec.n;
		for (int ci = 0; ci < p.cages.length; ci++) {
			int cellsLeft = cageCellsRemaining[ci];
			if (cellsLeft == 0) continue;
			int sumLeft = cageSumRemaining[ci];
			// In the cells remaining, we place cellsLeft distinct digits not in cageDigitsUsed,
			// summing to sumLeft. Each chosen digit d satisfies:
			//   d >= sumLeft - sumOfTop(cellsLeft - 1)
			//   d <= sumLeft - sumOfBottom(cellsLeft - 1)
			// where sumOfTop(k) = sum of k largest available digits, sumOfBottom(k) = sum of
			// k smallest available digits.
			int availMask = (~cageDigitsUsed[ci]) & ((1 << n) - 1);
			int[] avail = unpack(availMask);
			if (avail.length < cellsLeft) return false;
			int sumTop = 0, sumBottom = 0;
			for (int k = 0; k < cellsLeft - 1; k++) {
				sumTop += avail[avail.length - 1 - k];
				sumBottom += avail[k];
			}
			int minDigit = sumLeft - sumTop;
			int maxDigit = sumLeft - sumBottom;
			if (minDigit > maxDigit) return false;
			int allowedMask = 0;
			for (int d : avail) {
				if (d >= minDigit && d <= maxDigit) {
					allowedMask |= 1 << (d - 1);
				}
			}
			if (allowedMask == 0) return false;
			// Intersect each cage cell's candidates with allowedMask.
			for (int cell : p.cages[ci].cells) {
				if (values[cell] != 0) continue;
				cands[cell] &= allowedMask;
				if (cands[cell] == 0) return false;
			}
		}
		return true;
	}

	/** Return digits 1..n whose bit is set in mask, sorted ascending. */
	private static int[] unpack(int mask) {
		int[] out = new int[Integer.bitCount(mask)];
		int idx = 0;
		while (mask != 0) {
			int bit = Integer.lowestOneBit(mask);
			out[idx++] = Integer.numberOfTrailingZeros(bit) + 1;
			mask ^= bit;
		}
		return out;
	}
}
