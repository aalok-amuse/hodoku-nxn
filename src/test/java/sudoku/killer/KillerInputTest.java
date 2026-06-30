/*
 * Parser tests for the Killer Sudoku text format.
 */
package sudoku.killer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillerInputTest {

	@Test
	void parsesValid4x4() {
		String input =
		    "# A simple 4x4 Killer\n" +
		    "4\n" +
		    "A A B B\n" +
		    "C A B D\n" +
		    "C C D D\n" +
		    "E E F F\n" +
		    "A = 8\n" +
		    "B = 5\n" +
		    "C = 8\n" +
		    "D = 9\n" +
		    "E = 3\n" +
		    "F = 7\n";
		KillerPuzzle p = KillerInput.fromString(input);
		assertEquals(4, p.spec.n);
		assertEquals(6, p.cages.length);
		// Cage A has cells (0,0),(0,1),(1,1) → row-major indices 0, 1, 5
		Cage a = findCage(p, "A", input);
		assertNotNull(a);
		assertEquals(8, a.sum);
		assertEquals(3, a.size());
	}

	@Test
	void rejectsCellInTwoCages() {
		// Doesn't really fit the format (each grid cell has exactly one id) but
		// verify the KillerPuzzle invariant rejects manually-built bad input.
		Cage c1 = new Cage(1, 5, new int[]{ 0, 1 });
		Cage c2 = new Cage(2, 5, new int[]{ 1, 2 });
		assertThrows(IllegalArgumentException.class,
		             () -> new KillerPuzzle(sudoku.BoardSpec.S4, new Cage[]{ c1, c2 }));
	}

	@Test
	void rejectsMissingCageSum() {
		String input =
		    "4\n" +
		    "A A B B\n" +
		    "A A B B\n" +
		    "C C D D\n" +
		    "C C D D\n" +
		    "A = 10\n" +
		    "B = 10\n" +
		    "C = 10\n";
		// D's sum is missing.
		assertThrows(IllegalArgumentException.class, () -> KillerInput.fromString(input));
	}

	@Test
	void rejectsCageGridSizeMismatch() {
		String input =
		    "4\n" +
		    "A A B B\n" +
		    "A A B B\n" +
		    "C C D D\n";  // only 3 rows instead of 4
		assertThrows(IllegalArgumentException.class, () -> KillerInput.fromString(input));
	}

	@Test
	void supportsGivensBlock() {
		String input =
		    "4\n" +
		    "A A B B\n" +
		    "A A B B\n" +
		    "C C D D\n" +
		    "C C D D\n" +
		    "A = 10\n" +
		    "B = 10\n" +
		    "C = 10\n" +
		    "D = 10\n" +
		    "givens:\n" +
		    "1...\n" +
		    "....\n" +
		    "....\n" +
		    "...4\n";
		KillerPuzzle p = KillerInput.fromString(input);
		assertEquals(1, p.givens[0]);
		assertEquals(4, p.givens[15]);
		assertEquals(0, p.givens[8]);
	}

	private Cage findCage(KillerPuzzle p, String id, String input) {
		// We don't expose the id-string in Cage (uses int id assigned in declaration order),
		// so re-derive: count which cage comes first by encountering the id in the grid.
		String[] tokens = input.split("\\r?\\n");
		java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
		// Find the size line
		int n = p.spec.n;
		int rowIdx = 0;
		for (int i = 0; i < tokens.length && rowIdx < n; i++) {
			String line = tokens[i].trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			if (rowIdx == 0 && line.equals(String.valueOf(n))) { rowIdx = 0; continue; }
			String[] cells = line.split("\\s+");
			if (cells.length == n) {
				for (String c : cells) seen.add(c);
				rowIdx++;
			}
		}
		int order = 0;
		for (String s : seen) {
			order++;
			if (s.equals(id)) {
				for (Cage c : p.cages) if (c.id == order) return c;
			}
		}
		return null;
	}
}
