/*
 * Convert a one-line vanilla Sudoku puzzle to Amuse-style JSON.
 *
 * Input  (stdin or argv[0]): single line, digits 1..n / A..G, '0' or '.' for
 *                            empty cells; length = n*n where n is the board
 *                            side (4, 5, 6, 7, 9, 16).
 * Output (stdout):           Amuse-style JSON with `puzzle` + `solmap` fields.
 *
 * Usage:
 *   echo "12..3...." | java -cp Hodoku.jar sudoku.PuzzleToAmuseJson > out.json
 *   java -cp Hodoku.jar sudoku.PuzzleToAmuseJson "12..3...." > out.json
 */
package sudoku;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class PuzzleToAmuseJson {

	private static final String ALPHA = "123456789ABCDEFG";

	public static void main(String[] args) throws Exception {
		String input;
		if (args.length >= 1) {
			input = args[0].trim();
		} else {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
				input = r.readLine().trim();
			}
		}
		int len = input.length();
		int n = (int) Math.round(Math.sqrt(len));
		if (n * n != len) throw new IllegalArgumentException("length " + len + " is not a perfect square");
		BoardSpec spec = BoardSpec.of(n);

		Sudoku2 s = new Sudoku2(spec);
		StringBuilder puzzle = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			char c = input.charAt(i);
			if (c == '.' || c == '0') {
				puzzle.append('0');
				continue;
			}
			int idx = ALPHA.indexOf(c);
			if (idx < 0 || idx >= n) throw new IllegalArgumentException("bad char '" + c + "' at " + i);
			puzzle.append(ALPHA.charAt(idx));
			s.setCell(i, idx + 1, true);
		}
		int[] sol = DpllSolver.solve(s);
		if (sol == null) throw new IllegalStateException("puzzle is unsolvable");
		StringBuilder solmap = new StringBuilder(len);
		for (int v : sol) solmap.append(ALPHA.charAt(v - 1));

		System.out.println("{");
		System.out.println("  \"name\": \"Sudoku\",");
		System.out.println("  \"game_data\": {");
		System.out.println("    \"rows\": " + spec.boxHeight + ",");
		System.out.println("    \"cols\": " + spec.boxWidth + ",");
		System.out.println("    \"puzzle\": \"" + puzzle + "\",");
		System.out.println("    \"solmap\": \"" + solmap + "\"");
		System.out.println("  }");
		System.out.println("}");
	}
}
