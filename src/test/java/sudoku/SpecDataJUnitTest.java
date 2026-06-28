/*
 * SpecData tests — cross-check S9 against Sudoku2 statics, verify per-spec
 * lookup tables for other sizes.
 */
package sudoku;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SpecDataJUnitTest {

	static Stream<BoardSpec> allSpecs() {
		return Stream.of(BoardSpec.S4, BoardSpec.S5, BoardSpec.S6,
		                 BoardSpec.S7, BoardSpec.S9, BoardSpec.S16);
	}

	@Test
	void s9MaskLookupsMatchSudoku2() {
		SpecData d = SpecData.S9;
		for (int m = 0; m < 512; m++) {
			assertEquals(Sudoku2.ANZ_VALUES[m], d.anzValues[m],     "anzValues[" + m + "]");
			assertEquals(Sudoku2.CAND_FROM_MASK[m], d.candFromMask[m], "candFromMask[" + m + "]");
			assertArrayEquals(Sudoku2.POSSIBLE_VALUES[m], d.possibleValues[m]);
		}
	}

	@Test
	void s9BuddiesMatchSudoku2() {
		SpecData d = SpecData.S9;
		for (int c = 0; c < 81; c++) {
			assertEquals(Sudoku2.buddies[c], d.buddies[c], "buddies[" + c + "]");
			assertEquals(Sudoku2.buddiesM1[c], d.buddiesM1[c]);
			assertEquals(Sudoku2.buddiesM2[c], d.buddiesM2[c]);
		}
	}

	@Test
	void s9TemplatesMatchSudoku2() {
		SpecData d = SpecData.S9;
		for (int u = 0; u < 9; u++) {
			assertEquals(Sudoku2.ROW_TEMPLATES[u],   d.rowTemplates[u]);
			assertEquals(Sudoku2.COL_TEMPLATES[u],   d.colTemplates[u]);
			assertEquals(Sudoku2.BLOCK_TEMPLATES[u], d.blockTemplates[u]);
		}
		for (int u = 0; u < 27; u++) {
			assertEquals(Sudoku2.ALL_CONSTRAINTS_TEMPLATES[u], d.allConstraintsTemplates[u]);
			assertEquals(Sudoku2.ALL_CONSTRAINTS_TEMPLATES_M1[u], d.allConstraintsTemplatesM1[u]);
			assertEquals(Sudoku2.ALL_CONSTRAINTS_TEMPLATES_M2[u], d.allConstraintsTemplatesM2[u]);
			assertEquals(Sudoku2.CONSTRAINT_TYPE_FROM_CONSTRAINT[u], d.constraintType[u]);
			assertEquals(Sudoku2.CONSTRAINT_NUMBER_FROM_CONSTRAINT[u], d.constraintNumber[u]);
		}
	}

	@Test
	void s9GroupedBuddiesMatchSudoku2() {
		SpecData d = SpecData.S9;
		// Spot check — exhaustive would be 11 × 256 = 2,816 entries
		java.util.Random rng = new java.util.Random(0);
		for (int trial = 0; trial < 200; trial++) {
			int g = rng.nextInt(11), m = rng.nextInt(256);
			assertEquals(Sudoku2.groupedBuddiesM1[g][m], d.groupedBuddiesM1[g][m]);
			assertEquals(Sudoku2.groupedBuddiesM2[g][m], d.groupedBuddiesM2[g][m]);
		}
	}

	@Test
	void s9TemplatesReferencesSudoku2Templates() {
		assertSame(Sudoku2.templates, SpecData.S9.templates);
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	void maskTableSizesMatchSpec(BoardSpec s) {
		SpecData d = SpecData.for_(s);
		int expected = 1 << s.n;
		assertEquals(expected, d.anzValues.length);
		assertEquals(expected, d.candFromMask.length);
		assertEquals(expected, d.possibleValues.length);
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	void buddiesCoverEverySpec(BoardSpec s) {
		SpecData d = SpecData.for_(s);
		assertEquals(s.length, d.buddies.length);
		for (int c = 0; c < s.length; c++) {
			assertNotNull(d.buddies[c], "buddies[" + c + "] must exist");
			assertFalse(d.buddies[c].contains(c), "cell must not be its own buddy");
		}
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	void anzValuesIsBitCount(BoardSpec s) {
		SpecData d = SpecData.for_(s);
		for (int m = 0; m < (1 << s.n); m++) {
			assertEquals(Integer.bitCount(m), d.anzValues[m]);
		}
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	void candFromMaskIsLowestBit(BoardSpec s) {
		SpecData d = SpecData.for_(s);
		assertEquals(0, d.candFromMask[0]);
		for (int m = 1; m < (1 << s.n); m++) {
			int expected = Integer.numberOfTrailingZeros(m) + 1;
			assertEquals(expected, d.candFromMask[m]);
		}
	}

	@Test
	void specDataIsCached() {
		SpecData a = SpecData.for_(BoardSpec.S4);
		SpecData b = SpecData.for_(BoardSpec.S4);
		assertSame(a, b, "SpecData.for_(spec) should return cached instance");
	}

	@Test
	void s9SpecDataIsTheStaticInstance() {
		assertSame(SpecData.S9, SpecData.for_(BoardSpec.S9));
	}
}
