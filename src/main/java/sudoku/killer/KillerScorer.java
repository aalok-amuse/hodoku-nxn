/*
 * Hodoku-style scoring solver for Killer Sudoku.
 *
 * Tries techniques in increasing order of difficulty. Each step adds the
 * technique's weight to the running score; the puzzle's final level is the
 * highest level any of its steps required.
 *
 * Techniques implemented (cheapest first):
 *
 *   1. NAKED_SINGLE       — cell with only one candidate left
 *   2. HIDDEN_SINGLE      — digit can only go in one cell of a unit (row/col/box)
 *   3. CAGE_UNIQUENESS    — digit already placed in a cage can be removed from
 *                            the cage's other cells (handled during placement,
 *                            counted at scoring time when it directly forces a
 *                            cell via the next pass of NAKED_SINGLE)
 *   4. CAGE_SUBSET        — cage's remaining sum + digits-used + cells-left
 *                            admits exactly one combination → cage cells
 *                            constrained to that combination
 *   5. LOCKED_CANDIDATES  — a digit's candidates within a cage all share a
 *                            row/col/box → digit removed from the rest of that
 *                            row/col/box
 *   6. CAGE_45_INNIE      — single cell of a house (row/col/box) covered by
 *                            cages that otherwise sit entirely inside the
 *                            house; the missing cell is forced by the 45-rule
 *
 * If logical techniques exhaust before the puzzle is solved, the scorer falls
 * back to DPLL (heavy BRUTE_FORCE penalty) so the puzzle is still ranked.
 */
package sudoku.killer;

import sudoku.BoardSpec;

import java.util.ArrayList;
import java.util.List;

public final class KillerScorer {

	/** A scored solve result. */
	public static final class Result {
		public final int[] solution;                 // null if unsolvable
		/** Score from logical techniques only (excludes BRUTE_FORCE penalty). */
		public final int logicalScore;
		/** Total score: logical + BRUTE_FORCE penalty (if used). */
		public final int score;
		/** Level reached by logical techniques alone. */
		public final KillerTechnique.Level logicalLevel;
		/** Overall level: logical level, or EXTREME if brute force was needed. */
		public final KillerTechnique.Level level;
		public final List<KillerStep> steps;
		public final long elapsedMs;
		public final boolean bruteForced;            // true if logical solving was incomplete

		Result(int[] solution, int logicalScore, KillerTechnique.Level logicalLevel,
		       List<KillerStep> steps, long elapsedMs, boolean bruteForced) {
			this.solution = solution;
			this.logicalScore = logicalScore;
			this.logicalLevel = logicalLevel;
			this.steps = steps;
			this.elapsedMs = elapsedMs;
			this.bruteForced = bruteForced;
			this.score = bruteForced ? logicalScore + KillerTechnique.BRUTE_FORCE.weight : logicalScore;
			this.level = bruteForced ? KillerTechnique.Level.EXTREME : logicalLevel;
		}
	}

	private final BoardSpec spec;
	private final KillerPuzzle p;
	private final int n;
	private final int len;

	private final int[] values;
	private final int[] cands;
	private final int[] cageDigitsUsed;
	private final int[] cageSumRemaining;
	private final int[] cageCellsRemaining;

	private int score = 0;
	private KillerTechnique.Level level = KillerTechnique.Level.EASY;
	private final List<KillerStep> steps = new ArrayList<>();

	private KillerScorer(KillerPuzzle puzzle) {
		this.p = puzzle;
		this.spec = puzzle.spec;
		this.n = spec.n;
		this.len = spec.length;
		this.values = puzzle.givens.clone();
		this.cands = new int[len];
		int allMask = spec.maxMask & 0xFFFF;
		for (int i = 0; i < len; i++) {
			cands[i] = (values[i] == 0) ? allMask : 0;
		}
		this.cageDigitsUsed = new int[p.cages.length];
		this.cageSumRemaining = new int[p.cages.length];
		this.cageCellsRemaining = new int[p.cages.length];
		for (int ci = 0; ci < p.cages.length; ci++) {
			cageSumRemaining[ci] = p.cages[ci].sum;
			cageCellsRemaining[ci] = p.cages[ci].size();
		}
	}

