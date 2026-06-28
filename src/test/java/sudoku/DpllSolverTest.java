/*
 * DpllSolver tests — solve known puzzles of each supported size.
 */
package sudoku;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DpllSolverTest {

	/** Encode 1..16 as 16 chars: digits + ':;<=>?@'. */
	private static final String ALPHA = "123456789:;<=>?@";

	@Test
	void solves4x4FromGivensOnly() {
		// Erase 4 cells from a verified solution.
		String puzzle = makePuzzle(BoardSpec.S4, "1234341221434321", 6, 11);
		String[] result = solveAndVerify(BoardSpec.S4, puzzle);
		assertSatisfiesSudoku(BoardSpec.S4, result[0]);
	}

	@Test
	void solves6x6FromGivensOnly() {
		// Solution: 123456 456123 234561 561234 345612 612345 (3-wide × 2-tall boxes)
		String puzzle = makePuzzle(BoardSpec.S6, "123456456123234561561234345612612345", 14, 17);
		String[] result = solveAndVerify(BoardSpec.S6, puzzle);
		assertSatisfiesSudoku(BoardSpec.S6, result[0]);
	}

	@Test
	void solves9x9SyntheticPuzzle() {
		// Generic 9×9: tile a known 3-band, 3-stack shifted grid, then erase cells.
		String solution = buildShifted9x9();
		String puzzle = makePuzzle(BoardSpec.S9, solution, 40, 23);
		String[] result = solveAndVerify(BoardSpec.S9, puzzle);
		assertSatisfiesSudoku(BoardSpec.S9, result[0]);
	}

	/** Build a valid 9×9 grid via the shifted-box construction. */
	private String buildShifted9x9() {
		int n = 9, boxW = 3, boxH = 3;
		StringBuilder sb = new StringBuilder();
		for (int r = 0; r < n; r++) {
			for (int c = 0; c < n; c++) {
				int v = ((r % boxH) * boxW + (r / boxH) + c) % n + 1;
				sb.append(ALPHA.charAt(v - 1));
			}
		}
		return sb.toString();
	}

	@Test
	void solves16x16FromMostlyFilledGrid() {
		// Build a known valid 16x16, erase 25 cells.
		String full = buildFull16x16();
		java.util.Random rng = new java.util.Random(7);
		char[] puzzle = full.toCharArray();
		int erased = 0;
		while (erased < 25) {
			int p = rng.nextInt(256);
			if (puzzle[p] != '.') { puzzle[p] = '.'; erased++; }
		}
		String[] result = solveAndVerify(BoardSpec.S16, new String(puzzle));
		assertSatisfiesSudoku(BoardSpec.S16, result[0]);
	}

	@Test
	void returnsNullForUnsolvablePuzzle() {
		// Two 1s in the same row — no valid completion.
		BoardSpec spec = BoardSpec.S4;
		Sudoku2 s = new Sudoku2(spec);
		s.setCell(0, 1, true);
		s.setCell(1, 1, true);  // conflicts with cell 0 in row 0
		assertNull(DpllSolver.solve(s),
		           "DpllSolver should return null for unsolvable puzzles");
	}

	@Test
	void returnsImmediatelyOnFullyFilledGrid() {
		// All 16 cells filled with a valid 4×4 solution → DPLL has no work.
		String filled = "1234341221434321";
		BoardSpec spec = BoardSpec.S4;
		Sudoku2 s = new Sudoku2(spec);
		for (int i = 0; i < 16; i++) {
			s.setCell(i, ALPHA.indexOf(filled.charAt(i)) + 1, true);
		}
		int[] result = DpllSolver.solve(s);
		assertNotNull(result);
		for (int i = 0; i < 16; i++) {
			assertEquals(ALPHA.indexOf(filled.charAt(i)) + 1, result[i]);
		}
	}

	// ---------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------

	/** Set givens, run DPLL, return [solutionAsString]. Asserts DPLL succeeded. */
	private String[] solveAndVerify(BoardSpec spec, String puzzle) {
		Sudoku2 s = new Sudoku2(spec);
		assertEquals(spec.length, puzzle.length(),
		             "puzzle string length must match spec.length");
		for (int i = 0; i < spec.length; i++) {
			char c = puzzle.charAt(i);
			if (c != '.' && c != '0') {
				int digit = ALPHA.indexOf(c) + 1;
				assertTrue(s.setCell(i, digit, true),
				           "given at cell " + i + " was rejected (invalid puzzle?)");
			}
		}
		int[] solved = DpllSolver.solve(s);
		assertNotNull(solved, "DPLL must find a solution");
		assertEquals(spec.length, solved.length);
		StringBuilder sb = new StringBuilder();
		for (int v : solved) sb.append(v == 0 ? '.' : ALPHA.charAt(v - 1));
		return new String[]{ sb.toString() };
	}

	/** Verify the solution string satisfies Sudoku rules for the spec. */
	private void assertSatisfiesSudoku(BoardSpec spec, String solution) {
		assertEquals(spec.length, solution.length());
		for (int[] unit : spec.allUnits) {
			boolean[] seen = new boolean[spec.n + 1];
			for (int cell : unit) {
				int v = ALPHA.indexOf(solution.charAt(cell)) + 1;
				assertTrue(v >= 1 && v <= spec.n, "bad digit at cell " + cell);
				assertFalse(seen[v], "duplicate digit " + v + " in unit");
				seen[v] = true;
			}
		}
	}

	/** Build a deterministic puzzle by erasing N cells from a verified solution. */
	private String makePuzzle(BoardSpec spec, String solution, int erase, int seed) {
		assertEquals(spec.length, solution.length());
		// Verify the solution is actually valid before erasing.
		boolean[] seen;
		for (int[] unit : spec.allUnits) {
			seen = new boolean[spec.n + 1];
			for (int cell : unit) {
				int v = ALPHA.indexOf(solution.charAt(cell)) + 1;
				assertTrue(v >= 1 && v <= spec.n, "bad digit in given solution");
				assertFalse(seen[v], "given solution violates Sudoku rules");
				seen[v] = true;
			}
		}
		char[] grid = solution.toCharArray();
		java.util.Random rng = new java.util.Random(seed);
		int erased = 0;
		while (erased < erase) {
			int idx = rng.nextInt(grid.length);
			if (grid[idx] != '.') { grid[idx] = '.'; erased++; }
		}
		return new String(grid);
	}

	/** Build a valid 16×16 grid using shifted-box tiling. */
	private String buildFull16x16() {
		int n = 16, boxW = 4, boxH = 4;
		StringBuilder sb = new StringBuilder();
		for (int r = 0; r < n; r++) {
			for (int c = 0; c < n; c++) {
				int v = ((r % boxH) * boxW + (r / boxH) + c) % n + 1;
				sb.append(ALPHA.charAt(v - 1));
			}
		}
		return sb.toString();
	}
}
