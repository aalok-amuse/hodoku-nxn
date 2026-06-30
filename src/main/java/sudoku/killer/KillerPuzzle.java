/*
 * A Killer Sudoku puzzle: a {@link BoardSpec} (size + box layout) plus a
 * cage decomposition. Optionally carries givens (most Killers have none —
 * the cages alone constrain the puzzle).
 *
 * Cages must partition the grid: every cell belongs to exactly one cage.
 * That invariant is checked at construction.
 */
package sudoku.killer;

import sudoku.BoardSpec;

import java.util.Arrays;

public final class KillerPuzzle {

	public final BoardSpec spec;
	public final Cage[] cages;
	/**
	 * Per-cell cage index (into {@link #cages}). cellCage[cell] = i means that
	 * cell belongs to cages[i].
	 */
	public final int[] cellCage;
	/** Optional givens. givens[cell] = 0 means empty; 1..n means a placed digit. */
	public final int[] givens;

	public KillerPuzzle(BoardSpec spec, Cage[] cages, int[] givens) {
		this.spec = spec;
		this.cages = cages.clone();
		this.cellCage = new int[spec.length];
		Arrays.fill(this.cellCage, -1);
		for (int i = 0; i < cages.length; i++) {
			for (int cell : cages[i].cells) {
				if (cell < 0 || cell >= spec.length) {
					throw new IllegalArgumentException("cage " + cages[i].id
					    + " references out-of-range cell " + cell);
				}
				if (this.cellCage[cell] != -1) {
					throw new IllegalArgumentException("cell " + cell
					    + " belongs to two cages: " + cages[this.cellCage[cell]].id
					    + " and " + cages[i].id);
				}
				this.cellCage[cell] = i;
			}
			if (cages[i].size() > spec.n) {
				throw new IllegalArgumentException("cage " + cages[i].id
				    + " has more cells (" + cages[i].size() + ") than digits ("
				    + spec.n + "); cannot hold distinct values");
			}
		}
		// All cells must be in some cage.
		for (int cell = 0; cell < spec.length; cell++) {
			if (this.cellCage[cell] == -1) {
				throw new IllegalArgumentException("cell " + cell + " is not in any cage");
			}
		}
		// Total cage sums must equal n * (1 + 2 + ... + n) for the puzzle to be feasible.
		long totalCageSum = 0;
		for (Cage c : cages) totalCageSum += c.sum;
		long expected = (long) spec.n * spec.n * (spec.n + 1) / 2;
		if (totalCageSum != expected) {
			throw new IllegalArgumentException("cage sums total " + totalCageSum
			    + " but expected " + expected + " for n=" + spec.n);
		}
		// Givens (optional).
		if (givens == null) {
			this.givens = new int[spec.length];
		} else {
			if (givens.length != spec.length) {
				throw new IllegalArgumentException("givens length must match spec.length");
			}
			this.givens = givens.clone();
			for (int v : this.givens) {
				if (v < 0 || v > spec.n) {
					throw new IllegalArgumentException("given out of range 0.." + spec.n + ": " + v);
				}
			}
		}
	}

	public KillerPuzzle(BoardSpec spec, Cage[] cages) {
		this(spec, cages, null);
	}

	/** @return the cage containing the given cell. */
	public Cage cageOf(int cell) {
		return cages[cellCage[cell]];
	}
}
