/*
 * Batch CLI for Killer Sudoku puzzles, with Hodoku-style scoring.
 *
 * Usage:
 *   java -cp target/Hodoku.jar sudoku.BatchSolveKiller <file1> [file2] ...
 *
 * Each file is a Killer puzzle in either the text format documented in
 * {@link sudoku.killer.KillerInput} or the Amuse JSON format documented in
 * {@link sudoku.killer.KillerJson} (auto-detected by file extension).
 *
 * Output (one line per puzzle, on stdout):
 *
 *   <file>  #<seq>  <Level>  (<score>)  <steps>steps  <ms>ms  [matches-solmap]
 *
 * The score is the sum of per-technique weights for every step the solver
 * took (NAKED_SINGLE = 4, HIDDEN_SINGLE = 14, CAGE_SUBSET = 20,
 * LOCKED_CANDIDATES = 40, CAGE_45_INNIE = 60, BRUTE_FORCE = 5000). The level
 * is the highest band any single step required (Easy / Medium / Hard / Unfair
 * / Extreme); puzzles that fall back to brute force are always Extreme.
 *
 * If the JSON input includes a `solmap`, the solver's result is checked
 * against it and a `matches-solmap` suffix is added on success.
 */
package sudoku;

import sudoku.killer.KillerInput;
import sudoku.killer.KillerJson;
import sudoku.killer.KillerPuzzle;
import sudoku.killer.KillerScorer;
import sudoku.killer.KillerTechnique;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class BatchSolveKiller {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: java -cp Hodoku.jar sudoku.BatchSolveKiller <file1> [file2] ...");
			System.exit(2);
		}
		int solved = 0, unsolvable = 0, errors = 0;
		int seq = 0;
		Map<KillerTechnique.Level, Integer> levelCounts = new HashMap<>();
		for (String path : args) {
			seq++;
			String name = Paths.get(path).getFileName().toString();
			try {
				KillerPuzzle puzzle;
				String solmap = null;
				if (path.toLowerCase().endsWith(".json")) {
					String json = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
					puzzle = KillerJson.fromString(json);
					solmap = KillerJson.readSolmap(json);
				} else {
					puzzle = KillerInput.fromFile(Paths.get(path));
				}
				KillerScorer.Result r = KillerScorer.solve(puzzle);
				if (r.solution != null) {
					solved++;
					String match = "";
					if (solmap != null) {
						match = "  " + (matchesSolmap(r.solution, solmap) ? "matches-solmap" : "DIFFERS-from-solmap");
					}
					String levelStr;
					if (r.bruteForced) {
						// "Logical reasoning got to MEDIUM; brute force was then
						// needed → final EXTREME band."
						levelStr = String.format("%s/BF", r.logicalLevel.name());
					} else {
						levelStr = r.logicalLevel.name();
					}
					System.out.printf("%s  #%d  %-9s logical=%d  total=%d  %dsteps  %dms%s%n",
					    name, seq, levelStr, r.logicalScore, r.score,
					    r.steps.size(), r.elapsedMs, match);
					levelCounts.merge(r.level, 1, Integer::sum);
				} else {
					unsolvable++;
					System.out.printf("%s  #%d  Unsolvable  logical=%d  %dms%n",
					    name, seq, r.logicalScore, r.elapsedMs);
				}
			} catch (IOException e) {
				errors++;
				System.out.printf("%s  #%d  IO-ERROR  (%s)%n", name, seq, e.getMessage());
			} catch (Throwable t) {
				errors++;
				System.out.printf("%s  #%d  PARSE-ERROR  (%s)%n",
				    name, seq, t.getMessage());
			}
		}
		System.out.printf("%d puzzles: %d solved, %d unsolvable, %d errors%n",
		    seq, solved, unsolvable, errors);
		for (Map.Entry<KillerTechnique.Level, Integer> e : levelCounts.entrySet()) {
			System.out.printf("   %s: %d%n", e.getKey().name(), e.getValue());
		}
	}

	/** Verify a solver result against an expected `solmap` string from JSON input. */
	private static boolean matchesSolmap(int[] solution, String solmap) {
		if (solmap.length() != solution.length) return false;
		for (int i = 0; i < solution.length; i++) {
			char expected = solmap.charAt(i);
			if (expected < '1' || expected > '9') return false;
			if (solution[i] != expected - '0') return false;
		}
		return true;
	}
}
