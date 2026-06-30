/*
 * Tiny helper: read vanilla puzzles (one per line) from the given file, print
 * each as `<score> <puzzle>` so you can grep/sort by score. For 4×4/16×16
 * calibration-set picking.
 *
 * Usage:
 *   java -cp Hodoku.jar sudoku.ScoreEachLine <file>
 */
package sudoku;

import solver.SudokuSolver;
import solver.SudokuSolverFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public final class ScoreEachLine {

	private static final String ALPHA = "123456789ABCDEFG";

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.err.println("usage: ScoreEachLine <file>");
			System.exit(2);
		}
		List<String> lines = Files.readAllLines(Paths.get(args[0]));
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			int len = line.length();
			int n = (int) Math.round(Math.sqrt(len));
			if (n * n != len) { System.out.println("? bad-length " + line); continue; }
			BoardSpec spec;
			try { spec = BoardSpec.of(n); } catch (Exception e) {
				System.out.println("? bad-size " + line); continue;
			}
			try {
				Sudoku2 s = new Sudoku2(spec);
				for (int i = 0; i < len; i++) {
					char c = line.charAt(i);
					if (c == '.' || c == '0') continue;
					int idx = ALPHA.indexOf(c);
					if (idx < 0 || idx >= n) { s = null; break; }
					s.setCell(i, idx + 1, true);
				}
				if (s == null) { System.out.println("? bad-char " + line); continue; }
				SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();
				DifficultyLevel max = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
				solver.solve(max, s, false, null);
				System.out.println(s.getScore() + "\t" + line);
			} catch (Throwable t) {
				System.out.println("? error(" + t.getClass().getSimpleName() + ") " + line);
			}
		}
	}
}
