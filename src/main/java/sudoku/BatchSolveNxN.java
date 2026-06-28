/*
 * Multi-size, multi-format batch solver. Mirrors Hodoku's /bs CLI for any
 * supported board size (4, 5, 6, 7, 9, 16) and either of two input formats:
 *
 *   - **Flat text** (default): one puzzle per line, size auto-detected from
 *     line length (16=4x4, 25=5x5, 36=6x6, 49=7x7, 81=9x9, 256=16x16).
 *     Digit alphabet: 1..9, then ':'(10), ';'(11), '<'(12), '='(13), '>'(14),
 *     '?'(15), '@'(16). Empty cells are '.' or '0'.
 *
 *   - **Amuse Labs xword.json** (one puzzle per file): files whose name ends
 *     in ".xword.json" or ".json" are parsed via {@link XwordJson} (9x9 only).
 *
 * Usage:
 *   java -cp Hodoku.jar sudoku.BatchSolveNxN <file1> [file2] ...
 *
 * Each argument is processed independently. For flat-text files the output
 * goes to <file>.out.txt next to the input; for xword.json files results
 * accumulate into a single batch report on stdout.
 *
 * Output (one line per puzzle):
 *   <input>  #<N>  <Level>  (<score>)  <filled>/<length>  <ms>ms
 */
package sudoku;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import solver.SudokuSolver;
import solver.SudokuSolverFactory;

public class BatchSolveNxN {

