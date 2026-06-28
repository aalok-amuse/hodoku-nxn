/*
 * Minimal Amuse Labs .xword.json reader. Extracts only the two
 * fields needed for Sudoku scoring: `box` (9x9 solution, col-major) and
 * `preRevealIdxs` (9x9 booleans marking givens, col-major). Everything else
 * in the file is ignored.
 *
 * No external JSON dependency — a small bracket-matching scanner covers the
 * well-formed input produced by Amuse Labs.
 *
 * Output: an 81-character string in Hodoku format: digits 1-9 for givens,
 * '.' for empties, row-major.
 */
package sudoku;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class XwordJson {

	private XwordJson() {}

	/** Read an .xword.json file and return its 81-character Hodoku puzzle string. */
	public static String fileTo81(Path file) throws IOException {
		String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		return jsonTo81(json);
	}

	/** Parse a JSON string and return its 81-character Hodoku puzzle. */
	public static String jsonTo81(String json) {
		String[][] box       = readStringArray2D(json, "box");
		boolean[][] preReveal = readBooleanArray2D(json, "preRevealIdxs");
		if (box.length != 9 || preReveal.length != 9) {
			throw new IllegalArgumentException("xword.json must be 9x9 (got " + box.length + ")");
		}
		// Amuse Labs uses col-major: box[c][r] is the cell at (row r, col c).
		StringBuilder sb = new StringBuilder(81);
		for (int r = 0; r < 9; r++) {
			for (int c = 0; c < 9; c++) {
				if (preReveal[c][r]) {
					String digit = box[c][r];
					if (digit == null || digit.length() != 1) {
						throw new IllegalArgumentException("bad digit at (r=" + r + ",c=" + c + "): " + digit);
					}
					sb.append(digit.charAt(0));
				} else {
					sb.append('.');
				}
			}
		}
		return sb.toString();
	}

	// -----------------------------------------------------------------
	// Minimal JSON helpers — bracket-matching scanner. Sufficient for the
	// well-formed input produced by Amuse Labs; not a general JSON parser.
	// -----------------------------------------------------------------

	/** Locate {@code "key":} and return the index of the next '['. */
	private static int findArrayStart(String json, String key) {
		String pat = "\"" + key + "\"";
		int k = json.indexOf(pat);
		if (k < 0) {
			throw new IllegalArgumentException("missing key in JSON: " + key);
		}
		int i = k + pat.length();
		// Skip whitespace and colon.
		while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) {
			i++;
		}
		if (i >= json.length() || json.charAt(i) != '[') {
			throw new IllegalArgumentException("value of " + key + " is not an array");
		}
		return i;
	}

	/**
	 * Bracket-match: given a '[' at {@code start}, return the index of the
	 * matching ']'. Tracks string quoting so brackets inside strings don't count.
	 */
	private static int matchBracket(String json, int start) {
		int depth = 0;
		boolean inString = false;
		for (int i = start; i < json.length(); i++) {
			char c = json.charAt(i);
			if (inString) {
				if (c == '\\' && i + 1 < json.length()) i++;       // skip escaped char
				else if (c == '"') inString = false;
			} else {
				if (c == '"') inString = true;
				else if (c == '[') depth++;
				else if (c == ']') {
					depth--;
					if (depth == 0) return i;
				}
			}
		}
		throw new IllegalArgumentException("unbalanced brackets starting at " + start);
	}

	/** Read a 2D array of quoted strings — e.g. [["a","b"],["c","d"]]. */
	private static String[][] readStringArray2D(String json, String key) {
		int outerStart = findArrayStart(json, key);
		int outerEnd = matchBracket(json, outerStart);
		String[] rows = splitTopLevelArrays(json, outerStart + 1, outerEnd);
		String[][] out = new String[rows.length][];
		for (int i = 0; i < rows.length; i++) {
			out[i] = readStrings(rows[i]);
		}
		return out;
	}

	/** Read a 2D array of true/false booleans. */
	private static boolean[][] readBooleanArray2D(String json, String key) {
		int outerStart = findArrayStart(json, key);
		int outerEnd = matchBracket(json, outerStart);
		String[] rows = splitTopLevelArrays(json, outerStart + 1, outerEnd);
		boolean[][] out = new boolean[rows.length][];
		for (int i = 0; i < rows.length; i++) {
			out[i] = readBooleans(rows[i]);
		}
		return out;
	}

	/**
	 * Inside a [[..],[..],[..]] block (between the outer brackets), return each
	 * inner array's contents (still bracketed: "[..]" each).
	 */
	private static String[] splitTopLevelArrays(String json, int from, int toExclusive) {
		java.util.List<String> parts = new java.util.ArrayList<>();
		int i = from;
		while (i < toExclusive) {
			while (i < toExclusive && json.charAt(i) != '[') i++;
			if (i >= toExclusive) break;
			int end = matchBracket(json, i);
			parts.add(json.substring(i, end + 1));
			i = end + 1;
		}
		return parts.toArray(new String[0]);
	}

	/** Parse "[\"a\",\"b\",\"c\"]" into ["a","b","c"]. */
	private static String[] readStrings(String inner) {
		java.util.List<String> out = new java.util.ArrayList<>();
		int i = 0;
		while (i < inner.length()) {
			while (i < inner.length() && inner.charAt(i) != '"') i++;
			if (i >= inner.length()) break;
			int start = ++i;
			StringBuilder sb = new StringBuilder();
			while (i < inner.length() && inner.charAt(i) != '"') {
				char c = inner.charAt(i);
				if (c == '\\' && i + 1 < inner.length()) {
					sb.append(inner.charAt(i + 1));
					i += 2;
				} else {
					sb.append(c);
					i++;
				}
			}
			out.add(sb.toString());
			i++;
		}
		return out.toArray(new String[0]);
	}

	/** Parse "[true,false,true]" into [true,false,true]. */
	private static boolean[] readBooleans(String inner) {
		java.util.List<Boolean> out = new java.util.ArrayList<>();
		int i = 0;
		while (i < inner.length()) {
			char c = inner.charAt(i);
			if (c == 't' && inner.regionMatches(i, "true", 0, 4)) {
				out.add(Boolean.TRUE);
				i += 4;
			} else if (c == 'f' && inner.regionMatches(i, "false", 0, 5)) {
				out.add(Boolean.FALSE);
				i += 5;
			} else {
				i++;
			}
		}
		boolean[] arr = new boolean[out.size()];
		for (int k = 0; k < arr.length; k++) arr[k] = out.get(k);
		return arr;
	}
}
