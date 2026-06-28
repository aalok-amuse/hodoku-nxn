/*
 * Verification of BoardSpec for all 6 sizes (4, 5, 6, 7, 9, 16).
 * Run:   java -cp target/Hodoku.jar sudoku.BoardSpecTest
 *
 * Checks each spec on:
 *   - dimension self-consistency (length, unit counts, words)
 *   - rows / cols / boxes cover every cell exactly once per axis
 *   - constraints[cell] correctly indexes the unit arrays
 *   - candidate masks: maxMask, bitcount, no collisions
 *   - cross-check vs hardcoded Sudoku2 layout for the 9x9 case
 *   - boxOf() / rowOf() / colOf() agree with the unit arrays
 */
package sudoku;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BoardSpecTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        BoardSpec[] specs = { BoardSpec.S4, BoardSpec.S5, BoardSpec.S6,
                              BoardSpec.S7, BoardSpec.S9, BoardSpec.S16 };

        for (BoardSpec s : specs) {
            System.out.println("---- " + s + " ----");
            checkDimensions(s);
            checkRowsCols(s);
            checkBoxes(s);
            checkConstraints(s);
            checkMasks(s);
            checkAccessors(s);
            System.out.println();
        }
        checkOfLookup();
        checkInvalidSpecs();
        check9x9MatchesSudoku2();
        checkSudoku2CarriesSpec();
        checkSudokuSetBaseSizing();
        checkSudokuSetBaseOps();
        checkSpecDataS9MatchesSudoku2();
        checkSpecDataOtherSizes();

        System.out.println("================");
        System.out.printf("PASSED: %d  FAILED: %d%n", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------------
    // Per-spec checks
    // ---------------------------------------------------------------------

    private static void checkDimensions(BoardSpec s) {
        assertTrue("length == n*n",         s.length == s.n * s.n);
        assertTrue("rows.length == n",      s.rows.length == s.n);
        assertTrue("cols.length == n",      s.cols.length == s.n);
        if (s.hasBoxes) {
            assertTrue("boxes.length == n", s.boxes.length == s.n);
            assertTrue("3n units",           s.allUnits.length == 3 * s.n);
        } else {
            assertTrue("no boxes => 0",     s.boxes.length == 0);
            assertTrue("2n units",           s.allUnits.length == 2 * s.n);
        }
        assertTrue("constraints rows == length", s.constraints.length == s.length);
        int expectedWords = (s.length + 63) >>> 6;
        assertTrue("wordsPerBitset",         s.wordsPerBitset == expectedWords);
    }

    private static void checkRowsCols(BoardSpec s) {
        Set<Integer> seenInRows = new HashSet<>();
        for (int r = 0; r < s.n; r++) {
            assertTrue("row " + r + " has n cells", s.rows[r].length == s.n);
            for (int cell : s.rows[r]) {
                assertTrue("row cell in 0..length", cell >= 0 && cell < s.length);
                assertTrue("row cell unique",        seenInRows.add(cell));
                assertTrue("rowOf(cell) == r",       s.rowOf(cell) == r);
            }
        }
        assertTrue("rows cover all cells", seenInRows.size() == s.length);

        Set<Integer> seenInCols = new HashSet<>();
        for (int c = 0; c < s.n; c++) {
            assertTrue("col " + c + " has n cells", s.cols[c].length == s.n);
            for (int cell : s.cols[c]) {
                assertTrue("col cell unique",         seenInCols.add(cell));
                assertTrue("colOf(cell) == c",        s.colOf(cell) == c);
            }
        }
        assertTrue("cols cover all cells", seenInCols.size() == s.length);
    }

    private static void checkBoxes(BoardSpec s) {
        if (!s.hasBoxes) {
            assertTrue("boxOf returns -1 when no boxes", s.boxOf(0) == -1);
            return;
        }
        Set<Integer> seenInBoxes = new HashSet<>();
        for (int b = 0; b < s.n; b++) {
            assertTrue("box " + b + " has n cells", s.boxes[b].length == s.n);
            for (int cell : s.boxes[b]) {
                assertTrue("box cell unique",        seenInBoxes.add(cell));
                assertTrue("boxOf(cell) == b",       s.boxOf(cell) == b);
            }
            // Cells in a box should form a boxWidth x boxHeight rectangle.
            int minR = s.n, maxR = -1, minC = s.n, maxC = -1;
            for (int cell : s.boxes[b]) {
                minR = Math.min(minR, s.rowOf(cell));
                maxR = Math.max(maxR, s.rowOf(cell));
                minC = Math.min(minC, s.colOf(cell));
                maxC = Math.max(maxC, s.colOf(cell));
            }
            assertTrue("box height correct", maxR - minR + 1 == s.boxHeight);
            assertTrue("box width correct",  maxC - minC + 1 == s.boxWidth);
        }
        assertTrue("boxes cover all cells", seenInBoxes.size() == s.length);
    }

    private static void checkConstraints(BoardSpec s) {
        int expectedPerCell = s.hasBoxes ? 3 : 2;
        for (int cell = 0; cell < s.length; cell++) {
            assertTrue("constraints[cell].length", s.constraints[cell].length == expectedPerCell);
            for (int unitIdx : s.constraints[cell]) {
                assertTrue("unitIdx in range",       unitIdx >= 0 && unitIdx < s.allUnits.length);
                int[] unit = s.allUnits[unitIdx];
                boolean found = false;
                for (int x : unit) { if (x == cell) { found = true; break; } }
                assertTrue("cell in its own unit", found);
            }
        }
    }

    private static void checkMasks(BoardSpec s) {
        assertTrue("masks.length == n+1", s.masks.length == s.n + 1);
        assertTrue("masks[0] == 0",        s.masks[0] == 0);
        int union = 0;
        for (int d = 1; d <= s.n; d++) {
            int m = s.masks[d] & 0xFFFF;
            assertTrue("mask has exactly 1 bit", Integer.bitCount(m) == 1);
            union |= m;
        }
        int maxMaskU = s.maxMask & 0xFFFF;
        assertTrue("maxMask == (1<<n)-1", maxMaskU == ((1 << s.n) - 1));
        assertTrue("union of masks == maxMask", union == maxMaskU);
    }

    private static void checkAccessors(BoardSpec s) {
        for (int cell = 0; cell < s.length; cell++) {
            int r = s.rowOf(cell);
            int c = s.colOf(cell);
            assertTrue("rowOf in range", r >= 0 && r < s.n);
            assertTrue("colOf in range", c >= 0 && c < s.n);
            assertTrue("cell reconstructable", r * s.n + c == cell);
        }
    }

    // ---------------------------------------------------------------------
    // Global checks
    // ---------------------------------------------------------------------

    private static void checkOfLookup() {
        System.out.println("---- BoardSpec.of(n) lookup ----");
        for (int n : new int[]{4,5,6,7,9,16}) {
            assertTrue("of(" + n + ").n == " + n, BoardSpec.of(n).n == n);
        }
        try {
            BoardSpec.of(8);
            failed++; System.out.println("  FAIL: of(8) should throw");
        } catch (IllegalArgumentException e) {
            passed++;
            System.out.println("  ok  of(8) throws as expected");
        }
        System.out.println();
    }

    private static void checkInvalidSpecs() {
        System.out.println("---- invalid specs reject ----");
        // 9x9 with 2x2 box doesn't tile
        try {
            java.lang.reflect.Constructor<BoardSpec> ctor =
                BoardSpec.class.getDeclaredConstructor(int.class, int.class, int.class);
            ctor.setAccessible(true);
            ctor.newInstance(9, 2, 2);
            failed++; System.out.println("  FAIL: 9x9 with 2x2 box should throw");
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getTargetException() instanceof IllegalArgumentException) {
                passed++; System.out.println("  ok  9x9 with 2x2 box throws");
            } else { failed++; System.out.println("  FAIL: wrong exception: " + e.getTargetException()); }
        } catch (Exception e) {
            failed++; System.out.println("  FAIL: " + e);
        }
        System.out.println();
    }

    private static void check9x9MatchesSudoku2() {
        System.out.println("---- 9x9 BoardSpec vs Sudoku2 static finals ----");
        BoardSpec s = BoardSpec.S9;
        assertTrue("LENGTH matches",   s.length == Sudoku2.LENGTH);
        assertTrue("UNITS matches",    s.n == Sudoku2.UNITS);
        for (int r = 0; r < 9; r++) {
            assertTrue("rows[" + r + "] matches", Arrays.equals(s.rows[r], Sudoku2.ROWS[r]));
            assertTrue("cols[" + r + "] matches", Arrays.equals(s.cols[r], Sudoku2.COLS[r]));
            assertTrue("boxes[" + r + "] matches", Arrays.equals(s.boxes[r], Sudoku2.BLOCKS[r]));
        }
        // Sudoku2 mask order matches ours (bit i for digit i+1).
        for (int d = 1; d <= 9; d++) {
            assertTrue("masks[" + d + "] matches", s.masks[d] == Sudoku2.MASKS[d]);
        }
        assertTrue("maxMask matches", s.maxMask == Sudoku2.MAX_MASK);
        // Constraints: Sudoku2 stores them as [row, col, box].
        for (int cell = 0; cell < 81; cell++) {
            assertTrue("constraints[" + cell + "] matches",
                       Arrays.equals(s.constraints[cell], Sudoku2.CONSTRAINTS[cell]));
        }
        System.out.println();
    }

    private static void checkSudoku2CarriesSpec() {
        System.out.println("---- Sudoku2 carries its BoardSpec ----");
        // No-arg constructor: default S9
        Sudoku2 def = new Sudoku2();
        assertTrue("default ctor uses S9", def.getSpec() == BoardSpec.S9);
        assertTrue("S9 cells length",      defCellsLen(def) == 81);

        // Explicit S9
        Sudoku2 s9 = new Sudoku2(BoardSpec.S9);
        assertTrue("explicit S9 spec",     s9.getSpec() == BoardSpec.S9);
        assertTrue("S9 cells length",      defCellsLen(s9) == 81);

        // Non-S9 instances allocate the right array sizes
        for (BoardSpec s : new BoardSpec[]{ BoardSpec.S4, BoardSpec.S5, BoardSpec.S6,
                                            BoardSpec.S7, BoardSpec.S16 }) {
            Sudoku2 inst = new Sudoku2(s);
            assertTrue("getSpec()==" + s.n, inst.getSpec() == s);
            int len = defCellsLen(inst);
            assertTrue("cells length == " + s.length, len == s.length);
        }

        // clone() preserves spec
        Sudoku2 s4 = new Sudoku2(BoardSpec.S4);
        Sudoku2 c4 = s4.clone();
        assertTrue("clone preserves spec", c4.getSpec() == BoardSpec.S4);
        assertTrue("clone has own cells",  defCellsLen(c4) == 16);
        System.out.println();
    }

    private static void checkSudokuSetBaseSizing() {
        System.out.println("---- SudokuSetBase sizing per spec ----");
        // Default ctor preserves 9x9 (2 words, 81 valid bits).
        SudokuSetBase def = new SudokuSetBase();
        assertTrue("default ctor: 2 words",   def.getWordCount() == 2);

        // Size from BoardSpec.
        int[][] expected = {{4,1},{5,1},{6,1},{7,1},{9,2},{16,4}};
        for (int[] e : expected) {
            BoardSpec spec = BoardSpec.of(e[0]);
            SudokuSetBase s = new SudokuSetBase(spec);
            assertTrue("n=" + e[0] + " => " + e[1] + " words",
                       s.getWordCount() == e[1]);
        }
        System.out.println();
    }

    private static void checkSudokuSetBaseOps() {
        System.out.println("---- SudokuSetBase 9x9 ops (regression-style) ----");
        SudokuSetBase a = new SudokuSetBase();
        SudokuSetBase b = new SudokuSetBase();
        assertTrue("empty initially",          a.isEmpty());
        a.add(0);  a.add(63); a.add(64); a.add(80);
        assertTrue("contains low cells",       a.contains(0) && a.contains(63));
        assertTrue("contains high cells",      a.contains(64) && a.contains(80));
        assertTrue("does NOT contain unused",  !a.contains(50));
        assertTrue("mask1 has bits 0 + 63",    a.getMask1() == (1L | (1L << 63)));
        assertTrue("mask2 has bits 0 + 16",    a.getMask2() == (1L | (1L << 16)));

        b.add(0); b.add(64);
        assertTrue("a intersects b",           a.intersects(b));
        assertTrue("a contains b",             a.contains(b));
        SudokuSetBase clone = a.clone();
        assertTrue("clone equals",             clone.equals(a));
        assertTrue("clone independent",        clone != a && clone.getWords() != a.getWords());

        SudokuSetBase full = new SudokuSetBase(); full.setAll();
        assertTrue("setAll low word all 1",    full.getMask1() == SudokuSetBase.MAX_MASK1);
        assertTrue("setAll top word 0x1FFFF",  full.getMask2() == SudokuSetBase.MAX_MASK2);
        full.not();
        assertTrue("not(setAll) is empty",     full.isEmpty());

        // 16x16: top word covers full 64 bits — different topMask.
        SudokuSetBase big = new SudokuSetBase(BoardSpec.S16);
        big.setAll();
        for (int i = 0; i < 256; i++) assertTrue("S16 has cell " + i, big.contains(i));
        big.not();
        assertTrue("S16 not(setAll) is empty", big.isEmpty());

        // 4x4: top word has 16 bits.
        SudokuSetBase small = new SudokuSetBase(BoardSpec.S4);
        small.setAll();
        assertTrue("S4 setAll has 16 bits",    small.getMask1() == 0xFFFFL);
        for (int i = 0; i < 16; i++) assertTrue("S4 has cell " + i, small.contains(i));
        System.out.println();
    }

    private static void checkSpecDataS9MatchesSudoku2() {
        System.out.println("---- SpecData.S9 vs Sudoku2 static tables ----");
        SpecData d = SpecData.S9;
        // Mask lookups
        for (int m = 0; m < 0x200; m++) {
            assertTrue("anzValues[" + m + "]",   d.anzValues[m] == Sudoku2.ANZ_VALUES[m]);
            assertTrue("candFromMask[" + m + "]", d.candFromMask[m] == Sudoku2.CAND_FROM_MASK[m]);
            assertTrue("possibleValues[" + m + "]",
                       java.util.Arrays.equals(d.possibleValues[m], Sudoku2.POSSIBLE_VALUES[m]));
        }
        // Per-cell buddies
        for (int c = 0; c < 81; c++) {
            assertTrue("buddies[" + c + "] equals", d.buddies[c].equals(Sudoku2.buddies[c]));
            assertTrue("buddiesM1[" + c + "]",     d.buddiesM1[c] == Sudoku2.buddiesM1[c]);
            assertTrue("buddiesM2[" + c + "]",     d.buddiesM2[c] == Sudoku2.buddiesM2[c]);
        }
        // Unit templates
        for (int u = 0; u < 9; u++) {
            assertTrue("rowTemplates[" + u + "]",   d.rowTemplates[u].equals(Sudoku2.ROW_TEMPLATES[u]));
            assertTrue("colTemplates[" + u + "]",   d.colTemplates[u].equals(Sudoku2.COL_TEMPLATES[u]));
            assertTrue("blockTemplates[" + u + "]", d.blockTemplates[u].equals(Sudoku2.BLOCK_TEMPLATES[u]));
        }
        for (int u = 0; u < 27; u++) {
            assertTrue("allConstraintsTemplates[" + u + "]",
                       d.allConstraintsTemplates[u].equals(Sudoku2.ALL_CONSTRAINTS_TEMPLATES[u]));
            assertTrue("allConstraintsTemplatesM1[" + u + "]",
                       d.allConstraintsTemplatesM1[u] == Sudoku2.ALL_CONSTRAINTS_TEMPLATES_M1[u]);
            assertTrue("allConstraintsTemplatesM2[" + u + "]",
                       d.allConstraintsTemplatesM2[u] == Sudoku2.ALL_CONSTRAINTS_TEMPLATES_M2[u]);
        }
        for (int u = 0; u < 18; u++) {
            assertTrue("rowBlockTemplates[" + u + "]", d.rowBlockTemplates[u].equals(Sudoku2.ROW_BLOCK_TEMPLATES[u]));
            assertTrue("colBlockTemplates[" + u + "]", d.colBlockTemplates[u].equals(Sudoku2.COL_BLOCK_TEMPLATES[u]));
        }
        // Constraint type/number
        for (int u = 0; u < 27; u++) {
            assertTrue("constraintType[" + u + "]",
                       d.constraintType[u] == Sudoku2.CONSTRAINT_TYPE_FROM_CONSTRAINT[u]);
            assertTrue("constraintNumber[" + u + "]",
                       d.constraintNumber[u] == Sudoku2.CONSTRAINT_NUMBER_FROM_CONSTRAINT[u]);
        }
        // Grouped buddies — sample 3 random groups
        java.util.Random r = new java.util.Random(0);
        for (int trial = 0; trial < 30; trial++) {
            int g = r.nextInt(11), m = r.nextInt(256);
            assertTrue("groupedBuddiesM1[" + g + "][" + m + "]",
                       d.groupedBuddiesM1[g][m] == Sudoku2.groupedBuddiesM1[g][m]);
            assertTrue("groupedBuddiesM2[" + g + "][" + m + "]",
                       d.groupedBuddiesM2[g][m] == Sudoku2.groupedBuddiesM2[g][m]);
        }
        // Templates: should reference the same array
        assertTrue("templates ref",  d.templates == Sudoku2.templates);
        System.out.println();
    }

    private static void checkSpecDataOtherSizes() {
        System.out.println("---- SpecData for other specs ----");
        for (BoardSpec spec : new BoardSpec[]{ BoardSpec.S4, BoardSpec.S5, BoardSpec.S6,
                                                BoardSpec.S7, BoardSpec.S16 }) {
            SpecData d = SpecData.for_(spec);
            int n = spec.n;
            int maskCount = 1 << n;
            // Sanity: mask tables sized correctly
            assertTrue("n=" + n + " anzValues size",   d.anzValues.length == maskCount);
            assertTrue("n=" + n + " candFromMask size", d.candFromMask.length == maskCount);
            assertTrue("n=" + n + " possibleValues size", d.possibleValues.length == maskCount);
            assertTrue("n=" + n + " buddies size",     d.buddies.length == spec.length);
            assertTrue("n=" + n + " all templates",    d.allConstraintsTemplates.length == spec.allUnits.length);
            // Spot-check some lookups
            assertTrue("n=" + n + " anzValues[0]",      d.anzValues[0] == 0);
            assertTrue("n=" + n + " anzValues[all-on]", d.anzValues[maskCount - 1] == n);
            assertTrue("n=" + n + " candFromMask[1]",   d.candFromMask[1] == 1);
            assertTrue("n=" + n + " possibleValues[1]", d.possibleValues[1][0] == 1);
            // Buddies of cell 0 should match the unit decomposition
            int peerCount = 0;
            for (int unitIdx : spec.constraints[0]) {
                for (int peer : spec.allUnits[unitIdx]) {
                    if (peer != 0) peerCount++;
                }
            }
            // Deduplicate by walking through unique cells in the SudokuSet
            int distinctPeers = 0;
            for (int c = 0; c < spec.length; c++) {
                if (d.buddies[0].contains(c)) distinctPeers++;
            }
            // peerCount counts duplicates across units; distinctPeers is the union.
            assertTrue("n=" + n + " peers of cell 0 = expected count for " + spec,
                       distinctPeers == expectedPeers(spec));
            // templates only exists for S9
            assertTrue("n=" + n + " no templates", d.templates == null);
        }
        System.out.println();
    }

    /** Theoretical peer count per cell for each spec (row-1 + col-1 + (box -1 if any) - duplicates). */
    private static int expectedPeers(BoardSpec s) {
        int n = s.n;
        if (!s.hasBoxes) return 2 * (n - 1);                 // row + col peers, cell 0 is intersection
        // For boxed specs: row peers + col peers + box peers, minus box overlap with row+col.
        int row = n - 1, col = n - 1;
        int box = s.boxWidth * s.boxHeight - 1;
        int overlap = (s.boxWidth - 1) + (s.boxHeight - 1);  // box's row and col overlap with row+col
        return row + col + box - overlap;
    }

    /** Reflective length of Sudoku2's private cells[] field. */
    private static int defCellsLen(Sudoku2 s) {
        try {
            java.lang.reflect.Field f = Sudoku2.class.getDeclaredField("cells");
            f.setAccessible(true);
            return ((short[]) f.get(s)).length;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static void assertTrue(String label, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  ok  " + label);
        } else {
            failed++;
            System.out.println("  FAIL " + label);
        }
    }
}
