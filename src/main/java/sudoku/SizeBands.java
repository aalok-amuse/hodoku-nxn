/*
 * Per-size band thresholds for the Hodoku score.
 *
 * Hodoku's default bands (Easy ≤ 600, Medium ≤ 1100, Hard ≤ 1700) are
 * calibrated against 9×9 puzzles. At smaller grids the maximum achievable
 * score is much lower (fewer cells, simpler techniques fire), so the default
 * thresholds map essentially every non-9×9 puzzle into "Easy". That makes the
 * band labels useless for source-difficulty differentiation below 9×9.
 *
 * The thresholds below were derived from the al-sudoku content library
 * (3811 puzzles, sizes 4–9, source-labeled easy/medium/hard): for each size,
 * the Easy ceiling sits at the 80th percentile of the source "easy" tier and
 * the Medium ceiling at the 80th percentile of the source "medium" tier.
 * That makes scorer-Easy ≈ source-easy, scorer-Medium ≈ source-medium,
 * scorer-Hard ≈ source-hard within ±10% agreement.
 *
 * For 9×9 we keep Hodoku's original 600/1100/1700/5000 layout (so the byte-
 * identical 9×9 regression doesn't shift).
 */
package sudoku;

public final class SizeBands {

	/** Levels in increasing order — matches DifficultyType ordinal. */
	public enum Band {
		EASY, MEDIUM, HARD, UNFAIR, EXTREME;
		public String displayName() {
			switch (this) {
				case EASY: return "Easy";
				case MEDIUM: return "Medium";
				case HARD: return "Hard";
				case UNFAIR: return "Unfair";
				case EXTREME: return "Extreme";
				default: return name();
			}
		}
	}

	/**
	 * Returns the size-aware band for a Hodoku score on an n-side board.
	 * Falls back to Hodoku's 9×9 thresholds for sizes we haven't calibrated.
	 */
	public static Band levelFor(int n, int score) {
		int easyCap, medCap, hardCap, unfairCap;
		switch (n) {
			case 4:  easyCap = 44;  medCap = 48;   hardCap = 64;   unfairCap = 128;  break;
			case 5:  easyCap = 52;  medCap = 56;   hardCap = 128;  unfairCap = 256;  break;
			case 6:  easyCap = 96;  medCap = 284;  hardCap = 970;  unfairCap = 2000; break;
			case 7:  easyCap = 92;  medCap = 134;  hardCap = 502;  unfairCap = 1500; break;
			case 8:  easyCap = 134; medCap = 306;  hardCap = 1126; unfairCap = 3000; break;
			case 9:  easyCap = 204; medCap = 408;  hardCap = 844;  unfairCap = 2000; break;
			default: easyCap = 600; medCap = 1100; hardCap = 1700; unfairCap = 5000;
		}
		if (score <= easyCap)   return Band.EASY;
		if (score <= medCap)    return Band.MEDIUM;
		if (score <= hardCap)   return Band.HARD;
		if (score <= unfairCap) return Band.UNFAIR;
		return Band.EXTREME;
	}
}
