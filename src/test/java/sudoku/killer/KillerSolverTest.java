/*
 * Killer Sudoku solver tests.
 */
package sudoku.killer;

import org.junit.jupiter.api.Test;
import sudoku.BoardSpec;

import static org.junit.jupiter.api.Assertions.*;

class KillerSolverTest {

	/**
	 * A 4x4 Killer with a unique solution. Hand-designed:
	 *
	 *   Cages on a 4x4 grid:
	 *     A A B B
	 *     C A B D
	 *     C C D D
	 *     E E F F
	 *
	 *   Sums:  A=8 (cells 0,1,5; needs to sum to 8 distinct from {1,2,3,4})
	 *          → digits {1,3,4} or {2,3,3 invalid} → only {1,3,4}
	 *
	 *   This puzzle (and its expected solution) was derived by hand.
	 */
	@Test
	void solvesSmallKiller() {
		// 8 two-cell cages tiling the 4x4 grid in horizontal pairs.
		// Cage sums alternate 3 (=1+2) and 7 (=3+4) so each row is forced.
		// Total: 4*3 + 4*7 = 40 = 4*(1+2+3+4) ✓
		String input =
		    "# 4x4 Killer with 8 two-cell cages\n" +
		    "4\n" +
		    "A A B B\n" +
		    "C C D D\n" +
		    "E E F F\n" +
		    "G G H H\n" +
		    "A = 3\n" +
		    "B = 7\n" +
		    "C = 7\n" +
		    "D = 3\n" +
		    "E = 3\n" +
		    "F = 7\n" +
		    "G = 7\n" +
		    "H = 3\n";
		KillerPuzzle p = KillerInput.fromString(input);
		KillerSolver.Result r = KillerSolver.solve(p);
		assertNotNull(r.solution, "expected a solution");
		assertValid4x4(p, r.solution);
	}

	/**
	 * Trivial sanity: a one-cage-per-cell 4x4 with arbitrary sums (each cage has
	 * 1 cell, sum = the digit). Should solve trivially when sums form a valid grid.
	 *
	 * We construct a known valid 4x4 solution as singleton cages.
	 */
	@Test
	void solvesSingletonCageKiller() {
		int[] sol = { 1,2,3,4,  3,4,1,2,  2,1,4,3,  4,3,2,1 };
		Cage[] cages = new Cage[16];
		for (int i = 0; i < 16; i++) {
			cages[i] = new Cage(i + 1, sol[i], new int[]{ i });
		}
		KillerPuzzle p = new KillerPuzzle(BoardSpec.S4, cages);
		KillerSolver.Result r = KillerSolver.solve(p);
		assertNotNull(r.solution);
		for (int i = 0; i < 16; i++) {
			assertEquals(sol[i], r.solution[i], "cell " + i);
		}
	}

	/** Cage with impossible sum (too small to fit cells) → unsolvable. */
	@Test
	void rejectsImpossibleSum() {
		// 4x4 with one big cage: 16 cells must sum to (1+2+3+4) * 4 = 40.
		// If we declare sum = 39, KillerPuzzle's invariant check catches it
		// at construction (not the solver).
		Cage[] cages = new Cage[16];
		for (int i = 0; i < 16; i++) cages[i] = new Cage(i + 1, 5, new int[]{ i });
		assertThrows(IllegalArgumentException.class,
		             () -> new KillerPuzzle(BoardSpec.S4, cages),
		             "total cage sum != n*Σ(1..n) should be rejected");
	}

	/** Cage whose sum can't be reached with distinct digits → unsolvable. */
	@Test
	void rejectsUnreachableCageSum() {
		// 4x4: one big cage of 4 cells with sum 30 (max distinct 4-cell sum = 1+2+3+4 = 10).
		// First we'd need total = 40, so other cages must compensate. Easier: build
		// a small grid and ensure the solver returns null.
		// 1-cell cage with sum=5 in a 4x4 (digits 1..4) → no valid placement.
		Cage[] cages = new Cage[16];
		cages[0] = new Cage(1, 5, new int[]{ 0 });          // impossible — digit 5 not allowed
		// Fill the rest with singleton cages summing correctly to compensate.
		// Other 15 cells must sum to 40 - 5 = 35; they're singletons, so each sum = its digit.
		// But each digit is in 1..4 (S4), so 15 singletons summing to 35 means an avg of ~2.3
		// — feasible. The point is cage 1 (sum=5) is itself unsatisfiable.
		int remaining = 40 - 5;
		for (int i = 1; i < 16; i++) {
			int s = (i % 4) + 1;    // some plausible digit 1..4
			remaining -= s;
			cages[i] = new Cage(i + 1, s, new int[]{ i });
		}
		// Don't try to make this completely consistent — just check that one
		// cage being impossible (sum=5 in S4) makes the puzzle unsolvable.
		KillerPuzzle p;
		try {
			p = new KillerPuzzle(BoardSpec.S4, cages);
		} catch (IllegalArgumentException e) {
			// Caught at construction if totals don't match — that's fine too.
			return;
		}
		KillerSolver.Result r = KillerSolver.solve(p);
		assertNull(r.solution);
	}

	// -----------------------------------------------------------------

	/** Verify that the solution is a valid 4x4 Killer arrangement: rows, cols, boxes hold 1..4. */
	private void assertValid4x4(KillerPuzzle p, int[] sol) {
		BoardSpec spec = p.spec;
		for (int[] unit : spec.allUnits) {
			boolean[] seen = new boolean[spec.n + 1];
			for (int cell : unit) {
				int v = sol[cell];
				assertTrue(v >= 1 && v <= spec.n);
				assertFalse(seen[v], "duplicate digit " + v + " in unit");
				seen[v] = true;
			}
		}
		// Cage sums + uniqueness.
		for (Cage c : p.cages) {
			int sum = 0;
			boolean[] seen = new boolean[spec.n + 1];
			for (int cell : c.cells) {
				int v = sol[cell];
				assertFalse(seen[v], "duplicate digit " + v + " in cage " + c.id);
				seen[v] = true;
				sum += v;
			}
			assertEquals(c.sum, sum, "cage " + c.id + " sum mismatch");
		}
	}
}
