/*
 * XwordJson parser tests. Verifies that the minimal bracket-matching parser
 * extracts `box` and `preRevealIdxs` correctly from Amuse Labs JSON, and that
 * the resulting 81-char string aligns with col-major (row r, col c) →
 * `box[c][r]` semantics.
 */
package sudoku;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XwordJsonTest {

	/** Minimal hand-built JSON exercising both fields plus extra junk to skip. */
	private static final String MINIMAL_JSON =
		"{\n" +
		"  \"title\": \"test\",\n" +
		"  \"w\": 9, \"h\": 9,\n" +
		"  \"box\": [\n" +
		"    [\"1\",\"4\",\"7\",\"2\",\"5\",\"8\",\"3\",\"6\",\"9\"],\n" +  // col 0
		"    [\"2\",\"5\",\"8\",\"3\",\"6\",\"9\",\"4\",\"7\",\"1\"],\n" +  // col 1
		"    [\"3\",\"6\",\"9\",\"4\",\"7\",\"1\",\"5\",\"8\",\"2\"],\n" +
		"    [\"4\",\"7\",\"1\",\"5\",\"8\",\"2\",\"6\",\"9\",\"3\"],\n" +
		"    [\"5\",\"8\",\"2\",\"6\",\"9\",\"3\",\"7\",\"1\",\"4\"],\n" +
		"    [\"6\",\"9\",\"3\",\"7\",\"1\",\"4\",\"8\",\"2\",\"5\"],\n" +
		"    [\"7\",\"1\",\"4\",\"8\",\"2\",\"5\",\"9\",\"3\",\"6\"],\n" +
		"    [\"8\",\"2\",\"5\",\"9\",\"3\",\"6\",\"1\",\"4\",\"7\"],\n" +
		"    [\"9\",\"3\",\"6\",\"1\",\"4\",\"7\",\"2\",\"5\",\"8\"]\n" +
		"  ],\n" +
		"  \"preRevealIdxs\": [\n" +
		// col 0 — only row 0 revealed
		"    [true,false,false,false,false,false,false,false,false],\n" +
		"    [false,false,false,false,false,false,false,false,false],\n" +
		"    [false,false,false,false,false,false,false,false,false],\n" +
		"    [false,false,false,false,false,false,false,false,false],\n" +
		"    [false,false,false,false,false,false,false,false,false],\n" +
		"    [false,false,false,false,false,false,false,false,false],\n" +
		"    [false,false,false,false,false,false,false,false,false],\n" +
		"    [false,false,false,false,false,false,false,false,false],\n" +
		// col 8 — only row 8 revealed
		"    [false,false,false,false,false,false,false,false,true]\n" +
		"  ],\n" +
		"  \"extra\": \"should be ignored\"\n" +
		"}\n";

	@Test
	void parsesMinimalJson() {
		String line = XwordJson.jsonTo81(MINIMAL_JSON);
		assertEquals(81, line.length());
		// (row 0, col 0): preRevealIdxs[0][0] = true, box[0][0] = '1'
		assertEquals('1', line.charAt(0));
		// (row 8, col 8): preRevealIdxs[8][8] = true, box[8][8] = '8'
		assertEquals('8', line.charAt(80));
		// All other 79 cells should be '.'
		for (int i = 1; i < 80; i++) {
			assertEquals('.', line.charAt(i), "cell " + i + " should be empty");
		}
	}

	@Test
	void rejectsMalformedJson() {
		assertThrows(IllegalArgumentException.class,
		             () -> XwordJson.jsonTo81("{\"box\":[]}"),
		             "missing preRevealIdxs should throw");
		assertThrows(IllegalArgumentException.class,
		             () -> XwordJson.jsonTo81("{}"),
		             "no box should throw");
	}

	@Test
	void colMajorOrderingIsRespected() {
		// Build a minimal xword.json where preReveal marks two cells in different
		// col/row positions, and verify the resulting 81-char string places the
		// digit at the row-major index that corresponds to box[c][r] (col-major).
		String json = "{ \"box\": [" +
		    // 9 columns, each with 9 rows. Each col[i] = ['c'+i] repeated.
		    rep("\"1\",", 9) + "\b" +    // sentinel, will be overwritten below
		    "]}";
		// The above is just a placeholder; build properly:
		StringBuilder sb = new StringBuilder("{\"box\":[");
		for (int c = 0; c < 9; c++) {
			if (c > 0) sb.append(',');
			sb.append('[');
			for (int r = 0; r < 9; r++) {
				if (r > 0) sb.append(',');
				int v = ((c * 9 + r) % 9) + 1;
				sb.append('"').append(v).append('"');
			}
			sb.append(']');
		}
		sb.append("],\"preRevealIdxs\":[");
		// preReveal[3][7] = true (i.e., row 7 col 3 — index 7*9+3 = 66 should hold the digit)
		// preReveal[0][0] = true (index 0)
		// Everything else false.
		for (int c = 0; c < 9; c++) {
			if (c > 0) sb.append(',');
			sb.append('[');
			for (int r = 0; r < 9; r++) {
				if (r > 0) sb.append(',');
				boolean keep = (c == 0 && r == 0) || (c == 3 && r == 7);
				sb.append(keep ? "true" : "false");
			}
			sb.append(']');
		}
		sb.append("]}");
		String line = XwordJson.jsonTo81(sb.toString());
		assertEquals(81, line.length());
		// (row 0, col 0) → digit from box[0][0]
		int expectedAt0 = ((0 * 9 + 0) % 9) + 1;
		assertEquals(Character.forDigit(expectedAt0, 10), line.charAt(0));
		// (row 7, col 3) → row-major index 7*9+3 = 66, digit from box[3][7]
		int expectedAt66 = ((3 * 9 + 7) % 9) + 1;
		assertEquals(Character.forDigit(expectedAt66, 10), line.charAt(66));
	}

	private static String rep(String s, int n) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < n; i++) b.append(s);
		return b.toString();
	}
}
