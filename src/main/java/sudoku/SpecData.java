/*
 * Part of hodoku-nxn — a fork of HoDoKu generalised for multiple board sizes.
 *
 * Per-{@link BoardSpec} pre-computed lookup tables. The 9x9 instance reproduces
 * the contents of the long-standing static fields on {@link Sudoku2}
 * (ANZ_VALUES, POSSIBLE_VALUES, CAND_FROM_MASK, buddies, *_TEMPLATES, ...)
 * so existing 9x9 solver paths can migrate to {@code sudoku.getSpecData().X}
 * without any score change.
 *
 * For non-9x9 specs the same tables are computed from first principles. Most
 * scale linearly with the board (size, units, buddies). Two scale with 2^n
 * (mask lookups) — fine for n in {4,5,6,7,9,16} (max 65,536 entries).
 *
 * One field is 9x9-only: {@link #templates} (46,656 pre-serialised placements).
 * On non-S9 specs that field is {@code null}; callers must guard.
 */
package sudoku;

public final class SpecData {

	/** The board descriptor this data was computed for. */
	public final BoardSpec spec;

	// -------------------------------------------------------------------
	// Mask lookups — sized 2^n.
	// -------------------------------------------------------------------

	/** Number of set bits in each n-bit mask. */
	public final int[] anzValues;
	/** Digit (1..n) corresponding to the lowest set bit of each mask, 0 if mask is 0. */
	public final short[] candFromMask;
	/** For each mask, an int[] of the digits (1..n) whose bit is set. */
	public final int[][] possibleValues;

	// -------------------------------------------------------------------
	// Per-cell precomputed sets.
	// -------------------------------------------------------------------

	/** buddies[cell] = SudokuSet of all peers (same row/col/box, excluding the cell). */
	public final SudokuSet[] buddies;
	/** Legacy 9x9 buddy split: buddies[cell].getWord(0). Only valid for n=9. */
	public final long[] buddiesM1;
	/** Legacy 9x9 buddy split: buddies[cell].getWord(1). Only valid for n=9. */
	public final long[] buddiesM2;

	// -------------------------------------------------------------------
	// Unit templates (cells of each unit as a SudokuSet).
	// -------------------------------------------------------------------

	public final SudokuSet[] rowTemplates;
	public final SudokuSet[] colTemplates;
	public final SudokuSet[] blockTemplates;          // empty for Latin specs
	public final SudokuSet[] rowBlockTemplates;       // rows... + boxes...; empty for Latin specs
	public final SudokuSet[] colBlockTemplates;       // cols... + boxes...; empty for Latin specs
	public final SudokuSet[] allConstraintsTemplates; // rows... + cols... + boxes...

	/** Legacy 9x9 splits of allConstraintsTemplates (low/high 64-bit words). Only valid for n=9. */
	public final long[] allConstraintsTemplatesM1;
	public final long[] allConstraintsTemplatesM2;

	// -------------------------------------------------------------------
	// Grouped buddies (vectorised buddy intersection used by getBuddies()).
	//
	// For each 8-cell group g and each 8-bit combination m, holds the
	// intersection of buddies[c] over the cells c in group g that are
	// active in m. The driver pre-decomposes a set into 11 (for 9x9)
	// or 32 (for 16x16) bytes and AND-reduces by table lookup.
	// -------------------------------------------------------------------

	public final SudokuSetBase[][] groupedBuddies;
	public final long[][] groupedBuddiesM1;
	public final long[][] groupedBuddiesM2;

	// -------------------------------------------------------------------
	// Constraint type/number lookups.
	// -------------------------------------------------------------------

	/** For each unit, its type: {@link Sudoku2#ROW}, {@link Sudoku2#COL}, or {@link Sudoku2#BLOCK}. */
	public final int[] constraintType;
	/** For each unit, its 1-based number within its type. */
	public final int[] constraintNumber;
	/** For each cell, its box index, or -1 if the spec has no boxes. */
	public final int[] blockFromIndex;

	// -------------------------------------------------------------------
	// 9x9-only: pre-serialised templates loaded from templates.dat.
	// -------------------------------------------------------------------

	/** Pre-computed 9x9 single-digit placements (46,656 entries). {@code null} for non-S9. */
	public final SudokuSetBase[] templates;

	// -------------------------------------------------------------------

