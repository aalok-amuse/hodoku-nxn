/*
 * Parser for a simple text Killer Sudoku input format.
 *
 * Format:
 *   - First non-blank, non-comment line: the side length n (one of 4,5,6,7,9,16).
 *   - Next n lines: the cage grid. Each line has n tokens (whitespace-separated),
 *     each token is a single character cage id (any printable non-whitespace char).
 *   - Remaining lines: cage sums, one per line, as `<id>=<sum>` (whitespace permitted).
 *     Optionally a `givens:` block can appear: a line `givens:` followed by n
 *     lines of n single-character digits or '.' for empty.
 *   - Lines beginning with `#` and blank lines are ignored.
 *
 * Example (a 4x4 Killer):
 *
 *   4
 *   A A B B
 *   C A B D
 *   C C D D
 *   E E F F
 *   A = 6
 *   B = 7
 *   C = 11
 *   D = 8
 *   E = 4
 *   F = 4
 *
 * Larger boards use the same letter alphabet (or digits / mixed) — anything
 * non-whitespace works as an id, and ids may repeat across cells of the same
 * cage but must be unique per cage.
 */
package sudoku.killer;

import sudoku.BoardSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KillerInput {

	private KillerInput() {}

	public static KillerPuzzle fromFile(Path file) throws IOException {
		return fromString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
	}

	public static KillerPuzzle fromString(String text) {
		List<String> lines = new ArrayList<>();
		for (String raw : text.split("\\r?\\n")) {
			String t = raw.trim();
			if (t.isEmpty() || t.startsWith("#")) continue;
			lines.add(t);
		}
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("empty input");
		}

		int n;
		try {
			n = Integer.parseInt(lines.get(0));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("first line must be the board size (got: '" + lines.get(0) + "')");
		}
		BoardSpec spec = BoardSpec.of(n);
		int length = spec.length;

		if (lines.size() < 1 + n) {
			throw new IllegalArgumentException("not enough lines for an " + n + "x" + n + " grid");
		}

		// Read the cage grid (next n lines).
		Map<String, List<Integer>> cellsById = new LinkedHashMap<>();
		for (int r = 0; r < n; r++) {
			String[] tokens = lines.get(1 + r).split("\\s+");
			if (tokens.length != n) {
				throw new IllegalArgumentException("row " + r + " has " + tokens.length
				    + " tokens, expected " + n);
			}
			for (int c = 0; c < n; c++) {
				cellsById.computeIfAbsent(tokens[c], k -> new ArrayList<>()).add(r * n + c);
			}
		}

		// Parse the remainder for sums and optional givens.
		Map<String, Integer> sumById = new HashMap<>();
		int[] givens = null;
		int idx = 1 + n;
		while (idx < lines.size()) {
			String line = lines.get(idx);
			if (line.equalsIgnoreCase("givens:")) {
				givens = new int[length];
				if (idx + 1 + n > lines.size()) {
					throw new IllegalArgumentException("givens: block must contain " + n + " rows");
				}
				for (int r = 0; r < n; r++) {
					String row = lines.get(idx + 1 + r).replaceAll("\\s+", "");
					if (row.length() != n) {
						throw new IllegalArgumentException("givens row " + r + " must have "
						    + n + " chars (got " + row.length() + ")");
					}
					for (int c = 0; c < n; c++) {
						char ch = row.charAt(c);
						if (ch == '.' || ch == '0') {
							givens[r * n + c] = 0;
						} else if (ch >= '1' && ch <= ('0' + n)) {
							givens[r * n + c] = ch - '0';
						} else {
							throw new IllegalArgumentException("bad given char '" + ch
							    + "' at (r=" + r + ",c=" + c + ")");
						}
					}
				}
				idx += 1 + n;
				continue;
			}
			// Otherwise expect "ID = N"
			String[] parts = line.split("=", 2);
			if (parts.length != 2) {
				throw new IllegalArgumentException("expected '<id>=<sum>' on line " + (idx + 1) + ": " + line);
			}
			String id = parts[0].trim();
			int sum;
			try { sum = Integer.parseInt(parts[1].trim()); }
			catch (NumberFormatException e) {
				throw new IllegalArgumentException("non-integer sum for cage " + id + ": " + parts[1]);
			}
			if (sumById.put(id, sum) != null) {
				throw new IllegalArgumentException("duplicate cage sum for id " + id);
			}
			idx++;
		}

		// Build the Cage array.
		if (sumById.size() != cellsById.size()) {
			throw new IllegalArgumentException("cage-grid has " + cellsById.size() + " cages, "
			    + "but " + sumById.size() + " sums declared");
		}
		Cage[] cages = new Cage[cellsById.size()];
		int ci = 0;
		for (Map.Entry<String, List<Integer>> e : cellsById.entrySet()) {
			Integer s = sumById.get(e.getKey());
			if (s == null) {
				throw new IllegalArgumentException("no sum declared for cage id " + e.getKey());
			}
			int[] cells = new int[e.getValue().size()];
			for (int k = 0; k < cells.length; k++) cells[k] = e.getValue().get(k);
			cages[ci++] = new Cage(ci, s, cells);
		}
		return new KillerPuzzle(spec, cages, givens);
	}
}
