/*
 * Synthesizes a small validation corpus of vanilla Sudoku puzzles at any
 * supported size. Each puzzle is one line of digits 1..n (and A..G for 16x16)
 * with '.' for empty cells.
 *
 * Strategy:
 *   1. Start from an empty grid.
 *   2. Place a few random digits, then call DpllSolver to fill the rest —
 *      that gives a guaranteed-solvable completed grid.
 *   3. Erase cells to reach the requested clue count.
 *
 * No uniqueness check (single solution) is performed; the validator only needs
 * solvable inputs to compute scoring vs search-effort correlation, and a
 * non-unique puzzle still has *a* solution the scorer will find.
 *
 * Usage:
 *   java -cp Hodoku.jar sudoku.GenerateVanillaCorpus <n> <count> <cluesPerPuzzle> <outFile>
 *
 * Example:
 *   java ... sudoku.GenerateVanillaCorpus 6 30 18 corpus/vanilla6.txt
 */
package sudoku;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class GenerateVanillaCorpus {

	private static final String ALPHA = "123456789ABCDEFG";

	public static void main(String[] args) throws IOException {
		if (args.length != 4) {
			System.err.println("usage: GenerateVanillaCorpus <n> <count> <clues> <outFile>");
			System.exit(2);
		}
		int n = Integer.parseInt(args[0]);
		int count = Integer.parseInt(args[1]);
		int clues = Integer.parseInt(args[2]);
		Path out = Paths.get(args[3]);

		BoardSpec spec = BoardSpec.of(n);
		if (clues < 1 || clues > spec.length) {
			throw new IllegalArgumentException("clues out of range");
		}
		Files.createDirectories(out.toAbsolutePath().getParent());
		Random rng = new Random(0xC0FFEEL ^ (long) n);
		int produced = 0, attempts = 0;
		try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			while (produced < count) {
				attempts++;
				int[] solved = generateSolvedGrid(spec, rng);
				if (solved == null) continue;
				erase(solved, spec.length - clues, rng);
				w.write(encode(solved, n));
				w.newLine();
				produced++;
				if (attempts > count * 50) {
					System.err.println("warning: high attempt count");
					break;
				}
			}
		}
		System.out.printf("wrote %d puzzles to %s (%d attempts)%n", produced, out, attempts);
	}

	/**
	 * Solve an empty grid once (instant — DPLL with MRV produces the canonical
	 * Sudoku/Latin square), then permute digits and the digit-symbolic rows for
	 * diversity. Cheaper than letting DPLL search from random partial seeds,
	 * which can pathological-blow up on degenerate-box boards like 7×7.
	 */
	private static final int[] BASE_CACHE_KEYS = new int[]{4, 5, 6, 7, 9, 16};
	private static final java.util.Map<Integer, int[]> BASE_CACHE = new java.util.HashMap<>();

	private static int[] generateSolvedGrid(BoardSpec spec, Random rng) {
		int[] base = BASE_CACHE.get(spec.n);
		if (base == null) {
			Sudoku2 s = new Sudoku2(spec);
			base = DpllSolver.solve(s);
			if (base == null) return null;
			BASE_CACHE.put(spec.n, base);
		}
		// Random digit permutation: pi[d] = new digit for old digit d, 1-indexed.
		int[] pi = new int[spec.n + 1];
		List<Integer> digits = new ArrayList<>();
		for (int d = 1; d <= spec.n; d++) digits.add(d);
		Collections.shuffle(digits, rng);
		for (int d = 1; d <= spec.n; d++) pi[d] = digits.get(d - 1);

		int[] grid = new int[spec.length];
		for (int i = 0; i < spec.length; i++) grid[i] = pi[base[i]];

		// Permute rows within each horizontal band, and cols within each vertical
		// stack — preserves all Sudoku constraints but creates genuinely distinct
		// puzzles (digit permutation alone gives identical structural difficulty).
		grid = permuteRowsWithinBands(spec, grid, rng);
		grid = permuteColsWithinStacks(spec, grid, rng);
		return grid;
	}

	private static int[] permuteRowsWithinBands(BoardSpec spec, int[] grid, Random rng) {
		int n = spec.n;
		int boxH = spec.boxHeight > 0 ? spec.boxHeight : n;
		int[] rowMap = new int[n];
		for (int i = 0; i < n; i++) rowMap[i] = i;
		for (int b = 0; b < n; b += boxH) {
			List<Integer> rows = new ArrayList<>();
			for (int r = b; r < Math.min(b + boxH, n); r++) rows.add(rowMap[r]);
			Collections.shuffle(rows, rng);
			for (int r = b; r < Math.min(b + boxH, n); r++) rowMap[r] = rows.get(r - b);
		}
		int[] out = new int[spec.length];
		for (int r = 0; r < n; r++)
			for (int c = 0; c < n; c++)
				out[r * n + c] = grid[rowMap[r] * n + c];
		return out;
	}

	private static int[] permuteColsWithinStacks(BoardSpec spec, int[] grid, Random rng) {
		int n = spec.n;
		int boxW = spec.boxWidth > 0 ? spec.boxWidth : n;
		int[] colMap = new int[n];
		for (int i = 0; i < n; i++) colMap[i] = i;
		for (int b = 0; b < n; b += boxW) {
			List<Integer> cols = new ArrayList<>();
			for (int c = b; c < Math.min(b + boxW, n); c++) cols.add(colMap[c]);
			Collections.shuffle(cols, rng);
			for (int c = b; c < Math.min(b + boxW, n); c++) colMap[c] = cols.get(c - b);
		}
		int[] out = new int[spec.length];
		for (int r = 0; r < n; r++)
			for (int c = 0; c < n; c++)
				out[r * n + c] = grid[r * n + colMap[c]];
		return out;
	}

	private static void erase(int[] grid, int eraseCount, Random rng) {
		List<Integer> cells = shuffledIndices(grid.length, rng);
		int erased = 0;
		for (int idx : cells) {
			if (erased >= eraseCount) break;
			if (grid[idx] != 0) {
				grid[idx] = 0;
				erased++;
			}
		}
	}

	private static List<Integer> shuffledIndices(int len, Random rng) {
		List<Integer> out = new ArrayList<>(len);
		for (int i = 0; i < len; i++) out.add(i);
		Collections.shuffle(out, rng);
		return out;
	}

	private static String encode(int[] grid, int n) {
		StringBuilder sb = new StringBuilder(grid.length);
		for (int v : grid) {
			sb.append(v == 0 ? '.' : ALPHA.charAt(v - 1));
		}
		return sb.toString();
	}
}
