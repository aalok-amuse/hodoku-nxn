/*
 * One step taken by the Killer scorer: which technique fired, what cell
 * was filled, and what value. Kept as a compact record for diagnostics
 * and step-by-step replay.
 */
package sudoku.killer;

public final class KillerStep {

	public final KillerTechnique technique;
	/** Cell index that was filled, or -1 if the step was a pure-elimination. */
	public final int cell;
	/** Digit placed, or 0 if elimination-only. */
	public final int digit;

	public KillerStep(KillerTechnique technique, int cell, int digit) {
		this.technique = technique;
		this.cell = cell;
		this.digit = digit;
	}

	@Override
	public String toString() {
		if (cell < 0) {
			return technique.displayName + " (elimination)";
		}
		return technique.displayName + " — cell " + cell + " = " + digit;
	}
}