	/** Digit alphabet covering 1..16. Index `i` encodes digit `i+1`. */
	public static final String ALPHA = "123456789:;<=>?@";

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.err.println("usage: java -cp Hodoku.jar sudoku.BatchSolveNxN <file1> [file2] ...");
			System.err.println("  flat-text files: one puzzle per line (size auto-detected)");
			System.err.println("  .xword.json files: Amuse Labs format (9x9 only)");
			System.exit(2);
		}

		int total = 0, solved = 0;
		Map<String, Integer> categoryCounts = new HashMap<>();
		long t0 = System.nanoTime();

		for (String arg : args) {
			if (isXwordJson(arg)) {
				// xword.json: one puzzle per file. Extract the 81-char line and solve.
				total++;
				Result r = solveXwordJson(arg, total);
				System.out.println(r.format());
				if (r.solved) solved++;
				categoryCounts.merge(r.category, 1, Integer::sum);
			} else {
				// Flat text: one puzzle per line. Output goes to <input>.out.txt next to input.
				String outputFile = arg + ".out.txt";
				try (BufferedReader br = new BufferedReader(new FileReader(arg));
				     java.io.PrintWriter out = new java.io.PrintWriter(outputFile)) {
					String line;
					while ((line = br.readLine()) != null) {
						line = line.trim();
						if (line.isEmpty()) continue;
						total++;
						Result r = solveOne(line, total);
						out.println(r.format());
						if (r.solved) solved++;
						categoryCounts.merge(r.category, 1, Integer::sum);
					}
				}
				System.out.println("Wrote " + outputFile);
			}
		}

		long ms = (System.nanoTime() - t0) / 1_000_000;
		System.out.println(total + " puzzles in " + ms + "ms");
		System.out.printf("  %d solved, %d unsolved%n", solved, total - solved);
		for (Map.Entry<String, Integer> e : categoryCounts.entrySet()) {
			System.out.printf("   %s: %d%n", e.getKey(), e.getValue());
		}
	}

	/** True if the path looks like an Amuse Labs xword.json file. */
	private static boolean isXwordJson(String path) {
		String lower = path.toLowerCase();
		return lower.endsWith(".xword.json") || lower.endsWith(".json");
	}

	/**
	 * Load one .xword.json file, convert to 81-char Hodoku format, and solve.
	 * The puzzle's display id is the filename minus the .xword.json suffix.
	 */
	private static Result solveXwordJson(String path, int seq) {
		long t0 = System.nanoTime();
		String displayName = Paths.get(path).getFileName().toString()
				.replaceAll("\\.xword\\.json$", "")
				.replaceAll("\\.json$", "");
		String line;
		try {
			line = XwordJson.fileTo81(Paths.get(path));
		} catch (Throwable t) {
			return new Result(displayName, seq, 9, 0, 81, false, 0, "BadJson",
					(System.nanoTime() - t0) / 1_000_000,
					t.getClass().getSimpleName() + ": " + t.getMessage());
		}
		Result r = solveOne(line, seq);
		// Re-label with the display name (filename) rather than the raw 81-char puzzle.
		return new Result(displayName, r.seq, r.n, r.filled, r.total, r.solved,
				r.score, r.category, r.ms + ((System.nanoTime() - t0) / 1_000_000 - r.ms),
				r.error);
	}

	private static class Result {
		final String line;
		final int seq;
		final int n;
		final int filled;
		final int total;
		final boolean solved;
		final int score;
		final String category;
		final long ms;
		final String error;

		Result(String line, int seq, int n, int filled, int total, boolean solved,
		       int score, String category, long ms, String error) {
			this.line = line; this.seq = seq; this.n = n;
			this.filled = filled; this.total = total; this.solved = solved;
			this.score = score; this.category = category; this.ms = ms; this.error = error;
		}

		String format() {
			if (error != null) {
				return String.format("%s  #%d  ERROR (%s)", line, seq, error);
			}
			return String.format("%s  #%d  %s  (%d)  %d/%d  %dms",
					line, seq, category, score, filled, total, ms);
		}
	}

	/** Solve a single puzzle line. Catches errors so a bad line doesn't kill the batch. */
	private static Result solveOne(String line, int seq) {
		long t0 = System.nanoTime();
		int n = sizeOf(line.length());
		if (n < 0) {
			return new Result(line, seq, 0, 0, 0, false, 0, "BadSize",
					(System.nanoTime() - t0) / 1_000_000, "length " + line.length() + " not in {16,25,36,49,81,256}");
		}
		try {
			BoardSpec spec = BoardSpec.of(n);
			Sudoku2 s = new Sudoku2(spec);
			// Set cells from the input string.
			for (int i = 0; i < spec.length; i++) {
				char c = line.charAt(i);
				int digit = decodeDigit(c, n);
				if (digit < 0) {
					return new Result(line, seq, n, 0, spec.length, false, 0, "BadChar",
							(System.nanoTime() - t0) / 1_000_000,
							"char '" + c + "' at position " + i);
				}
				if (digit > 0) {
					s.setCell(i, digit, true);
				}
			}
			// Solve.
			SudokuSolver theSolver = SudokuSolverFactory.getDefaultSolverInstance();
			DifficultyLevel max = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
			boolean ok = theSolver.solve(max, s, false, null);
			int filled = countFilled(s);
			boolean solved = ok && filled == spec.length;
			int score = s.getScore();
			DifficultyLevel level = s.getLevel();
			String cat = level == null ? "Unknown" : level.getName();
			long ms = (System.nanoTime() - t0) / 1_000_000;
			return new Result(line, seq, n, filled, spec.length, solved, score, cat, ms, null);
		} catch (Throwable t) {
			return new Result(line, seq, n, 0, n * n, false, 0, "Crashed",
					(System.nanoTime() - t0) / 1_000_000,
					t.getClass().getSimpleName() + ": " + t.getMessage());
		}
	}

	/** Map a single character to digit (1..n). Returns 0 for empty, -1 for invalid. */
	private static int decodeDigit(char c, int n) {
		if (c == '.' || c == '0') return 0;
		int idx = ALPHA.indexOf(c);
		if (idx < 0 || idx >= n) return -1;
		return idx + 1;
	}

	/** Return n given total cell count (length), or -1 if unsupported. */
	private static int sizeOf(int length) {
		switch (length) {
			case 16:  return 4;
			case 25:  return 5;
			case 36:  return 6;
			case 49:  return 7;
			case 81:  return 9;
			case 256: return 16;
			default:  return -1;
		}
	}

	private static int countFilled(Sudoku2 s) {
		int f = 0;
		for (int i = 0; i < s.getLength(); i++) {
			if (s.getValue(i) != 0) f++;
		}
		return f;
	}
}
