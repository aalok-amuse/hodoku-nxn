/*
 * Part of hodoku-nxn — a fork of HoDoKu generalised for multiple board sizes.
 *
 * BoardSpec replaces the static finals on Sudoku2.java (LENGTH, UNITS, ROWS,
 * COLS, BLOCKS, CONSTRAINTS, MASKS, MAX_MASK) with a per-instance descriptor.
 * Six pre-built specs are provided: S4, S5, S6, S7, S9, S16.
 *
 *   Size | Box layout                       | Bitset words
 *   -----+----------------------------------+-------------
 *   4x4  | 2x2                              | 1
 *   5x5  | Latin square (no boxes)          | 1
 *   6x6  | 3 wide x 2 tall                  | 1
 *   7x7  | Latin square (no boxes)          | 1
 *   9x9  | 3x3                              | 2
 *   16x16| 4x4                              | 4
 */
package sudoku;

import java.util.Arrays;

public final class BoardSpec {

    public final int n;
    public final int length;
    public final int boxWidth;
    public final int boxHeight;
    public final boolean hasBoxes;
    public final int boxesAcross;
    public final int boxesDown;

    /** rows[r] = indices of the n cells in row r */
    public final int[][] rows;
    /** cols[c] = indices of the n cells in column c */
    public final int[][] cols;
    /** boxes[b] = indices of the n cells in box b. Empty (length 0) if !hasBoxes. */
    public final int[][] boxes;
    /** All units in order: rows..., cols..., (boxes... if hasBoxes). Length = 2n or 3n. */
    public final int[][] allUnits;
    /** For each cell, the indices into allUnits of the units containing it. Length 2 or 3 per row. */
    public final int[][] constraints;

    /** Candidate bitmasks: masks[d] has bit (d-1) set, for d in 1..n. masks[0] = 0. */
    public final short[] masks;
    /** (1 &lt;&lt; n) - 1 */
    public final short maxMask;
    /** Number of 64-bit words needed for a cell-set bitmap covering all `length` cells. */
    public final int wordsPerBitset;

    /** Pre-built spec: 4x4 with 2x2 boxes. */
    public static final BoardSpec S4  = new BoardSpec(4,  2, 2);
    /** Pre-built spec: 5x5 Latin square (no boxes). */
    public static final BoardSpec S5  = new BoardSpec(5,  0, 0);
    /** Pre-built spec: 6x6 with 3-wide x 2-tall boxes. */
    public static final BoardSpec S6  = new BoardSpec(6,  3, 2);
    /** Pre-built spec: 7x7 Latin square (no boxes). */
    public static final BoardSpec S7  = new BoardSpec(7,  0, 0);
    /** Pre-built spec: 9x9 with 3x3 boxes (standard). */
    public static final BoardSpec S9  = new BoardSpec(9,  3, 3);
    /** Pre-built spec: 16x16 with 4x4 boxes. */
    public static final BoardSpec S16 = new BoardSpec(16, 4, 4);

    /** Look up a spec by side length. Throws if n is not one of the supported sizes. */
    public static BoardSpec of(int n) {
        switch (n) {
            case 4:  return S4;
            case 5:  return S5;
            case 6:  return S6;
            case 7:  return S7;
            case 9:  return S9;
            case 16: return S16;
            default: throw new IllegalArgumentException("unsupported size n=" + n);
        }
    }

    private BoardSpec(int n, int boxWidth, int boxHeight) {
        if (n < 2 || n > 16) {
            throw new IllegalArgumentException("n out of supported range [2..16]: " + n);
        }
        if (boxWidth < 0 || boxHeight < 0) {
            throw new IllegalArgumentException("box dims must be >= 0");
        }
        if (boxWidth > 0 && boxHeight > 0) {
            if (boxWidth * boxHeight != n) {
                throw new IllegalArgumentException(
                    "box " + boxWidth + "x" + boxHeight + " does not tile " + n + "x" + n);
            }
            if (n % boxWidth != 0 || n % boxHeight != 0) {
                throw new IllegalArgumentException(
                    "box " + boxWidth + "x" + boxHeight + " does not divide " + n);
            }
        } else if (boxWidth != 0 || boxHeight != 0) {
            throw new IllegalArgumentException("both box dims must be 0 or both > 0");
        }

        this.n = n;
        this.length = n * n;
        this.boxWidth = boxWidth;
        this.boxHeight = boxHeight;
        this.hasBoxes = boxWidth > 0;
        this.boxesAcross = hasBoxes ? n / boxWidth : 0;
        this.boxesDown   = hasBoxes ? n / boxHeight : 0;

        // Build rows: cell index = r * n + c
        this.rows = new int[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                rows[r][c] = r * n + c;
            }
        }