	public static Result solve(KillerPuzzle puzzle) {
		long t0 = System.nanoTime();
		KillerScorer s = new KillerScorer(puzzle);

		// Seed: propagate givens.
		for (int i = 0; i < s.len; i++) {
			int v = s.values[i];
			if (v != 0) {
				if (!s.placeWithoutScoring(i, v)) {
					return new Result(null, 0, KillerTechnique.Level.EXTREME,
					                  s.steps, ms(t0), false);
				}
			}
		}

		// (Skipping the cheap propagateCageBounds pass — that work is now done
		// by the scored CAGE_SUBSET technique, which uses the full combo-union
		// rather than just min/max bounds.)

		// Logical-techniques loop.
		while (!s.isSolved()) {
			boolean fired = false;
			KillerStep step;
			if ((step = s.findNakedSingle())     != null) { s.applyStep(step); fired = true; }
			else if ((step = s.findHiddenSingle())   != null) { s.applyStep(step); fired = true; }
			else if ((step = s.findCageSubsetStep()) != null) { s.applyStep(step); fired = true; }
			else if ((step = s.findLockedStep())     != null) { s.applyStep(step); fired = true; }
			else if ((step = s.find45RuleInnie())    != null) { s.applyStep(step); fired = true; }
			if (!fired) break;
		}

		// Bump the logical level if cumulative score exceeds the current band (Hodoku
		// does this too: a puzzle with many Medium-level steps can total into the
		// Hard band even though no single step was Hard.)
		KillerTechnique.Level byScore = KillerTechnique.Level.forScore(s.score);
		if (byScore.ordinal() > s.level.ordinal()) {
			s.level = byScore;
		}

		if (s.isSolved()) {
			return new Result(s.values, s.score, s.level, s.steps, ms(t0), false);
		}

		// Brute-force fallback.
		KillerSolver.Result dpll = KillerSolver.solve(rebuildAsKillerPuzzle(s));
		if (dpll.solution == null) {
			return new Result(null, s.score, s.level, s.steps, ms(t0), false);
		}
		s.steps.add(new KillerStep(KillerTechnique.BRUTE_FORCE, -1, 0));
		// Adopt the DPLL solution as the final values.
		for (int i = 0; i < s.len; i++) s.values[i] = dpll.solution[i];
		return new Result(s.values, s.score, s.level, s.steps, ms(t0), true);
	}

	private static long ms(long t0) { return (System.nanoTime() - t0) / 1_000_000; }

	// -------------------------------------------------------------------
	// State queries / mutators
	// -------------------------------------------------------------------

	private boolean isSolved() {
		for (int i = 0; i < len; i++) if (values[i] == 0) return false;
		return true;
	}

	private void applyStep(KillerStep step) {
		steps.add(step);
		score += step.technique.weight;
		if (step.technique.level.ordinal() > level.ordinal()) {
			level = step.technique.level;
		}
		if (step.cell >= 0 && step.digit > 0) {
			placeWithoutScoring(step.cell, step.digit);
		}
	}

	/**
	 * Place a digit, propagate row/col/box/cage uniqueness, update cage trackers.
	 * Returns false on contradiction.
	 */
	private boolean placeWithoutScoring(int cell, int digit) {
		int bit = 1 << (digit - 1);
		int cageIdx = p.cellCage[cell];
		if ((cageDigitsUsed[cageIdx] & bit) != 0) return false;
		cageDigitsUsed[cageIdx] |= bit;
		cageSumRemaining[cageIdx] -= digit;
		cageCellsRemaining[cageIdx]--;
		if (cageSumRemaining[cageIdx] < 0) return false;
		if (cageCellsRemaining[cageIdx] == 0 && cageSumRemaining[cageIdx] != 0) return false;
		values[cell] = digit;
		cands[cell] = 0;
		// Row/col/box peers
		for (int unitIdx : spec.constraints[cell]) {
			for (int peer : spec.allUnits[unitIdx]) {
				if (peer == cell) continue;
				cands[peer] &= ~bit;
				if (values[peer] == 0 && cands[peer] == 0) return false;
			}
		}
		// Cage peers
		for (int peer : p.cages[cageIdx].cells) {
			if (peer == cell) continue;
			cands[peer] &= ~bit;
			if (values[peer] == 0 && cands[peer] == 0) return false;
		}
		return true;
	}

	// -------------------------------------------------------------------
	// Techniques
	// -------------------------------------------------------------------

	private KillerStep findNakedSingle() {
		for (int cell = 0; cell < len; cell++) {
			if (values[cell] != 0) continue;
			int m = cands[cell];
			if (Integer.bitCount(m) == 1) {
				int digit = Integer.numberOfTrailingZeros(m) + 1;
				return new KillerStep(KillerTechnique.NAKED_SINGLE, cell, digit);
			}
		}
		return null;
	}

