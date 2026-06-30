/*
 * A Killer Sudoku cage: an irregular group of cells that must
 *   - all hold distinct digits, and
 *   - sum to a target value.
 *
 * Cages partition the grid (every cell belongs to exactly one cage).
 */
package sudoku.killer;

import java.util.Arrays;

public final class Cage {

	/** Cage identifier (stable across the puzzle; usually a small int). */
	public final int id;
	/** Required sum of the digits placed in this cage. */
	public final int sum;
	/** Sorted, distinct cell indices (row-major 0..n²-1). */
	public final int[] cells;

	public Cage(int id, int sum, int[] cells) {
		if (cells == null || cells.length == 0) {
			throw new IllegalArgumentException("cage " + id + " must contain at least one cell");
		}
		if (sum < 1) {
			throw new IllegalArgumentException("cage " + id + " sum must be >= 1");
		}
		int[] sorted = cells.clone();
		Arrays.sort(sorted);
		for (int i = 1; i < sorted.length; i++) {
			if (sorted[i] == sorted[i - 1]) {
				throw new IllegalArgumentException("cage " + id + " has duplicate cell " + sorted[i]);
			}
		}
		this.id = id;
		this.sum = sum;
		this.cells = sorted;
	}

	/** @return number of cells in the cage (= number of digits to place in it). */
	public int size() { return cells.length; }

	@Override
	public String toString() {
		return "Cage{id=" + id + ", sum=" + sum + ", cells=" + Arrays.toString(cells) + "}";
	}
}