        // Build cols
        this.cols = new int[n][n];
        for (int c = 0; c < n; c++) {
            for (int r = 0; r < n; r++) {
                cols[c][r] = r * n + c;
            }
        }

        // Build boxes
        if (hasBoxes) {
            this.boxes = new int[n][n];
            for (int b = 0; b < n; b++) {
                int boxRow = b / boxesAcross;        // which row-of-boxes (0..boxesDown-1)
                int boxCol = b % boxesAcross;        // which col-of-boxes (0..boxesAcross-1)
                int rBase = boxRow * boxHeight;
                int cBase = boxCol * boxWidth;
                int idx = 0;
                for (int dr = 0; dr < boxHeight; dr++) {
                    for (int dc = 0; dc < boxWidth; dc++) {
                        boxes[b][idx++] = (rBase + dr) * n + (cBase + dc);
                    }
                }
            }
        } else {
            this.boxes = new int[0][];
        }

        // Build allUnits: rows..., cols..., boxes...
        int unitCount = hasBoxes ? 3 * n : 2 * n;
        this.allUnits = new int[unitCount][];
        for (int i = 0; i < n; i++) {
            allUnits[i]         = rows[i];
            allUnits[n + i]     = cols[i];
            if (hasBoxes) {
                allUnits[2 * n + i] = boxes[i];
            }
        }

        // Build constraints[cell] = unit indices containing this cell
        this.constraints = new int[length][hasBoxes ? 3 : 2];
        for (int cell = 0; cell < length; cell++) {
            int r = cell / n;
            int c = cell % n;
            constraints[cell][0] = r;            // row unit index
            constraints[cell][1] = n + c;        // col unit index
            if (hasBoxes) {
                int b = (r / boxHeight) * boxesAcross + (c / boxWidth);
                constraints[cell][2] = 2 * n + b;
            }
        }

        // Candidate masks
        this.masks = new short[n + 1];
        masks[0] = 0;
        for (int d = 1; d <= n; d++) {
            masks[d] = (short) (1 << (d - 1));
        }
        this.maxMask = (short) ((1 << n) - 1);

        // Bitset word count
        this.wordsPerBitset = (length + 63) >>> 6;
    }

    /** Row index (0..n-1) of a cell. */
    public int rowOf(int cell)   { return cell / n; }
    /** Column index (0..n-1) of a cell. */
    public int colOf(int cell)   { return cell % n; }
    /** Box index (0..n-1) of a cell, or -1 if this spec has no boxes. */
    public int boxOf(int cell) {
        if (!hasBoxes) return -1;
        return (rowOf(cell) / boxHeight) * boxesAcross + (colOf(cell) / boxWidth);
    }

    @Override
    public String toString() {
        return "BoardSpec(n=" + n
            + (hasBoxes ? ", box=" + boxWidth + "x" + boxHeight : ", Latin")
            + ", length=" + length
            + ", units=" + allUnits.length
            + ", words=" + wordsPerBitset + ")";
    }

    /** Print structural summary — useful for debugging. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(this).append('\n');
        sb.append("  rows[0] = ").append(Arrays.toString(rows[0])).append('\n');
        sb.append("  cols[0] = ").append(Arrays.toString(cols[0])).append('\n');
        if (hasBoxes) {
            sb.append("  boxes[0] = ").append(Arrays.toString(boxes[0])).append('\n');
        }
        sb.append("  constraints[0] = ").append(Arrays.toString(constraints[0])).append('\n');
        sb.append("  maxMask = 0x").append(Integer.toHexString(maxMask & 0xFFFF));
        return sb.toString();
    }
}
