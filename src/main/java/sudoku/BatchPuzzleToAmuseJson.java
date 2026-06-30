/*
 * Read TSV lines (name<TAB>puzzle) from stdin, write one Amuse-style JSON
 * file per line into the given output directory. Saves JVM-startup cost over
 * running PuzzleToAmuseJson once per puzzle.
 *
 * Usage:
 *   echo -e "01-foo\t1.2.3...\n02-bar\t.1.2..3" | \
 *     java -cp Hodoku.jar sudoku.BatchPuzzleToAmuseJson out-dir
 */
package sudoku;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class BatchPuzzleToAmuseJson {

	private static final String ALPHA = "123456789ABCDEFG";

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.err.println("usage: BatchPuzzleToAmuseJson <out-dir>");
			System.exit(2);
		}
		Path outDir = Paths.get(args[0]);
		Files.createDirectories(outDir);

		try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				int tab = line.indexOf('\t');
				if (tab < 0) throw new IllegalArgumentException("expected TAB in: " + line);
				String name = line.substring(0, tab).trim();
				String puz = line.substring(tab + 1).trim();
				int len = puz.length();
				int n = (int) Math.round(Math.sqrt(len));
				if (n * n != len) throw new IllegalArgumentException("length " + len + " not perfect square: " + name);
				BoardSpec spec = BoardSpec.of(n);

				Sudoku2 s = new Sudoku2(spec);
				StringBuilder puzzle = new StringBuilder(len);
				for (int i = 0; i < len; i++) {
					char c = puz.charAt(i);
					if (c == '.' || c == '0') {
						puzzle.append('0');
						continue;
					}
					int idx = ALPHA.indexOf(c);
					if (idx < 0 || idx >= n) throw new IllegalArgumentException("bad char '" + c + "' in " + name);
					puzzle.append(ALPHA.charAt(idx));
					s.setCell(i, idx + 1, true);
				}
				int[] sol = DpllSolver.solve(s);
				if (sol == null) {
					System.err.println("skipping " + name + ": unsolvable");
					continue;
				}
				StringBuilder solmap = new StringBuilder(len);
				for (int v : sol) solmap.append(ALPHA.charAt(v - 1));

				Path out = outDir.resolve(name + ".json");
				try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
					w.write("{\n");
					w.write("  \"name\": \"Sudoku\",\n");
					w.write("  \"game_data\": {\n");
					w.write("    \"rows\": " + spec.boxHeight + ",\n");
					w.write("    \"cols\": " + spec.boxWidth + ",\n");
					w.write("    \"puzzle\": \"" + puzzle + "\",\n");
					w.write("    \"solmap\": \"" + solmap + "\"\n");
					w.write("  }\n");
					w.write("}\n");
				}
				System.out.println("wrote " + out);
			}
		}
	}
}
