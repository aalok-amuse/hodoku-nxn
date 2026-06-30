/*
 * Tests for the Amuse-flavoured Killer JSON parser.
 */
package sudoku.killer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillerJsonTest {

	private static final String SAMPLE_JSON =
		"{\n" +
		"  \"name\": \"Killer Sudoku\",\n" +
		"  \"game_data\": {\n" +
		"    \"rows\": 3,\n" +
		"    \"cols\": 3,\n" +
		"    \"solmap\": \"819376542327154698645928173761835429938462715452719836283547961576291384194683257\",\n" +
		"    \"plevel\": 1,\n" +
		"    \"clusters\": [\n" +
		"      { \"sum\": 11, \"cells\": [1, 10] },\n" +
		"      { \"sum\": 19, \"cells\": [2, 3, 11, 12] },\n" +
		"      { \"sum\": 9,  \"cells\": [4, 13, 14] },\n" +
		"      { \"sum\": 13, \"cells\": [5, 6] },\n" +
		"      { \"sum\": 11, \"cells\": [7, 16] },\n" +
		"      { \"sum\": 6,  \"cells\": [8, 9] },\n" +
		"      { \"sum\": 12, \"cells\": [15, 24] },\n" +
		"      { \"sum\": 21, \"cells\": [17, 25, 26, 34] },\n" +
		"      { \"sum\": 11, \"cells\": [18, 27] },\n" +
		"      { \"sum\": 10, \"cells\": [19, 20] },\n" +
		"      { \"sum\": 12, \"cells\": [21, 29, 30] },\n" +
		"      { \"sum\": 22, \"cells\": [22, 23, 31, 32] },\n" +
		"      { \"sum\": 16, \"cells\": [28, 37] },\n" +
		"      { \"sum\": 14, \"cells\": [33, 42, 43] },\n" +
		"      { \"sum\": 17, \"cells\": [35, 36, 44, 45] },\n" +
		"      { \"sum\": 16, \"cells\": [38, 47, 56] },\n" +
		"      { \"sum\": 18, \"cells\": [39, 40, 41] },\n" +
		"      { \"sum\": 12, \"cells\": [46, 55, 64, 73] },\n" +
		"      { \"sum\": 5,  \"cells\": [48, 57] },\n" +
		"      { \"sum\": 12, \"cells\": [49, 58] },\n" +
		"      { \"sum\": 5,  \"cells\": [50, 59] },\n" +
		"      { \"sum\": 16, \"cells\": [51, 60] },\n" +
		"      { \"sum\": 17, \"cells\": [52, 61] },\n" +
		"      { \"sum\": 9,  \"cells\": [53, 54] },\n" +
		"      { \"sum\": 19, \"cells\": [62, 63, 71, 72] },\n" +
		"      { \"sum\": 16, \"cells\": [65, 74] },\n" +
		"      { \"sum\": 8,  \"cells\": [66, 67] },\n" +
		"      { \"sum\": 17, \"cells\": [68, 77] },\n" +
		"      { \"sum\": 4,  \"cells\": [69, 78] },\n" +
		"      { \"sum\": 5,  \"cells\": [70, 79] },\n" +
		"      { \"sum\": 10, \"cells\": [75, 76] },\n" +
		"      { \"sum\": 12, \"cells\": [80, 81] }\n" +
		"    ]\n" +
		"  }\n" +
		"}\n";

	@Test
	void parsesSampleKillerJson() {
		KillerPuzzle p = KillerJson.fromString(SAMPLE_JSON);
		assertEquals(9, p.spec.n);
		assertEquals(81, p.spec.length);
		assertEquals(32, p.cages.length, "sample has 32 cages");
		// First cage: cells 1, 10 → indices 0, 9 → row 0 col 0 and row 1 col 0.
		Cage first = p.cages[0];
		assertEquals(11, first.sum);
		assertArrayEquals(new int[]{ 0, 9 }, first.cells);
	}

	@Test
	void sampleKillerSolvesAndMatchesSolmap() {
		KillerPuzzle p = KillerJson.fromString(SAMPLE_JSON);
		String solmap = KillerJson.readSolmap(SAMPLE_JSON);
		assertNotNull(solmap);
		assertEquals(81, solmap.length());

		KillerSolver.Result r = KillerSolver.solve(p);
		assertNotNull(r.solution, "the sample Killer should solve");
		// Solver result should match the published solmap.
		for (int i = 0; i < 81; i++) {
			assertEquals(solmap.charAt(i) - '0', r.solution[i],
			    "mismatch at cell " + i + ": solver=" + r.solution[i]
			    + ", solmap=" + solmap.charAt(i));
		}
	}

	@Test
	void rejectsMissingClusters() {
		String bad = "{\"game_data\":{\"rows\":3,\"cols\":3}}";
		assertThrows(IllegalArgumentException.class, () -> KillerJson.fromString(bad));
	}

	@Test
	void rejectsCellOutOfRange() {
		String bad = "{\"game_data\":{\"rows\":3,\"cols\":3,\"clusters\":[" +
		    "{\"sum\":5,\"cells\":[82, 83]}]}}";
		assertThrows(IllegalArgumentException.class, () -> KillerJson.fromString(bad));
	}
}
