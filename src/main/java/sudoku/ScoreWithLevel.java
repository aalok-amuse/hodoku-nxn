/*
 * Like ScoreEachLine but also prints the difficulty level (band) for each
 * puzzle. Input: stdin lines of `name<TAB>puzzle`.
 *
 * Output: `<name>\t<score>\t<level>\t<puzzle>` per line.
 */
package sudoku;

import solver.SudokuSolver;
import solver.SudokuSolverFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ScoreWithLevel {

	private static final String ALPHA = "123456789ABCDEFG";

	public static void main(String[] args) throws Exception {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				int tab = line.indexOf('\t');
				if (tab < 0) continue;
				String name = line.substring(0, tab).trim();
				String puz = line.substring(tab + 1).trim();
				int len = puz.length();
				int n = (int) Math.round(Math.sqrt(len));
				if (n * n != len) { System.out.println(name + "\t?\t?\tbad-length"); continue; }
				BoardSpec spec;
				try { spec = BoardSpec.of(n); } catch (Exception e) {
					System.out.println(name + "\t?\t?\tbad-size"); continue;
				}
				try {
					Sudoku2 s = new Sudoku2(spec);
					for (int i = 0; i < len; i++) {
						char c = puz.charAt(i);
						if (c == '.' || c == '0') continue;
						int idx = ALPHA.indexOf(c);
						if (idx < 0 || idx >= n) { s = null; break; }
						s.setCell(i, idx + 1, true);
					}
					if (s == null) { System.out.println(name + "\t?\t?\tbad-char"); continue; }
					SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();
					DifficultyLevel max = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
					solver.solve(max, s, false, null);
					int score = s.getScore();
					DifficultyLevel lvl = s.getLevel();
					String lvlName = lvl == null ? "Unknown" : lvl.getName();
					System.out.println(name + "\t" + score + "\t" + lvlName);
				} catch (Throwable t) {
					System.out.println(name + "\t?\t?\t" + t.getClass().getSimpleName());
				}
			}
		}
	}
}
