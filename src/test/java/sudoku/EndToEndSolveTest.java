/*
 * End-to-end tests — push a puzzle of each supported size through the full
 * solver pipeline (SudokuSolver → step finder → technique solvers → DPLL fallback)
 * and verify the result satisfies Sudoku rules.
 */
package sudoku;

import org.junit.jupiter.api.Test;
import solver.SudokuSolver;
import solver.SudokuSolverFactory;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndSolveTest {

	private static final String ALPHA = "123456789:;<=>?@";

	@Test
	void solves4x4ThroughFullPipeline() {
		solveAndAssertValid(BoardSpec.S4, makePuzzleFromSolution(BoardSpec.S4, "1234341221434321", 6, 11));
	}

	@Test
	void solves6x6WithRectangularBoxes() {
		solveAndAssertValid(BoardSpec.S6, makePuzzleFromSolution(BoardSpec.S6, "123456456123234561561234345612612345", 14, 17));
	}

	@Test
	void solves9x9SyntheticPuzzle() {
		// Generic 9×9: shifted-box construction, ~40 cells erased. We don't
		// constrain the difficulty band — under-constrained tiled puzzles can
		// land anywhere from Easy to Extreme depending on which cells were
		// erased. The contract is just "solves cleanly".
		String puzzle = makePuzzleFromSolution(BoardSpec.S9, buildShifted9x9(), 40, 23);
		Sudoku2 s = solveAndAssertValid(BoardSpec.S9, puzzle);
		assertNotNull(s.getLevel(), "level should be assigned");
	}

	/** Shifted-box construction for a valid 9×9 grid (used by the synthetic test). */
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
	void solves16x16WithDpllFallback() {
		String puzzle = buildPartial16x16(20);
		Sudoku2 s = solveAndAssertValid(BoardSpec.S16, puzzle);
		assertEquals(256, filledCount(s));
	}

	@Test
	void allFourSizesRoundTripIndependently() {
		solveAndAssertValid(BoardSpec.S4, makePuzzleFromSolution(BoardSpec.S4, "1234341221434321", 6, 11));
		solveAndAssertValid(BoardSpec.S6, makePuzzleFromSolution(BoardSpec.S6, "123456456123234561561234345612612345", 14, 17));
		solveAndAssertValid(BoardSpec.S9, makePuzzleFromSolution(BoardSpec.S9, buildShifted9x9(), 40, 23));
		solveAndAssertValid(BoardSpec.S16, buildPartial16x16(15));
	}

	/** Erase {@code erase} cells from the given verified solution string. */
	private String makePuzzleFromSolution(BoardSpec spec, String solution, int erase, int seed) {
		assertEquals(spec.length, solution.length());
		char[] grid = solution.toCharArray();
		java.util.Random rng = new java.util.Random(seed);
		int erased = 0;
		while (erased < erase) {
			int idx = rng.nextInt(grid.length);
			if (grid[idx] != '.') { grid[idx] = '.'; erased++; }
		}
		return new String(grid);
	}

	// ---------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------

	private Sudoku2 solveAndAssertValid(BoardSpec spec, String puzzle) {
		assertEquals(spec.length, puzzle.length(),
		             "puzzle length must equal spec.length");
		Sudoku2 s = new Sudoku2(spec);
		int placed = 0;
		for (int i = 0; i < spec.length; i++) {
			char c = puzzle.charAt(i);
			if (c != '.' && c != '0') {
				int digit = ALPHA.indexOf(c) + 1;
				assertTrue(s.setCell(i, digit, true),
				           "setCell rejected given at " + i);
				placed++;
			}
		}
		SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();
		DifficultyLevel max = Options.getInstance()
		    .getDifficultyLevel(DifficultyType.EXTREME.ordinal());
		boolean ok = solver.solve(max, s, false, null);
		assertTrue(ok, "solver returned false on " + spec.n + "×" + spec.n + " puzzle");
		assertEquals(spec.length, filledCount(s),
		             "not all cells filled (placed " + placed + ")");
		assertValidSudoku(spec, s);
		return s;
	}

	private void assertValidSudoku(BoardSpec spec, Sudoku2 s) {
		for (int[] unit : spec.allUnits) {
			boolean[] seen = new boolean[spec.n + 1];
			for (int cell : unit) {
				int v = s.getValue(cell);
				assertTrue(v >= 1 && v <= spec.n);
				assertFalse(seen[v], "duplicate digit " + v + " in a unit");
				seen[v] = true;
			}
		}
	}

	private int filledCount(Sudoku2 s) {
		int n = 0;
		for (int i = 0; i < s.getLength(); i++) {
			if (s.getValue(i) != 0) n++;
		}
		return n;
	}

	private String buildPartial16x16(int emptyCells) {
		int n = 16, boxW = 4, boxH = 4;
		char[] grid = new char[n * n];
		for (int r = 0; r < n; r++) {
			for (int c = 0; c < n; c++) {
				int v = ((r % boxH) * boxW + (r / boxH) + c) % n + 1;
				grid[r * n + c] = ALPHA.charAt(v - 1);
			}
		}
		java.util.Random rng = new java.util.Random(42);
		int erased = 0;
		while (erased < emptyCells) {
			int idx = rng.nextInt(grid.length);
			if (grid[idx] != '.') { grid[idx] = '.'; erased++; }
		}
		return new String(grid);
	}
}
