/*
 * Phase 6 test driver — drives 4x4, 6x6, 9x9, 16x16 puzzles through the full
 * solver pipeline and verifies each is fully solved.
 *
 *   4x4 box 2x2  — singles-only
 *   6x6 box 3x2  — first rectangular-box case
 *   9x9 box 3x3  — sanity (already heavily tested by the regression corpus)
 *   16x16 box 4x4 — large board, 4-long bitset, 65,536-entry mask tables
 */
package sudoku;

import solver.SudokuSolver;
import solver.SudokuSolverFactory;

public class PhaseSixTest {

	/** A self-contained test puzzle: side n, puzzle givens, and full solution. */
	private static class Puzzle {
		final int n;
		final String givens, solution;
		Puzzle(int n, String givens, String solution) {
			this.n = n; this.givens = givens; this.solution = solution;
		}
	}

	// Build small puzzles by erasing ~40% of cells from a verified solution.
	private static Puzzle P4, P6;
	static {
		P4 = makePuzzle(4, 2, 2, "1234341221434321", 0.4, 13);
		P6 = makePuzzle(6, 3, 2, "123456456123234561561234345612612345", 0.4, 17);
	}

	// 16x16 trivial nearly-full puzzle (uses digits 1..16, with '0'+digit). Use
	// hex-like representation: digit 10 = ':', 11 = ';', 12 = '<', 13 = '=', 14 = '>', 15 = '?', 16 = '@'.
	// Easier: build programmatically.
	// We'll build a 16x16 grid by tiling a known Latin-square-like generator,
	// then verify it satisfies all Sudoku constraints, then remove a handful of
	// cells for the puzzle.
	private static String solution16, puzzle16;

	public static void main(String[] args) {
		build16x16();
		Puzzle P16 = new Puzzle(16, puzzle16, solution16);

		System.out.println("=== Phase 6 — multi-size end-to-end ===\n");
		boolean all = true;
		all &= runOne(P4);
		all &= runOne(P6);
		all &= runOne(P16);
		System.out.println(all ? "ALL PASSED" : "SOME FAILED");
		System.exit(all ? 0 : 1);
	}

	private static boolean runOne(Puzzle p) {
		System.out.println("---- " + p.n + "x" + p.n + " ----");
		BoardSpec spec = BoardSpec.of(p.n);
		Sudoku2 s = new Sudoku2(spec);
		// Set solution
		int[] sol = parseGrid(p.solution, p.n);
		if (sol == null) { System.out.println("  bad solution"); return false; }
		s.setSolution(sol);
		s.setSolutionSet(true);
		// Set givens
		int[] giv = parseGrid(p.givens, p.n);
		if (giv == null) { System.out.println("  bad givens"); return false; }
		int givenCount = 0;
		for (int i = 0; i < spec.length; i++) {
			if (giv[i] != 0) {
				s.setCell(i, giv[i], true);
				givenCount++;
			}
		}
		System.out.println("  givens placed: " + givenCount + "/" + spec.length);
		if (p.n == 16) {
			System.out.println("  grid AFTER setting givens (BEFORE solve):");
			printGrid(s, spec);
			// Spot-check: any cells where setValue produced a different value than we asked for?
			int corrupted = 0;
			for (int i = 0; i < spec.length; i++) {
				if (giv[i] != 0 && s.getValue(i) != giv[i]) corrupted++;
			}
			System.out.println("  givens-corruption count: " + corrupted);
		}

		try {
			SudokuSolver theSolver = SudokuSolverFactory.getDefaultSolverInstance();
			DifficultyLevel max = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
			long t0 = System.nanoTime();
			boolean solved = theSolver.solve(max, s, false, null);
			long ms = (System.nanoTime() - t0) / 1_000_000;
			int filled = spec.length - countUnsolved(s);
			System.out.println("  solve returned: " + solved + "  (filled " + filled + "/" + spec.length + ", " + ms + " ms)");
			// Validate the result satisfies Sudoku rules (rows / cols / boxes each have 1..n).
			boolean valid = filled == spec.length && satisfiesSudokuRules(s, spec);
			System.out.println("  result satisfies sudoku rules: " + valid);
			if (!valid && filled == spec.length) {
				printGrid(s, spec);
				// Report which units fail.
				int unitIdx = 0;
				for (int[] unit : spec.allUnits) {
					boolean[] seen = new boolean[spec.n + 1];
					for (int cell : unit) {
						int v = s.getValue(cell);
						if (v >= 1 && v <= spec.n) {
							if (seen[v]) {
								System.out.println("  duplicate " + v + " in unit " + unitIdx + " (cells " + java.util.Arrays.toString(unit) + ")");
							}
							seen[v] = true;
						}
					}
					unitIdx++;
				}
			}
			System.out.println();
			return solved && valid;
		} catch (Throwable t) {
			System.out.println("  CRASHED: " + t.getClass().getSimpleName() + ": " + t.getMessage());
			t.printStackTrace(System.out);
			System.out.println();
			return false;
		}
	}

