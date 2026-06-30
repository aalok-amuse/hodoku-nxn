/*
 * Validates the scoring system by correlating it with an independent
 * difficulty proxy: brute-force DPLL search effort (branches + backtracks).
 *
 * Rationale: an external label corpus is the strongest signal, but expensive
 * to assemble. The cheapest proxy that requires zero external labels is the
 * DPLL search effort — a puzzle that needs more branching is objectively
 * harder for a brute-force solver. If our scoring is meaningful, score and
 * branches should correlate strongly (Spearman ρ > 0.6 within a band).
 *
 * Usage:
 *   java -cp target/Hodoku.jar sudoku.BatchValidateScoring <file>...
 *
 * Input handling — for each argument:
 *   *.json                                            → Killer puzzle (Amuse JSON)
 *   text file with '=' lines and A-Z cage layout      → Killer puzzle (text)
 *   anything else                                     → vanilla puzzles, one per line
 *
 * Per-line vanilla format: digits 0-9 (and A-G for 16x16), '.' for empty;
 * length must be a perfect square (16, 25, 36, 49, 81, or 256).
 *
 * Output: per-bucket (size, variant) stats with Spearman ρ between score and
 * brute-force counters; a stronger correlation means the score tracks the
 * objective hardness signal better.
 */
package sudoku;

