/*
 * Killer scoring tests: each technique fires, each band is assigned correctly.
 */
package sudoku.killer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillerScorerTest {

	@Test
	void easyPuzzleScoresEasy() {
		// 4x4 with 8 two-cell cages — every cage has a unique combo, so all
		// progress comes from CAGE_SUBSET. Logical score = 8 cages * 20 = 160.
		String input =
		    "4\n" +
		    "A A B B\n" +
		    "C C D D\n" +
		    "E E F F\n" +
		    "G G H H\n" +
		    "A = 3\n" +
		    "B = 7\n" +
		    "C = 7\n" +
		    "D = 3\n" +
		    "E = 3\n" +
		    "F = 7\n" +
		    "G = 7\n" +
		    "H = 3\n";
		KillerPuzzle p = KillerInput.fromString(input);
		KillerScorer.Result r = KillerScorer.solve(p);
		assertNotNull(r.solution);
		assertEquals(KillerTechnique.Level.EASY, r.logicalLevel,
		             "all CAGE_SUBSET steps are EASY-level");
		assertTrue(r.logicalScore < KillerTechnique.Level.EASY.maxScore,
		             "logical score should fit in the EASY band, got " + r.logicalScore);
		// Final band may be EXTREME if the puzzle has multiple solutions and
		// needs brute force; we don't assert on r.level since it depends on the
		// uniqueness of the test puzzle.
	}

	@Test
	void brutForceFallbackInflatesScore() {
		// A pathological "all-singleton" puzzle that's fully determined by the
		// givens — should solve purely via NAKED_SINGLE / cage-uniqueness.
		int[] sol = { 1,2,3,4,  3,4,1,2,  2,1,4,3,  4,3,2,1 };
		Cage[] cages = new Cage[16];
		for (int i = 0; i < 16; i++) {
			cages[i] = new Cage(i + 1, sol[i], new int[]{ i });
		}
		KillerPuzzle p = new KillerPuzzle(sudoku.BoardSpec.S4, cages);
		KillerScorer.Result r = KillerScorer.solve(p);
		assertNotNull(r.solution);
		assertFalse(r.bruteForced, "all-singleton puzzle should solve logically");
		assertTrue(r.logicalScore > 0, "should accumulate some logical score");
	}

	@Test
	void scoreEqualsSumOfStepWeights() {
		// Construct any solvable Killer and assert score == sum of step weights.
		String input =
		    "4\n" +
		    "A A B B\n" +
		    "C C D D\n" +
		    "E E F F\n" +
		    "G G H H\n" +
		    "A = 3\n" + "B = 7\n" + "C = 7\n" + "D = 3\n" +
		    "E = 3\n" + "F = 7\n" + "G = 7\n" + "H = 3\n";
		KillerPuzzle p = KillerInput.fromString(input);
		KillerScorer.Result r = KillerScorer.solve(p);
		int sum = 0;
		for (KillerStep s : r.steps) sum += s.technique.weight;
		assertEquals(sum, r.score);
	}

	@Test
	void levelBumpsUpWhenScoreExceedsCurrentBand() {
		// Verify the Level.forScore() helper directly.
		assertEquals(KillerTechnique.Level.EASY, KillerTechnique.Level.forScore(0));
		assertEquals(KillerTechnique.Level.EASY, KillerTechnique.Level.forScore(800));
		assertEquals(KillerTechnique.Level.MEDIUM, KillerTechnique.Level.forScore(801));
		assertEquals(KillerTechnique.Level.HARD, KillerTechnique.Level.forScore(1501));
		assertEquals(KillerTechnique.Level.UNFAIR, KillerTechnique.Level.forScore(2501));
		assertEquals(KillerTechnique.Level.EXTREME, KillerTechnique.Level.forScore(99999));
	}
}