	/** The pre-built data for the standard 9x9 spec. */
	public static final SpecData S9 = new SpecData(BoardSpec.S9);

	/** Per-spec cache. */
	private static final java.util.Map<BoardSpec, SpecData> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	static { CACHE.put(BoardSpec.S9, S9); }

	/** @return the cached {@link SpecData} for {@code spec}, computing it on first use. */
	public static SpecData for_(BoardSpec spec) {
		return CACHE.computeIfAbsent(spec, SpecData::new);
	}

	/** Constructs spec data for the given board. Cheap for small n; ~70 MB for n=16 worst case. */
	public SpecData(BoardSpec spec) {
		this.spec = spec;
		int n = spec.n;
		int length = spec.length;
		int maskCount = 1 << n;

		// ---- Mask lookups ----
		this.anzValues = new int[maskCount];
		this.candFromMask = new short[maskCount];
		this.possibleValues = new int[maskCount][];
		int[] tmp = new int[n];
		for (int m = 0; m < maskCount; m++) {
			anzValues[m] = Integer.bitCount(m);
			candFromMask[m] = (short) (m == 0 ? 0 : Integer.numberOfTrailingZeros(m) + 1);
			int idx = 0;
			for (int d = 1; d <= n; d++) {
				if ((m & (1 << (d - 1))) != 0) tmp[idx++] = d;
			}
			possibleValues[m] = new int[idx];
			System.arraycopy(tmp, 0, possibleValues[m], 0, idx);
		}

		// ---- Per-cell buddies ----
		this.buddies = new SudokuSet[length];
		this.buddiesM1 = new long[length];
		this.buddiesM2 = new long[length];
		for (int cell = 0; cell < length; cell++) {
			SudokuSet bs = new SudokuSet(spec);
			for (int unitIdx : spec.constraints[cell]) {
				for (int peer : spec.allUnits[unitIdx]) {
					if (peer != cell) bs.add(peer);
				}
			}
			buddies[cell] = bs;
			buddiesM1[cell] = bs.getWord(0);
			buddiesM2[cell] = bs.getWordCount() > 1 ? bs.getWord(1) : 0L;
		}

		// ---- Unit templates ----
		this.rowTemplates = templatesFor(spec, spec.rows);
		this.colTemplates = templatesFor(spec, spec.cols);
		this.blockTemplates = spec.hasBoxes ? templatesFor(spec, spec.boxes) : new SudokuSet[0];
		this.allConstraintsTemplates = templatesFor(spec, spec.allUnits);
		this.allConstraintsTemplatesM1 = new long[allConstraintsTemplates.length];
		this.allConstraintsTemplatesM2 = new long[allConstraintsTemplates.length];
		for (int u = 0; u < allConstraintsTemplates.length; u++) {
			SudokuSet s = allConstraintsTemplates[u];
			allConstraintsTemplatesM1[u] = s.getWord(0);
			allConstraintsTemplatesM2[u] = s.getWordCount() > 1 ? s.getWord(1) : 0L;
		}

		if (spec.hasBoxes) {
			// rowBlockTemplates = rows... + boxes...
			SudokuSet[] rb = new SudokuSet[2 * n];
			System.arraycopy(rowTemplates, 0, rb, 0, n);
			System.arraycopy(blockTemplates, 0, rb, n, n);
			this.rowBlockTemplates = rb;
			// colBlockTemplates = cols... + boxes...
			SudokuSet[] cb = new SudokuSet[2 * n];
			System.arraycopy(colTemplates, 0, cb, 0, n);
			System.arraycopy(blockTemplates, 0, cb, n, n);
			this.colBlockTemplates = cb;
		} else {
			this.rowBlockTemplates = new SudokuSet[0];
			this.colBlockTemplates = new SudokuSet[0];
		}

		// ---- Constraint type / number ----
		this.constraintType = new int[spec.allUnits.length];
		this.constraintNumber = new int[spec.allUnits.length];
		for (int u = 0; u < spec.allUnits.length; u++) {
			if (u < n)            { constraintType[u] = Sudoku2.ROW;   constraintNumber[u] = u + 1; }
			else if (u < 2 * n)   { constraintType[u] = Sudoku2.COL;   constraintNumber[u] = u - n + 1; }
			else                  { constraintType[u] = Sudoku2.BLOCK; constraintNumber[u] = u - 2 * n + 1; }
		}

		// ---- Block from index ----
		this.blockFromIndex = new int[length];
		for (int cell = 0; cell < length; cell++) {
			blockFromIndex[cell] = spec.hasBoxes ? spec.constraints[cell][2] - 2 * n : -1;
		}

		// ---- Grouped buddies ----
		int groupCount = (length + 7) >>> 3; // ceil(length / 8)
		this.groupedBuddies = new SudokuSetBase[groupCount][256];
		this.groupedBuddiesM1 = new long[groupCount][256];
		this.groupedBuddiesM2 = new long[groupCount][256];
		for (int g = 0; g < groupCount; g++) {
			int groupOffset = g * 8;
			for (int m = 0; m < 256; m++) {
				// Match Sudoku2.initGroupForGroupedBuddies: start from the FULL set
				// and AND with buddies of each active cell. An empty mask leaves
				// the full set in place (callers depend on this).
				SudokuSetBase intersection = new SudokuSetBase(spec);
				intersection.setAll();
				for (int b = 0; b < 8; b++) {
					int cell = groupOffset + b;
					if (cell >= length) continue;
					if ((m & (1 << b)) != 0) {
						intersection.and(buddies[cell]);
					}
				}
				groupedBuddies[g][m] = intersection;
				groupedBuddiesM1[g][m] = intersection.getWord(0);
				groupedBuddiesM2[g][m] = intersection.getWordCount() > 1 ? intersection.getWord(1) : 0L;
			}
		}

		// ---- Templates (9x9 only — populated lazily by Sudoku2.initTemplates) ----
		this.templates = (n == 9 && spec.hasBoxes) ? Sudoku2.templates : null;
	}