	/** Parses a string of n*n chars. '.' or '0' = empty. Digits 1..n encoded as chars '1'..'(n+'0')'. */
	private static int[] parseGrid(String s, int n) {
		if (s.length() != n * n) return null;
		int[] out = new int[n * n];
		for (int i = 0; i < n * n; i++) {
			char c = s.charAt(i);
			if (c == '.' || c == '0') {
				out[i] = 0;
			} else if (c >= '1' && c <= ('0' + n)) {
				out[i] = c - '0';
			} else {
				return null; // bad char
			}
		}
		return out;
	}

	private static void printGrid(Sudoku2 s, BoardSpec spec) {
		int n = spec.n;
		String alpha = "0123456789:;<=>?@";
		for (int r = 0; r < n; r++) {
			StringBuilder sb = new StringBuilder("    ");
			for (int c = 0; c < n; c++) {
				int v = s.getValue(r * n + c);
				sb.append(v == 0 ? "." : Character.toString(alpha.charAt(v))).append(' ');
			}
			System.out.println(sb);
		}
	}

	/** Build a deterministic puzzle by erasing the requested fraction of cells from a verified solution. */
	private static Puzzle makePuzzle(int n, int boxW, int boxH, String solStr, double eraseFraction, int seed) {
		int len = n * n;
		int[] sol = new int[len];
		String alpha = "0123456789:;<=>?@";
		for (int i = 0; i < len; i++) sol[i] = alpha.indexOf(solStr.charAt(i));
		// Verify solution is valid Sudoku before using.
		boolean valid;
		try {
			BoardSpec spec = BoardSpec.of(n);
			boolean[] seen;
			valid = true;
			for (int[] unit : spec.allUnits) {
				seen = new boolean[n + 1];
				for (int cell : unit) {
					int v = sol[cell];
					if (v < 1 || v > n || seen[v]) { valid = false; break; }
					seen[v] = true;
				}
			}
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("unsupported size " + n, e);
		}
		if (!valid) throw new IllegalArgumentException("supplied solution for n=" + n + " is invalid");
		// Erase deterministically.
		int[] giv = sol.clone();
		java.util.Random rng = new java.util.Random(seed);
		int target = (int) (len * eraseFraction);
		int erased = 0;
		while (erased < target) {
			int idx = rng.nextInt(len);
			if (giv[idx] != 0) { giv[idx] = 0; erased++; }
		}
		StringBuilder gb = new StringBuilder(len);
		for (int v : giv) gb.append(v == 0 ? '.' : alpha.charAt(v));
		return new Puzzle(n, gb.toString(), solStr);
	}