	private KillerStep findHiddenSingle() {
		for (int[] unit : spec.allUnits) {
			for (int digit = 1; digit <= n; digit++) {
				int bit = 1 << (digit - 1);
				int found = -1;
				boolean alreadyPlaced = false;
				for (int cell : unit) {
					if (values[cell] == digit) { alreadyPlaced = true; break; }
					if (values[cell] == 0 && (cands[cell] & bit) != 0) {
						if (found != -1) { found = -2; break; }
						found = cell;
					}
				}
				if (!alreadyPlaced && found >= 0) {
					return new KillerStep(KillerTechnique.HIDDEN_SINGLE, found, digit);
				}
			}
		}
		return null;
	}

	/**
	 * Cage Subset: for each cage, find the union of all valid digit combinations
	 * that fill the cage's remaining cells with the right sum. Intersect each
	 * cage cell's candidates with that union — any digit not in any valid combo
	 * can't appear in that cell. If the intersection changes a cell's candidates,
	 * emit a CAGE_SUBSET step (which may or may not also force the cell to a
	 * single value, which the next pass of NAKED_SINGLE will pick up).
	 *
	 * This is correct: every valid completion of the puzzle places digits in the
	 * cage from one of these combos, so a digit excluded from all combos cannot
	 * appear anywhere in the cage.
	 */
	private KillerStep findCageSubsetStep() {
		for (int ci = 0; ci < p.cages.length; ci++) {
			int cellsLeft = cageCellsRemaining[ci];
			if (cellsLeft == 0) continue;
			int sumLeft = cageSumRemaining[ci];
			int availMask = (~cageDigitsUsed[ci]) & ((1 << n) - 1);
			int[] available = unpack(availMask);
			int comboUnion = 0;
			int comboCount = enumerateCombosUnion(available, 0, cellsLeft, sumLeft, 0,
			                                       new int[]{ 0 });
			if (comboCount == 0) continue;
			comboUnion = computeComboUnion(available, cellsLeft, sumLeft);
			// Apply: each cage cell's candidates intersect with comboUnion.
			for (int cell : p.cages[ci].cells) {
				if (values[cell] != 0) continue;
				int newCands = cands[cell] & comboUnion;
				if (newCands != cands[cell]) {
					// Real elimination — emit a step.
					int digit = 0;
					if (Integer.bitCount(newCands) == 1) {
						digit = Integer.numberOfTrailingZeros(newCands) + 1;
					}
					// Apply eliminations to all cage cells before returning, so a
					// single CAGE_SUBSET step accounts for one cage's full subset
					// pruning rather than one per affected cell.
					for (int cc : p.cages[ci].cells) {
						if (values[cc] != 0) continue;
						cands[cc] &= comboUnion;
					}
					return new KillerStep(KillerTechnique.CAGE_SUBSET, digit == 0 ? -1 : cell, digit);
				}
			}
		}
		return null;
	}

	/** Count combos summing to target with k digits from avail; stops at 2 for short-circuit. */
	private int enumerateCombosUnion(int[] avail, int start, int k, int target,
	                                  int currentMask, int[] count) {
		if (count[0] > 1) return count[0];
		if (k == 0) {
			if (target == 0) count[0]++;
			return count[0];
		}
		for (int i = start; i <= avail.length - k; i++) {
			int d = avail[i];
			if (d > target) break;
			enumerateCombosUnion(avail, i + 1, k - 1, target - d, currentMask | (1 << (d - 1)), count);
			if (count[0] > 1) return count[0];
		}
		return count[0];
	}

	/** Union of all combos' bitmasks. */
	private int computeComboUnion(int[] avail, int k, int target) {
		int[] union = new int[1];
		collectCombosUnion(avail, 0, k, target, 0, union);
		return union[0];
	}

	private void collectCombosUnion(int[] avail, int start, int k, int target,
	                                 int currentMask, int[] union) {
		if (k == 0) {
			if (target == 0) union[0] |= currentMask;
			return;
		}
		for (int i = start; i <= avail.length - k; i++) {
			int d = avail[i];
			if (d > target) break;
			collectCombosUnion(avail, i + 1, k - 1, target - d, currentMask | (1 << (d - 1)), union);
		}
	}

