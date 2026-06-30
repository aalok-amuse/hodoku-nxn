/*
 * Parser for the Amuse-flavoured Killer Sudoku JSON format.
 *
 * Expected shape (only the fields below are read; others are ignored):
 *
 *   {
 *     "game_data": {
 *       "rows": 3,                     ← box height (cells per box, vertical)
 *       "cols": 3,                     ← box width  (cells per box, horizontal)
 *       "solmap": "8193…",             ← optional: 81-char solution (used for
 *                                        verification only; the solver doesn't
 *                                        consume it)
 *       "clusters": [
 *         { "sum": 11, "cells": [1, 10] },
 *         { "sum": 19, "cells": [2, 3, 11, 12] },
 *         …
 *       ]
 *     }
 *   }
 *
 * Cells are **1-indexed** in this format (cell 1 = row 0, col 0; cell 10 =
 * row 1, col 0 for a 9×9 grid). They're converted to 0-indexed internally.
 *
 * Board size is derived: n = rows × cols. Supported sizes: 4, 5, 6, 7, 9, 16.
 * For Latin-square specs (5, 7), the rows/cols fields are not meaningful and
 * not used.
 */
package sudoku.killer;

import sudoku.BoardSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class KillerJson {

	private KillerJson() {}

	/** Read a Killer JSON file and return the puzzle. */
	public static KillerPuzzle fromFile(Path file) throws IOException {
		return fromString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
	}

	/** Parse a JSON string and return the puzzle. */
	public static KillerPuzzle fromString(String json) {
		int rows = readInt(json, "rows");
		int cols = readInt(json, "cols");
		int n = rows * cols;
		BoardSpec spec = BoardSpec.of(n);

		// Iterate the clusters array, extracting one Cage per object.
		int clustersStart = findArrayStart(json, "clusters");
		int clustersEnd = matchBracket(json, clustersStart);
		List<Cage> cageList = new ArrayList<>();
		int i = clustersStart + 1;
		while (i < clustersEnd) {
			while (i < clustersEnd && json.charAt(i) != '{') i++;
			if (i >= clustersEnd) break;
			int end = matchBracket(json, i);
			String obj = json.substring(i, end + 1);
			int sum = readInt(obj, "sum");
			int[] cells = readIntArray(obj, "cells");
			// Convert 1-indexed → 0-indexed.
			for (int k = 0; k < cells.length; k++) {
				cells[k] -= 1;
				if (cells[k] < 0 || cells[k] >= spec.length) {
					throw new IllegalArgumentException("cluster cell index out of range: "
					    + (cells[k] + 1) + " (expected 1.." + spec.length + ")");
				}
			}
			cageList.add(new Cage(cageList.size() + 1, sum, cells));
			i = end + 1;
		}

		return new KillerPuzzle(spec, cageList.toArray(new Cage[0]));
	}

	/** Read the expected solution string (`solmap`) if present, else null. */
	public static String readSolmap(String json) {
		int k = json.indexOf("\"solmap\"");
		if (k < 0) return null;
		int i = k + "\"solmap\"".length();
		while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;
		if (i >= json.length() || json.charAt(i) != '"') return null;
		int start = ++i;
		StringBuilder sb = new StringBuilder();
		while (i < json.length() && json.charAt(i) != '"') {
			char c = json.charAt(i);
			if (c == '\\' && i + 1 < json.length()) { sb.append(json.charAt(i + 1)); i += 2; }
			else { sb.append(c); i++; }
		}
		return sb.toString();
	}

	// -----------------------------------------------------------------
	// Minimal scanner — bracket-matching, string-aware. Same approach
	// as XwordJson; sufficient for well-formed input.
	// -----------------------------------------------------------------

	/** Find {@code "key":} and return the integer value that follows. */
	private static int readInt(String json, String key) {
		String pat = "\"" + key + "\"";
		int k = json.indexOf(pat);
		if (k < 0) {
			throw new IllegalArgumentException("missing key: " + key);
		}
		int i = k + pat.length();
		while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;
		int sign = 1;
		if (i < json.length() && json.charAt(i) == '-') { sign = -1; i++; }
		int start = i;
		while (i < json.length() && Character.isDigit(json.charAt(i))) i++;
		if (i == start) {
			throw new IllegalArgumentException("value of " + key + " is not an integer");
		}
		return sign * Integer.parseInt(json.substring(start, i));
	}

	/** Find {@code "key":} and return the array of integers that follows. */
	private static int[] readIntArray(String json, String key) {
		int start = findArrayStart(json, key);
		int end = matchBracket(json, start);
		List<Integer> out = new ArrayList<>();
		int i = start + 1;
		while (i < end) {
			while (i < end && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
			if (i >= end) break;
			int sign = 1;
			if (json.charAt(i) == '-') { sign = -1; i++; }
			int from = i;
			while (i < end && Character.isDigit(json.charAt(i))) i++;
			if (i > from) {
				out.add(sign * Integer.parseInt(json.substring(from, i)));
			}
		}
		int[] arr = new int[out.size()];
		for (int k = 0; k < arr.length; k++) arr[k] = out.get(k);
		return arr;
	}

	/** Locate {@code "key":} and return the index of the next '['. */
	private static int findArrayStart(String json, String key) {
		String pat = "\"" + key + "\"";
		int k = json.indexOf(pat);
		if (k < 0) {
			throw new IllegalArgumentException("missing key: " + key);
		}
		int i = k + pat.length();
		while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;
		if (i >= json.length() || json.charAt(i) != '[') {
			throw new IllegalArgumentException("value of " + key + " is not an array");
		}
		return i;
	}

	/** Bracket-match {@code […]} or {@code {…}} (string-aware). */
	private static int matchBracket(String json, int start) {
		char open = json.charAt(start);
		char close = (open == '[') ? ']' : (open == '{') ? '}' : 0;
		if (close == 0) {
			throw new IllegalArgumentException("not a bracket at " + start);
		}
		int depth = 0;
		boolean inString = false;
		for (int i = start; i < json.length(); i++) {
			char c = json.charAt(i);
			if (inString) {
				if (c == '\\' && i + 1 < json.length()) i++;
				else if (c == '"') inString = false;
			} else {
				if (c == '"') inString = true;
				else if (c == open) depth++;
				else if (c == close) {
					depth--;
					if (depth == 0) return i;
				}
			}
		}
		throw new IllegalArgumentException("unbalanced bracket starting at " + start);
	}
}