import sudoku.killer.KillerInput;
import sudoku.killer.KillerJson;
import sudoku.killer.KillerPuzzle;
import sudoku.killer.KillerScorer;
import solver.SudokuSolver;
import solver.SudokuSolverFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class BatchValidateScoring {

	/** One scored puzzle; what the correlation is computed over. */
	private static final class Sample {
		final int score;
		final int branches;
		final int backtracks;
		Sample(int score, int branches, int backtracks) {
			this.score = score;
			this.branches = branches;
			this.backtracks = backtracks;
		}
	}

	private static final String ALPHA = "123456789ABCDEFG";

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: java -cp Hodoku.jar sudoku.BatchValidateScoring <file>...");
			System.exit(2);
		}
		Map<String, List<Sample>> buckets = new TreeMap<>();
		int totalPuzzles = 0, errors = 0;
		for (String path : args) {
			try {
				Variant v = detectVariant(path);
				int loaded;
				if (v == Variant.KILLER_JSON) {
					loaded = ingestKillerJson(path, buckets);
				} else if (v == Variant.KILLER_TEXT) {
					loaded = ingestKillerText(path, buckets);
				} else {
					loaded = ingestVanilla(path, buckets);
				}
				totalPuzzles += loaded;
				System.out.printf("loaded %d puzzles from %s (%s)%n",
				    loaded, path, v.name().toLowerCase());
			} catch (IOException | RuntimeException e) {
				errors++;
				System.out.printf("ERROR %s: %s (%s)%n", path,
				    e.getClass().getSimpleName(), e.getMessage());
			}
		}
		System.out.printf("%n=== summary: %d puzzles, %d files errored ===%n%n",
		    totalPuzzles, errors);

		for (Map.Entry<String, List<Sample>> entry : buckets.entrySet()) {
			reportBucket(entry.getKey(), entry.getValue());
		}
	}

	private enum Variant { KILLER_JSON, KILLER_TEXT, VANILLA }

	private static Variant detectVariant(String path) throws IOException {
		String lower = path.toLowerCase();
		if (lower.endsWith(".json")) return Variant.KILLER_JSON;
		// Look for Killer text signature: an '=' line within the first 40 lines.
		List<String> head = Files.readAllLines(Paths.get(path));
		int peek = Math.min(40, head.size());
		for (int i = 0; i < peek; i++) {
			String line = head.get(i).trim();
			if (line.contains("=") && !line.startsWith("#")) return Variant.KILLER_TEXT;
		}
		return Variant.VANILLA;
	}

	// -------------------------------------------------------------------
	// Per-variant ingestion
	// -------------------------------------------------------------------

	private static int ingestKillerJson(String path, Map<String, List<Sample>> buckets) throws IOException {
		String json = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		KillerPuzzle puzzle = KillerJson.fromString(json);
		KillerScorer.Result r = KillerScorer.solve(puzzle);
		String key = bucketKey(puzzle.spec.n, "killer");
		buckets.computeIfAbsent(key, k -> new ArrayList<>())
		       .add(new Sample(r.logicalScore, r.bruteForceBranches, r.bruteForceBacktracks));
		return 1;
	}

	private static int ingestKillerText(String path, Map<String, List<Sample>> buckets) throws IOException {
		KillerPuzzle puzzle = KillerInput.fromFile(Paths.get(path));
		KillerScorer.Result r = KillerScorer.solve(puzzle);
		String key = bucketKey(puzzle.spec.n, "killer");
		buckets.computeIfAbsent(key, k -> new ArrayList<>())
		       .add(new Sample(r.logicalScore, r.bruteForceBranches, r.bruteForceBacktracks));
		return 1;
	}

	private static int ingestVanilla(String path, Map<String, List<Sample>> buckets) throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(path));
		int loaded = 0, skipped = 0;
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			Sample s = null;
			try { s = scoreVanillaLine(line); }
			catch (Throwable t) { /* per-puzzle crash — log + skip */ }
			if (s == null) { skipped++; continue; }
			int boardN = (int) Math.round(Math.sqrt(line.length()));
			String key = bucketKey(boardN, "vanilla");
			buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
			loaded++;
		}
		if (skipped > 0) {
			System.out.printf("  (%s: skipped %d puzzles the scorer couldn't handle)%n", path, skipped);
		}
		return loaded;
	}

	private static Sample scoreVanillaLine(String line) {
		int len = line.length();
		int n = (int) Math.round(Math.sqrt(len));
		if (n * n != len) return null;
		BoardSpec spec;
		try { spec = BoardSpec.of(n); }
		catch (IllegalArgumentException e) { return null; }
		Sudoku2 s = new Sudoku2(spec);
		for (int i = 0; i < len; i++) {
			char c = line.charAt(i);
			if (c == '.' || c == '0') continue;
			int idx = ALPHA.indexOf(c);
			if (idx < 0 || idx >= n) return null;
			s.setCell(i, idx + 1, true);
		}
		SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();
		DifficultyLevel max = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
		Sudoku2 work = s.clone();
		solver.solve(max, work, false, null);
		int score = work.getScore();

		Sudoku2 dpllInput = s.clone();
		DpllSolver.Result dpll = DpllSolver.solveCounted(dpllInput);
		return new Sample(score, dpll.branches, dpll.backtracks);
	}

	// -------------------------------------------------------------------
	// Stats
	// -------------------------------------------------------------------

	private static String bucketKey(int n, String variant) {
		return String.format("%s n=%d", variant, n);
	}

	private static void reportBucket(String key, List<Sample> samples) {
		int n = samples.size();
		if (n < 2) {
			System.out.printf("--- %s --- (n=%d, too few for correlation)%n", key, n);
			return;
		}
		int[] score = samples.stream().mapToInt(s -> s.score).toArray();
		int[] branches = samples.stream().mapToInt(s -> s.branches).toArray();
		int[] backtracks = samples.stream().mapToInt(s -> s.backtracks).toArray();

		double rhoBr = spearman(score, branches);
		double rhoBt = spearman(score, backtracks);

		System.out.printf("--- %s --- (n=%d)%n", key, n);
		System.out.printf("  score      : min=%d  median=%d  max=%d%n",
		    min(score), median(score), max(score));
		System.out.printf("  branches   : min=%d  median=%d  max=%d%n",
		    min(branches), median(branches), max(branches));
		System.out.printf("  backtracks : min=%d  median=%d  max=%d%n",
		    min(backtracks), median(backtracks), max(backtracks));
		System.out.printf("  Spearman rho(score, branches)   = %+.3f  (%s)%n",
		    rhoBr, interpret(rhoBr));
		System.out.printf("  Spearman rho(score, backtracks) = %+.3f  (%s)%n",
		    rhoBt, interpret(rhoBt));
		System.out.println();
	}

	private static int min(int[] a) { return Arrays.stream(a).min().orElse(0); }
	private static int max(int[] a) { return Arrays.stream(a).max().orElse(0); }
	private static int median(int[] a) {
		int[] sorted = a.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}

	/** Spearman rank correlation with average-rank tie handling. */
	static double spearman(int[] x, int[] y) {
		int n = x.length;
		if (n != y.length) throw new IllegalArgumentException("arrays differ in length");
		double[] rx = ranks(x);
		double[] ry = ranks(y);
		double mx = mean(rx), my = mean(ry);
		double num = 0, dx2 = 0, dy2 = 0;
		for (int i = 0; i < n; i++) {
			double a = rx[i] - mx, b = ry[i] - my;
			num += a * b;
			dx2 += a * a;
			dy2 += b * b;
		}
		double denom = Math.sqrt(dx2 * dy2);
		return denom == 0 ? 0 : num / denom;
	}

	/** Average-rank for ties: tied values share the mean of their position range. */
	private static double[] ranks(int[] a) {
		int n = a.length;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) order[i] = i;
		Arrays.sort(order, Comparator.comparingInt(i -> a[i]));
		double[] r = new double[n];
		int i = 0;
		while (i < n) {
			int j = i;
			while (j + 1 < n && a[order[j + 1]] == a[order[i]]) j++;
			double avg = (i + j + 2) / 2.0; // ranks are 1-based; average of i+1..j+1
			for (int k = i; k <= j; k++) r[order[k]] = avg;
			i = j + 1;
		}
		return r;
	}

	private static double mean(double[] a) {
		double s = 0;
		for (double v : a) s += v;
		return s / a.length;
	}

	private static String interpret(double rho) {
		double abs = Math.abs(rho);
		if (abs >= 0.7) return "strong";
		if (abs >= 0.4) return "moderate";
		if (abs >= 0.2) return "weak";
		return "essentially none";
	}
}
