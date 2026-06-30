/*
 * Read a Killer JSON, solve it via KillerSolver, write back the same JSON with
 * the `solmap` field updated to the solver's solution. Used for calibration
 * Killer puzzles whose cage layout admits multiple solutions — we label the
 * file with the deterministic one the solver finds.
 *
 * Usage:
 *   java -cp Hodoku.jar sudoku.KillerJsonSetSolmap <file.json>
 */
package sudoku;

import sudoku.killer.KillerJson;
import sudoku.killer.KillerPuzzle;
import sudoku.killer.KillerSolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class KillerJsonSetSolmap {

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.err.println("usage: KillerJsonSetSolmap <file>");
			System.exit(2);
		}
		String path = args[0];
		String json = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		KillerPuzzle p = KillerJson.fromString(json);
		KillerSolver.Result r = KillerSolver.solve(p);
		if (r.solution == null) {
			System.err.println(path + ": unsolvable, no solmap written");
			System.exit(1);
		}
		StringBuilder sb = new StringBuilder(r.solution.length);
		for (int v : r.solution) sb.append((char) ('0' + v));
		String newSol = sb.toString();
		String updated;
		if (json.contains("\"solmap\":")) {
			updated = json.replaceAll("\"solmap\":\\s*\"[^\"]*\"", "\"solmap\": \"" + newSol + "\"");
		} else {
			// Insert after first occurrence of "cols": <n>,
			updated = json.replaceFirst("(\"cols\":\\s*\\d+,)",
			    "$1\n    \"solmap\": \"" + newSol + "\",");
		}
		Files.write(Paths.get(path), updated.getBytes(StandardCharsets.UTF_8));
		System.out.println(path + ": solmap = " + newSol);
	}
}