	/**
	 * Locked Candidates within a cage: if a digit must be placed in the cage
	 * (i.e. appears in every valid combo) AND the cage's empty cells with that
	 * digit as a candidate all share a row/col/box, the digit can be removed
	 * from the rest of that row/col/box. If that elimination changes any
	 * cell's candidates, emit a step.
	 *
	 * The "must appear in every combo" guard is essential: without it we'd
	 * wrongly remove a digit from outside cells when the cage could in fact be
	 * filled without using that digit at all.
	 */
	private KillerStep findLockedStep() {
		for (int ci = 0; ci < p.cages.length; ci++) {
			int cellsLeft = cageCellsRemaining[ci];
			if (cellsLeft == 0) continue;
			int sumLeft = cageSumRemaining[ci];
			int availMask = (~cageDigitsUsed[ci]) & ((1 << n) - 1);
			int[] available = unpack(availMask);
			// Only consider digits guaranteed to appear in the cage — i.e. in every combo.
			int remaining = computeComboIntersection(available, cellsLeft, sumLeft);
			while (remaining != 0) {
				int bit = Integer.lowestOneBit(remaining);
				remaining ^= bit;
				int digit = Integer.numberOfTrailingZeros(bit) + 1;
				// Cells in this cage that still have `digit` as a candidate.
				int sharedRow = -2, sharedCol = -2, sharedBox = -2;
				int cellCount = 0;
				for (int cell : p.cages[ci].cells) {
					if (values[cell] == digit) { cellCount = -1; break; }
					if (values[cell] != 0) continue;
					if ((cands[cell] & bit) == 0) continue;
					cellCount++;
					int r = spec.rowOf(cell), c = spec.colOf(cell);
					int b = spec.hasBoxes ? spec.boxOf(cell) : -1;
					sharedRow = (sharedRow == -2) ? r : (sharedRow == r ? r : -1);
					sharedCol = (sharedCol == -2) ? c : (sharedCol == c ? c : -1);
					sharedBox = (sharedBox == -2) ? b : (sharedBox == b ? b : -1);
				}
				if (cellCount <= 0) continue;
				// Apply elimination to shared unit cells outside the cage. Emit a
				// step iff something actually changed.
				int[][] units = new int[3][];
				if (sharedRow >= 0) units[0] = spec.rows[sharedRow];
				if (sharedCol >= 0) units[1] = spec.cols[sharedCol];
				if (sharedBox >= 0) units[2] = spec.boxes[sharedBox];
				boolean changed = false;
				for (int[] unit : units) {
					if (unit == null) continue;
					for (int cell : unit) {
						if (values[cell] != 0) continue;
						if (p.cellCage[cell] == ci) continue;
						if ((cands[cell] & bit) == 0) continue;
						cands[cell] &= ~bit;
						changed = true;
					}
				}
				if (changed) {
					// Report a forced cell if the elimination created one.
					for (int[] unit : units) {
						if (unit == null) continue;
						for (int cell : unit) {
							if (values[cell] != 0) continue;
							if (Integer.bitCount(cands[cell]) == 1) {
								int forced = Integer.numberOfTrailingZeros(cands[cell]) + 1;
								return new KillerStep(KillerTechnique.LOCKED_CANDIDATES, cell, forced);
							}
						}
					}
					return new KillerStep(KillerTechnique.LOCKED_CANDIDATES, -1, 0);
				}
			}
		}
		return null;
	}

	/**
	 * Intersection of bitmasks of all valid k-combos summing to target. A bit
	 * is set iff that digit appears in every combo, i.e. is guaranteed in the
	 * cage. Returns 0 if no valid combo exists.
	 */
	private int computeComboIntersection(int[] avail, int k, int target) {
		int[] result = new int[]{ -1 };  // sentinel: -1 = not yet initialised
		collectCombosIntersection(avail, 0, k, target, 0, result);
		return result[0] == -1 ? 0 : result[0];
	}

	private void collectCombosIntersection(int[] avail, int start, int k, int target,
	                                        int currentMask, int[] result) {
		if (k == 0) {
			if (target == 0) {
				result[0] = (result[0] == -1) ? currentMask : (result[0] & currentMask);
			}
			return;
		}
		for (int i = start; i <= avail.length - k; i++) {
			int d = avail[i];
			if (d > target) break;
			collectCombosIntersection(avail, i + 1, k - 1, target - d,
			                          currentMask | (1 << (d - 1)), result);
		}
	}

