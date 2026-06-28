/*
 * BoardSpec tests — dimensions, unit decomposition, masks, accessor consistency.
 */
package sudoku;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BoardSpecJUnitTest {

	static Stream<BoardSpec> allSpecs() {
		return Stream.of(BoardSpec.S4, BoardSpec.S5, BoardSpec.S6,
		                 BoardSpec.S7, BoardSpec.S9, BoardSpec.S16);
	}

	@Test
	@DisplayName("BoardSpec.of(n) returns the right spec for supported sizes")
	void ofReturnsCorrectSpec() {
		assertSame(BoardSpec.S4,  BoardSpec.of(4));
		assertSame(BoardSpec.S5,  BoardSpec.of(5));
		assertSame(BoardSpec.S6,  BoardSpec.of(6));
		assertSame(BoardSpec.S7,  BoardSpec.of(7));
		assertSame(BoardSpec.S9,  BoardSpec.of(9));
		assertSame(BoardSpec.S16, BoardSpec.of(16));
	}

	@Test
	@DisplayName("BoardSpec.of rejects unsupported sizes")
	void ofRejectsUnsupported() {
		assertThrows(IllegalArgumentException.class, () -> BoardSpec.of(8));
		assertThrows(IllegalArgumentException.class, () -> BoardSpec.of(10));
		assertThrows(IllegalArgumentException.class, () -> BoardSpec.of(3));
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	@DisplayName("length == n*n; row/col/box counts; mask widths")
	void dimensionalSelfConsistency(BoardSpec s) {
		assertEquals(s.n * s.n, s.length);
		assertEquals(s.n, s.rows.length);
		assertEquals(s.n, s.cols.length);
		if (s.hasBoxes) {
			assertEquals(s.n, s.boxes.length);
			assertEquals(3 * s.n, s.allUnits.length);
		} else {
			assertEquals(0, s.boxes.length);
			assertEquals(2 * s.n, s.allUnits.length);
		}
		assertEquals(s.length, s.constraints.length);
		assertEquals(s.n + 1, s.masks.length);
		assertEquals((1 << s.n) - 1, s.maxMask & 0xFFFF);
		assertEquals((s.length + 63) >>> 6, s.wordsPerBitset);
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	@DisplayName("rows/cols partition all cells exactly once")
	void rowsColsPartitionCells(BoardSpec s) {
		Set<Integer> rowCells = new HashSet<>();
		for (int r = 0; r < s.n; r++) {
			assertEquals(s.n, s.rows[r].length);
			for (int cell : s.rows[r]) {
				assertTrue(cell >= 0 && cell < s.length);
				assertTrue(rowCells.add(cell), "duplicate row cell " + cell);
				assertEquals(r, s.rowOf(cell));
			}
		}
		assertEquals(s.length, rowCells.size());

		Set<Integer> colCells = new HashSet<>();
		for (int c = 0; c < s.n; c++) {
			assertEquals(s.n, s.cols[c].length);
			for (int cell : s.cols[c]) {
				assertTrue(colCells.add(cell), "duplicate col cell " + cell);
				assertEquals(c, s.colOf(cell));
			}
		}
		assertEquals(s.length, colCells.size());
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	@DisplayName("boxes partition all cells (only for hasBoxes specs)")
	void boxesPartitionCellsIfPresent(BoardSpec s) {
		if (!s.hasBoxes) {
			assertEquals(-1, s.boxOf(0));
			return;
		}
		Set<Integer> boxCells = new HashSet<>();
		for (int b = 0; b < s.n; b++) {
			assertEquals(s.n, s.boxes[b].length);
			int minR = s.n, maxR = -1, minC = s.n, maxC = -1;
			for (int cell : s.boxes[b]) {
				assertTrue(boxCells.add(cell));
				assertEquals(b, s.boxOf(cell));
				minR = Math.min(minR, s.rowOf(cell));
				maxR = Math.max(maxR, s.rowOf(cell));
				minC = Math.min(minC, s.colOf(cell));
				maxC = Math.max(maxC, s.colOf(cell));
			}
			assertEquals(s.boxHeight, maxR - minR + 1, "box " + b + " height");
			assertEquals(s.boxWidth,  maxC - minC + 1, "box " + b + " width");
		}
		assertEquals(s.length, boxCells.size());
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	@DisplayName("Every cell appears in its declared constraint units")
	void constraintsAreConsistent(BoardSpec s) {
		int expected = s.hasBoxes ? 3 : 2;
		for (int cell = 0; cell < s.length; cell++) {
			assertEquals(expected, s.constraints[cell].length);
			for (int unitIdx : s.constraints[cell]) {
				assertTrue(unitIdx >= 0 && unitIdx < s.allUnits.length);
				int[] unit = s.allUnits[unitIdx];
				boolean found = false;
				for (int x : unit) if (x == cell) { found = true; break; }
				assertTrue(found, "cell " + cell + " missing from unit " + unitIdx);
			}
		}
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	@DisplayName("Candidate masks: one bit per digit, union covers maxMask")
	void candidateMasksAreValid(BoardSpec s) {
		assertEquals(0, s.masks[0]);
		int union = 0;
		for (int d = 1; d <= s.n; d++) {
			int m = s.masks[d] & 0xFFFF;
			assertEquals(1, Integer.bitCount(m), "mask[" + d + "] should have 1 bit");
			union |= m;
		}
		assertEquals(s.maxMask & 0xFFFF, union);
	}

	@Test
	@DisplayName("S9 matches Sudoku2's static 9×9 layout exactly")
	void s9MatchesSudoku2() {
		BoardSpec s = BoardSpec.S9;
		assertEquals(Sudoku2.LENGTH, s.length);
		assertEquals(Sudoku2.UNITS, s.n);
		for (int r = 0; r < 9; r++) {
			assertArrayEquals(Sudoku2.ROWS[r],   s.rows[r]);
			assertArrayEquals(Sudoku2.COLS[r],   s.cols[r]);
			assertArrayEquals(Sudoku2.BLOCKS[r], s.boxes[r]);
		}
		for (int d = 1; d <= 9; d++) {
			assertEquals(Sudoku2.MASKS[d], s.masks[d]);
		}
		assertEquals(Sudoku2.MAX_MASK, s.maxMask);
		for (int cell = 0; cell < 81; cell++) {
			assertArrayEquals(Sudoku2.CONSTRAINTS[cell], s.constraints[cell]);
		}
	}
}
