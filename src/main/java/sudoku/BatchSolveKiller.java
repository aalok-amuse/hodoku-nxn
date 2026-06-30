/*
 * Batch CLI for Killer Sudoku puzzles.
 *
 * Usage:
 *   java -cp target/Hodoku.jar sudoku.BatchSolveKiller <file1> [file2] ...
 *
 * Each file is a Killer puzzle in the text format documented in
 * {@link sudoku.killer.KillerInput}. Output (one line per puzzle, on stdout):
 *
 *   <file>  #<seq>  <Solved|Unsolvable>  <branches>br/<backtracks>bt  <ms>ms
 *
 * Branches and backtracks are crude difficulty proxies: more search effort =
 * harder. No technique-level scoring yet — that's a separate work item.
 */
package sudoku;

import sudoku.killer.KillerInput;
import sudoku.killer.KillerPuzzle;
import sudoku.killer.KillerSolver;

import java.io.IOException;
import java.nio.file.Paths;

public final class BatchSolveKiller {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: java -cp Hodoku.jar sudoku.BatchSolveKiller <file1> [file2] ...");
			System.exit(2);
		}
		int solved = 0, unsolvable = 0, errors = 0;
		int seq = 0;
		for (String path : args) {
			seq++;
			String name = Paths.get(path).getFileName().toString();
			try {
				KillerPuzzle puzzle = KillerInput.fromFile(Paths.get(path));
				KillerSolver.Result r = KillerSolver.solve(puzzle);
				if (r.solution != null) {
					solved++;
					System.out.printf("%s  #%d  Solved   %dbr/%dbt  %dms%n",
					    name, seq, r.branches, r.backtracks, r.elapsedMs);
				} else {
					unsolvable++;
					System.out.printf("%s  #%d  Unsolvable   %dbr/%dbt  %dms%n",
					    name, seq, r.branches, r.backtracks, r.elapsedMs);
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
	}
}