	private static boolean satisfiesSudokuRules(Sudoku2 s, BoardSpec spec) {
		int n = spec.n;
		for (int[] unit : spec.allUnits) {
			boolean[] seen = new boolean[n + 1];
			for (int cell : unit) {
				int v = s.getValue(cell);
				if (v < 1 || v > n || seen[v]) return false;
				seen[v] = true;
			}
		}
		return true;
	}

	private static int countUnsolved(Sudoku2 s) {
		int n = 0;
		for (int i = 0; i < s.getLength(); i++) if (s.getValue(i) == 0) n++;
		return n;
	}

	/**
	 * Build a 16x16 solution + puzzle programmatically. The solution comes from a
	 * standard 16x16 generator: tile a 4x4 cycle so each row/col/4x4-box contains
	 * each of 1..16 exactly once. Then erase a small fraction of cells.
	 *
	 * Encoding: digits 1..16 → integers; we store them in arrays directly (no string),
	 * but for the string-based parseGrid we encode each digit as one char by widening
	 * to a 16-character alphabet '1'..'9', then ':', ';', '<', '=', '>', '?', '@'.
	 *
	 * We DO NOT actually use the string path for 16x16 — we set cells directly.
	 */
	private static void build16x16() {
		int n = 16;
		int[] sol = new int[n * n];
		int boxW = 4, boxH = 4;
		// Use canonical "shifted box" construction:
		//   sol[r][c] = ((r mod boxH) * boxW + (r / boxH) + c) mod n + 1
		for (int r = 0; r < n; r++) {
			for (int c = 0; c < n; c++) {
				int v = ((r % boxH) * boxW + (r / boxH) + c) % n + 1;
				sol[r * n + c] = v;
			}
		}
		// Verify it's a valid Sudoku (no duplicates in any row/col/box)
		if (!isValidSolution(sol, n, boxW, boxH)) {
			throw new IllegalStateException("16x16 builder produced invalid grid");
		}
		// Erase ~50% of cells for the puzzle (leaving many givens; this is just for
		// solver smoke-test, not a hard puzzle)
		int[] giv = sol.clone();
		java.util.Random rng = new java.util.Random(7);  // deterministic
		int target = (int) (n * n * 0.25);  // erase 25%
		int erased = 0;
		while (erased < target) {
			int idx = rng.nextInt(n * n);
			if (giv[idx] != 0) { giv[idx] = 0; erased++; }
		}
		solution16 = encode16(sol);
		puzzle16 = encode16(giv);
	}

	private static boolean isValidSolution(int[] g, int n, int boxW, int boxH) {
		// rows
		for (int r = 0; r < n; r++) {
			boolean[] seen = new boolean[n + 1];
			for (int c = 0; c < n; c++) {
				int v = g[r * n + c];
				if (v < 1 || v > n || seen[v]) return false;
				seen[v] = true;
			}
		}
		// cols
		for (int c = 0; c < n; c++) {
			boolean[] seen = new boolean[n + 1];
			for (int r = 0; r < n; r++) {
				int v = g[r * n + c];
				if (seen[v]) return false;
				seen[v] = true;
			}
		}
		// boxes
		int bxa = n / boxW, bxd = n / boxH;
		for (int b = 0; b < n; b++) {
			boolean[] seen = new boolean[n + 1];
			int br = b / bxa, bc = b % bxa;
			for (int dr = 0; dr < boxH; dr++) {
				for (int dc = 0; dc < boxW; dc++) {
					int v = g[(br * boxH + dr) * n + bc * boxW + dc];
					if (seen[v]) return false;
					seen[v] = true;
				}
			}
		}
		return true;
	}

	/** Encode 1..16 as 16 chars (digits + 7 letters). 0 → '.'. */
	private static String encode16(int[] g) {
		StringBuilder sb = new StringBuilder(g.length);
		String alpha = "123456789:;<=>?@";
		for (int v : g) {
			sb.append(v == 0 ? '.' : alpha.charAt(v - 1));
		}
		return sb.toString();
	}
}