	/**
	 * Computes the common buddies of all cells in {@code cells} into
	 * {@code buddiesOut}, using {@link #groupedBuddies} for vectorised lookup.
	 * Size-generic version of {@link Sudoku2#getBuddies(SudokuSetBase, SudokuSetBase)}.
	 */
	public void getBuddies(SudokuSetBase cells, SudokuSetBase buddiesOut) {
		buddiesOut.setAll();
		int groupCount = groupedBuddies.length;
		int wordCount = cells.getWordCount();
		for (int w = 0; w < wordCount; w++) {
			long mask = cells.getWord(w);
			if (mask == 0) continue;
			int baseGroup = w * 8;
			for (int b = 0; b < 8; b++) {
				int groupIdx = baseGroup + b;
				if (groupIdx >= groupCount) break;
				int mIndex = (int) ((mask >>> (b * 8)) & 0xFF);
				buddiesOut.and(groupedBuddies[groupIdx][mIndex]);
			}
		}
	}

	/**
	 * 9x9 fast path: given the two long-words representing a cell set, compute
	 * the common buddies into {@code buddiesOut}. Mirrors the second
	 * {@link Sudoku2#getBuddies(long, long, SudokuSetBase)} overload.
	 */
	public void getBuddies9x9(long mask1, long mask2, SudokuSetBase buddiesOut) {
		long outM1 = SudokuSetBase.MAX_MASK1;
		long outM2 = SudokuSetBase.MAX_MASK2;
		if (mask1 != 0) {
			for (int i = 0, j = 0; i < 8; i++, j += 8) {
				int mIndex = (int) ((mask1 >>> j) & 0xFF);
				outM1 &= groupedBuddiesM1[i][mIndex];
				outM2 &= groupedBuddiesM2[i][mIndex];
			}
		}
		if (mask2 != 0) {
			for (int i = 8, j = 0; i < 11; i++, j += 8) {
				int mIndex = (int) ((mask2 >>> j) & 0xFF);
				outM1 &= groupedBuddiesM1[i][mIndex];
				outM2 &= groupedBuddiesM2[i][mIndex];
			}
		}
		buddiesOut.set(outM1, outM2);
	}

	/** Helper: SudokuSet[] from an int[][] unit array. */
	private static SudokuSet[] templatesFor(BoardSpec spec, int[][] units) {
		SudokuSet[] out = new SudokuSet[units.length];
		for (int u = 0; u < units.length; u++) {
			SudokuSet s = new SudokuSet(spec);
			for (int cell : units[u]) s.add(cell);
			out[u] = s;
		}
		return out;
	}
}
