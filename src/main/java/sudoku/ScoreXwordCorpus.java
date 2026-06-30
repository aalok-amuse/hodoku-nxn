/*
 * Walk a directory tree of xword.json puzzles, run the Hodoku solver-scorer on
 * each, and emit CSV: size,difficulty,puzzle_id,givens,score,level,ms
 *
 * Expected layout (matches the sudoku-content-library zip):
 *   <root>/<size>/<difficulty>/<id>.xword.json
 *
 * Each xword.json has top-level fields w, h, box (full solution as 2D string
 * array), preRevealIdxs (2D boolean — true means the cell is a given).
 *
 * Usage:
 *   java -cp Hodoku.jar sudoku.ScoreXwordCorpus <rootDir> <outCsv>
 */
package sudoku;

import solver.SudokuSolver;
import solver.SudokuSolverFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public final class ScoreXwordCorpus {

	/** PMM's default 16x16 alphabet is "1-9,0,A-F" so a digit 10 lands on '0' not 'A'. */
	private static final String PMM_ALPHA_FULL = "1234567890ABCDEF";

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("usage: ScoreXwordCorpus <rootDir> <outCsv>");
			System.exit(2);
		}
		Path root = Paths.get(args[0]);
		Path out = Paths.get(args[1]);
		Path parent = out.toAbsolutePath().getParent();
		if (parent != null && !Files.isDirectory(parent)) {
			Files.createDirectories(parent);
		}

		List<Path> jsons = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(p -> p.getFileName().toString().endsWith(".xword.json"))
				.sorted()
				.forEach(jsons::add);
		}
		System.out.printf("found %d xword.json files under %s%n", jsons.size(), root);

		ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "score-worker");
			t.setDaemon(true);
			return t;
		});
		long perPuzzleBudgetMs = 5000;  // bail out of stuck puzzles after 5s
		try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			w.write("size,difficulty,puzzle_id,givens,score,level,ms,note");
			w.newLine();

			int done = 0, errors = 0, timeouts = 0;
			long t0 = System.nanoTime();
			for (Path p : jsons) {
				String size = "", diff = "", id = p.getFileName().toString().replace(".xword.json", "");
				Path par = p.getParent();
				if (par != null) {
					diff = par.getFileName().toString();
					Path gp = par.getParent();
					if (gp != null) size = gp.getFileName().toString();
				}
				Future<Row> fut = exec.submit(() -> scoreOne(p));
				try {
					Row r = fut.get(perPuzzleBudgetMs, TimeUnit.MILLISECONDS);
					w.write(String.format("%s,%s,%s,%d,%d,%s,%d,",
					    size, diff, id, r.givens, r.score, r.level, r.ms));
					w.newLine();
				} catch (TimeoutException te) {
					timeouts++;
					fut.cancel(true);
					// Replace the worker — the cancelled task may have left it stuck.
					exec.shutdownNow();
					exec = Executors.newSingleThreadExecutor(r -> {
						Thread t = new Thread(r, "score-worker");
						t.setDaemon(true);
						return t;
					});
					w.write(String.format("%s,%s,%s,,,,,timeout-after-%dms",
					    size, diff, id, perPuzzleBudgetMs));
					w.newLine();
				} catch (Throwable t) {
					errors++;
					Throwable cause = t.getCause() != null ? t.getCause() : t;
					w.write(String.format("%s,%s,%s,,,,,%s:%s",
					    size, diff, id, cause.getClass().getSimpleName(),
					    String.valueOf(cause.getMessage()).replace(',', ' ').replace('\n', ' ')));
					w.newLine();
				}
				done++;
				if (done % 100 == 0) {
					w.flush();
					double secs = (System.nanoTime() - t0) / 1e9;
					System.out.printf("  scored %d/%d (%d errors, %d timeouts) in %.1fs%n",
					    done, jsons.size(), errors, timeouts, secs);
				}
			}
			double secs = (System.nanoTime() - t0) / 1e9;
			System.out.printf("done: %d scored (%d errors, %d timeouts) in %.1fs -> %s%n",
			    done, errors, timeouts, secs, out);
		} finally {
			exec.shutdownNow();
		}
	}

	private static final class Row {
		int givens, score; String level; long ms;
	}

	private static Row scoreOne(Path xword) throws IOException {
		String json = new String(Files.readAllBytes(xword), StandardCharsets.UTF_8);
		int n = readInt(json, "\"w\"");
		BoardSpec spec = BoardSpec.of(n);

		// Parse box (2D string array of solution chars) — flatten to length n*n.
		String[] sol = parseRowMajorBox(json, n);
		// Parse preRevealIdxs (2D boolean) — true means given.
		boolean[][] pre = parseRowMajorBool(json, "preRevealIdxs", n);

		Sudoku2 s = new Sudoku2(spec);
		int givens = 0;
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				if (!pre[y][x]) continue;
				String chStr = sol[y * n + x];
				int d = mapDigit(chStr, n);
				if (d <= 0 || d > n) throw new IllegalStateException("bad solution char " + chStr);
				s.setCell(y * n + x, d, true);
				givens++;
			}
		}

		long t0 = System.nanoTime();
		SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();
		DifficultyLevel max = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
		solver.solve(max, s, false, null);
		long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

		Row r = new Row();
		r.givens = givens;
		r.score = s.getScore();
		DifficultyLevel lvl = s.getLevel();
		r.level = lvl == null ? "Unknown" : lvl.getName();
		r.ms = elapsedMs;
		return r;
	}

	private static int mapDigit(String s, int n) {
		// PMM uses "1-9,0,A-F" for sizes that need >9 chars (we only see 8 here
		// at most, so '0' won't appear). Treat '1'..'9' as their digit, 'A'..'G'
		// as 10..16 for hodoku-nxn compatibility, and '0' as 10.
		if (s == null || s.isEmpty()) return -1;
		char c = s.charAt(0);
		if (c >= '1' && c <= '9') return c - '0';
		if (c == '0') return 10;
		if (c >= 'A' && c <= 'G') return 10 + (c - 'A') + (c >= 'A' ? 1 : 0);
		return -1;
	}

	private static int readInt(String json, String key) {
		int i = json.indexOf(key);
		if (i < 0) throw new IllegalArgumentException("missing " + key);
		int colon = json.indexOf(':', i);
		int end = json.indexOf(',', colon);
		if (end < 0) end = json.indexOf('}', colon);
		return Integer.parseInt(json.substring(colon + 1, end).trim());
	}

	private static String[] parseRowMajorBox(String json, int n) {
		int i = json.indexOf("\"box\"");
		if (i < 0) throw new IllegalArgumentException("missing box");
		int open = json.indexOf('[', i);
		// pull all "X" strings from the box block — there are n*n of them.
		int depth = 0;
		StringBuilder sb = new StringBuilder();
		String[] out = new String[n * n];
		int outIdx = 0;
		int cur = open;
		while (cur < json.length()) {
			char c = json.charAt(cur++);
			if (c == '[') depth++;
			else if (c == ']') {
				depth--;
				if (depth == 0) break;
			} else if (c == '"') {
				sb.setLength(0);
				while (cur < json.length() && json.charAt(cur) != '"') {
					sb.append(json.charAt(cur++));
				}
				cur++; // skip closing quote
				if (outIdx < out.length) out[outIdx++] = sb.toString();
			}
		}
		if (outIdx != n * n) throw new IllegalArgumentException(
		    "box had " + outIdx + " entries, expected " + (n * n));
		return out;
	}

	private static boolean[][] parseRowMajorBool(String json, String key, int n) {
		int i = json.indexOf("\"" + key + "\"");
		if (i < 0) throw new IllegalArgumentException("missing " + key);
		int open = json.indexOf('[', i);
		boolean[][] out = new boolean[n][n];
		int depth = 0;
		int row = 0, col = 0;
		int cur = open;
		while (cur < json.length()) {
			char c = json.charAt(cur++);
			if (c == '[') {
				depth++;
				if (depth == 2) col = 0;
			} else if (c == ']') {
				depth--;
				if (depth == 1) { row++; }
				if (depth == 0) break;
			} else if (Character.isLetter(c)) {
				// 'true' or 'false'
				boolean v;
				if (c == 't') { v = true; cur += 3; }
				else { v = false; cur += 4; }
				if (row < n && col < n) out[row][col++] = v;
			}
		}
		return out;
	}
}
