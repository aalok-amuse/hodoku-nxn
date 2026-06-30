/*
 * Solve a puzzle and dump every rule fired with its weight, plus the running
 * total. Confirms Hodoku's score is exactly sum(per-step weight).
 *
 * Usage:
 *   java -cp Hodoku.jar sudoku.RuleBreakdown <puzzle-string>
 *   java -cp Hodoku.jar sudoku.RuleBreakdown --xword <path/to/foo.xword.json>
 */
package sudoku;

import solver.SudokuSolver;
import solver.SudokuSolverFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuleBreakdown {

	private static final String ALPHA = "123456789ABCDEFG";

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.err.println("usage: RuleBreakdown <puzzle-string>");
			System.err.println("       RuleBreakdown --xword <file.xword.json>");
			System.exit(2);
		}
		String puzzle;
		if (args[0].equals("--xword") && args.length == 2) {
			puzzle = puzzleFromXword(args[1]);
		} else {
			puzzle = args[0];
		}
		int len = puzzle.length();
		int n = (int) Math.round(Math.sqrt(len));
		if (n * n != len) throw new IllegalArgumentException("length " + len + " not a perfect square");
		BoardSpec spec = BoardSpec.of(n);

		Sudoku2 s = new Sudoku2(spec);
		for (int i = 0; i < len; i++) {
			char c = puzzle.charAt(i);
			if (c == '.' || c == '0') continue;
			int idx = ALPHA.indexOf(c);
			if (c == '0') idx = 9;            // PMM's 16x16 alphabet maps '0' to digit 10
			if (idx < 0 || idx >= n) throw new IllegalArgumentException("bad char '" + c + "'");
			s.setCell(i, idx + 1, true);
		}

		SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();
		DifficultyLevel max = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
		solver.solve(max, s, false, null);

		Map<String, int[]> counts = new LinkedHashMap<>(); // rule -> [count, weight, contribution]
		int total = 0;
		List<SolutionStep> steps = solver.getSteps();
		for (SolutionStep step : steps) {
			SolutionType t = step.getType();
			StepConfig cfg = t.getStepConfig();
			int weight = cfg == null ? 0 : cfg.getBaseScore();
			int[] row = counts.computeIfAbsent(t.getStepName(), k -> new int[]{0, weight, 0});
			row[0]++;
			row[2] += weight;
			total += weight;
		}

		System.out.printf("puzzle (n=%d): %s%n", n, puzzle);
		System.out.printf("%-30s %6s %6s %7s%n", "rule", "count", "weight", "score");
		System.out.printf("%-30s %6s %6s %7s%n", "----", "-----", "------", "-----");
		for (Map.Entry<String, int[]> e : counts.entrySet()) {
			System.out.printf("%-30s %6d %6d %7d%n",
			    e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]);
		}
		System.out.printf("%-30s %6s %6s %7d%n", "TOTAL", "", "", total);
		System.out.printf("solver.score = %d, level = %s, size_band = %s%n",
		    s.getScore(),
		    s.getLevel() == null ? "?" : s.getLevel().getName(),
		    SizeBands.levelFor(n, s.getScore()).displayName());
	}

	private static String puzzleFromXword(String path) throws Exception {
		String json = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		int n = (int) java.util.regex.Pattern.compile("\"w\"\\s*:\\s*(\\d+)").matcher(json).results()
		        .findFirst().map(m -> Integer.parseInt(m.group(1))).orElseThrow();
		String[] sol = new String[n * n];
		boolean[][] pre = new boolean[n][n];
		int boxStart = json.indexOf("[", json.indexOf("\"box\""));
		int depth = 0;
		StringBuilder sb = new StringBuilder();
		int outIdx = 0;
		for (int cur = boxStart; cur < json.length() && outIdx < n * n; cur++) {
			char c = json.charAt(cur);
			if (c == '[') depth++;
			else if (c == ']') { depth--; if (depth == 0) break; }
			else if (c == '"') {
				sb.setLength(0);
				while (json.charAt(++cur) != '"') sb.append(json.charAt(cur));
				sol[outIdx++] = sb.toString();
			}
		}
		int preStart = json.indexOf("[", json.indexOf("\"preRevealIdxs\""));
		depth = 0;
		int row = 0, col = 0;
		for (int cur = preStart; cur < json.length(); cur++) {
			char c = json.charAt(cur);
			if (c == '[') { depth++; if (depth == 2) col = 0; }
			else if (c == ']') { depth--; if (depth == 1) row++; if (depth == 0) break; }
			else if (c == 't') { pre[row][col++] = true; cur += 3; }
			else if (c == 'f') { pre[row][col++] = false; cur += 4; }
		}
		StringBuilder out = new StringBuilder();
		for (int y = 0; y < n; y++)
			for (int x = 0; x < n; x++)
				out.append(pre[y][x] ? sol[y * n + x] : ".");
		return out.toString();
	}
}