	/**
	 * 45-rule single innie: for each house (row/col/box), the digits in the
	 * house sum to target = n(n+1)/2 (= 45 for 9×9). Identify each cage as
	 * "contained" (all cells in the unit) or "straddling" (some cells in, some
	 * out). The straddling cells inside the unit are "innies".
	 *
	 *   target = sum(contained cage sums) + sum(innie cell values)
	 *
	 * When there's exactly ONE innie cell, its value is forced:
	 *   innie value = target - sum(contained cage sums)
	 *
	 * We only fire when the forced value is in 1..n and is a current candidate
	 * of the innie cell.
	 */
	private KillerStep find45RuleInnie() {
		int target = n * (n + 1) / 2;
		for (int[] unit : spec.allUnits) {
			boolean[] inUnit = new boolean[len];
			for (int cell : unit) inUnit[cell] = true;
			int sumContained = 0;
			boolean[] cageSeen = new boolean[p.cages.length];
			int innieCount = 0;
			int innieCell = -1;
			boolean abort = false;
			for (int cell : unit) {
				int ci = p.cellCage[cell];
				if (cageSeen[ci]) continue;
				cageSeen[ci] = true;
				int cellsInside = 0;
				for (int cc : p.cages[ci].cells) {
					if (inUnit[cc]) cellsInside++;
				}
				if (cellsInside == p.cages[ci].size()) {
					sumContained += p.cages[ci].sum;
				} else {
					innieCount += cellsInside;
					if (cellsInside == 1) {
						for (int cc : p.cages[ci].cells) {
							if (inUnit[cc]) innieCell = cc;
						}
					}
					// If a straddling cage contributes >1 innie, we'd need
					// multi-cell innie analysis. Bail on this house.
					if (cellsInside > 1) { abort = true; break; }
				}
			}
			if (abort || innieCount != 1 || innieCell < 0) continue;
			if (values[innieCell] != 0) continue;
			int forced = target - sumContained;
			if (forced < 1 || forced > n) continue;
			int bit = 1 << (forced - 1);
			if ((cands[innieCell] & bit) == 0) continue;
			return new KillerStep(KillerTechnique.CAGE_45_INNIE, innieCell, forced);
		}
		return null;
	}

	// -------------------------------------------------------------------
	// Cage-bound propagation (helper, not a counted step)
	// -------------------------------------------------------------------

	private boolean propagateCageBounds() {
		for (int ci = 0; ci < p.cages.length; ci++) {
			int cellsLeft = cageCellsRemaining[ci];
			if (cellsLeft == 0) continue;
			int sumLeft = cageSumRemaining[ci];
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
				if (d >= minDigit && d <= maxDigit) allowedMask |= 1 << (d - 1);
			}
			if (allowedMask == 0) return false;
			for (int cell : p.cages[ci].cells) {
				if (values[cell] != 0) continue;
				cands[cell] &= allowedMask;
				if (cands[cell] == 0) return false;
			}
		}
		return true;
	}

	// -------------------------------------------------------------------
	// Combination enumeration
	// -------------------------------------------------------------------

	/**
	 * Walk available digits and collect every k-combo summing to {@code target}.
	 * Records each combo as a bitmask in {@code out}. Stops early if more than
	 * one combo is found (caller only uses the result when exactly one exists).
	 */
	private void enumerateCombos(int[] avail, int start, int k, int target,
	                              int currentMask, List<Integer> out) {
		if (out.size() > 1) return;
		if (k == 0) {
			if (target == 0) out.add(currentMask);
			return;
		}
		for (int i = start; i <= avail.length - k; i++) {
			int d = avail[i];
			if (d > target) break;
			enumerateCombos(avail, i + 1, k - 1, target - d,
			                currentMask | (1 << (d - 1)), out);
			if (out.size() > 1) return;
		}
	}

	private int[] unpack(int mask) {
		int[] out = new int[Integer.bitCount(mask)];
		int idx = 0;
		while (mask != 0) {
			int bit = Integer.lowestOneBit(mask);
			out[idx++] = Integer.numberOfTrailingZeros(bit) + 1;
			mask ^= bit;
		}
		return out;
	}

	// -------------------------------------------------------------------
	// Brute force support
	// -------------------------------------------------------------------

	/** Rebuild a KillerPuzzle that reflects the current (partially-solved) state. */
	private static KillerPuzzle rebuildAsKillerPuzzle(KillerScorer s) {
		// Reuse the original cage layout and spec; pass the current values as givens.
		return new KillerPuzzle(s.spec, s.p.cages, s.values);
	}
}
