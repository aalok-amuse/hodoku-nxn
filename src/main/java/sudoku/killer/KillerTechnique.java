/*
 * Named Killer Sudoku solving techniques, each with a weight (added to the
 * running score every time the technique fires) and a level (the difficulty
 * band the technique introduces).
 *
 * Weights are roughly modelled on Hodoku's standard-Sudoku weights for the
 * techniques that have direct analogues (NAKED_SINGLE = 4, HIDDEN_SINGLE =
 * 14, LOCKED_CANDIDATES = 40). Killer-specific techniques are assigned
 * weights that reflect the reasoning depth required.
 *
 * The driver tries techniques in declaration order — easier first — and adds
 * the weight of each step taken to the puzzle's score.
 */
package sudoku.killer;

public enum KillerTechnique {

	NAKED_SINGLE        (   4, Level.EASY,    "Naked Single"),
	HIDDEN_SINGLE       (  14, Level.EASY,    "Hidden Single"),
	CAGE_UNIQUENESS     (  10, Level.EASY,    "Cage Uniqueness"),
	CAGE_SUBSET         (  20, Level.EASY,    "Cage Subset (forced combination)"),
	LOCKED_CANDIDATES   (  40, Level.MEDIUM,  "Locked Candidates"),
	CAGE_45_INNIE       (  60, Level.MEDIUM,  "45-Rule Innie"),
	CAGE_45_OUTIE       (  60, Level.MEDIUM,  "45-Rule Outie"),
	BRUTE_FORCE         (5000, Level.EXTREME, "Brute Force");

	public final int weight;
	public final Level level;
	public final String displayName;

	KillerTechnique(int weight, Level level, String displayName) {
		this.weight = weight;
		this.level = level;
		this.displayName = displayName;
	}

	/** Difficulty bands. Each technique introduces its level; the puzzle's level is the max over its steps. */
	public enum Level {
		EASY     ( 800),
		MEDIUM   (1500),
		HARD     (2500),
		UNFAIR   (4000),
		EXTREME  (Integer.MAX_VALUE);

		/** Maximum score for this band; if score exceeds this, the puzzle is promoted to the next band. */
		public final int maxScore;

		Level(int maxScore) { this.maxScore = maxScore; }

		/** Return the lowest band whose maxScore covers the given total score. */
		public static Level forScore(int score) {
			for (Level l : values()) {
				if (score <= l.maxScore) return l;
			}
			return EXTREME;
		}
	}
}
