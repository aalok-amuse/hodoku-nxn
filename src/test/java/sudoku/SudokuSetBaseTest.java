/*
 * SudokuSetBase tests — bitset ops across different word counts (1, 2, 4 longs).
 */
package sudoku;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SudokuSetBaseTest {

	static Stream<BoardSpec> allSpecs() {
		return Stream.of(BoardSpec.S4, BoardSpec.S5, BoardSpec.S6,
		                 BoardSpec.S7, BoardSpec.S9, BoardSpec.S16);
	}

	@Test
	void defaultConstructorIs9x9() {
		SudokuSetBase s = new SudokuSetBase();
		assertEquals(2, s.getWordCount(), "default ctor should be 9×9 (2 words)");
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	void specConstructorSizesBitsetCorrectly(BoardSpec spec) {
		SudokuSetBase s = new SudokuSetBase(spec);
		assertEquals(spec.wordsPerBitset, s.getWordCount());
		assertTrue(s.isEmpty(), "new set should be empty");
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	void addRemoveContainsRoundTrip(BoardSpec spec) {
		SudokuSetBase s = new SudokuSetBase(spec);
		int last = spec.length - 1;
		// Distinct representative cells. We collect into a set first so that on
		// small specs (where last/2 might equal Math.min(63, last)) we don't
		// double-test the same cell.
		java.util.Set<Integer> distinct = new java.util.LinkedHashSet<>();
		distinct.add(0);
		distinct.add(last / 2);
		distinct.add(last);
		if (last >= 63) distinct.add(63);
		if (last >= 64) distinct.add(64);
		for (int c : distinct) {
			assertFalse(s.contains(c), "fresh set should not contain " + c);
			s.add(c);
			assertTrue(s.contains(c), "after add: should contain " + c);
		}
		for (int c : distinct) {
			s.remove(c);
			assertFalse(s.contains(c), "after remove: should not contain " + c);
		}
		assertTrue(s.isEmpty());
	}

	@ParameterizedTest
	@MethodSource("allSpecs")
	void setAllThenNotIsEmpty(BoardSpec spec) {
		SudokuSetBase s = new SudokuSetBase(spec);
		s.setAll();
		// Every cell index 0..length-1 should be set.
		for (int c = 0; c < spec.length; c++) {
			assertTrue(s.contains(c), "setAll: cell " + c + " should be set");
		}
		s.not();
		assertTrue(s.isEmpty(), "not(setAll) should be empty");
	}

	@Test
	void s9LegacyMaskAccessorsStillWork() {
		SudokuSetBase s = new SudokuSetBase();
		s.add(0);
		s.add(63);
		s.add(64);
		s.add(80);
		assertEquals(1L | (1L << 63), s.getMask1());
		assertEquals(1L | (1L << 16), s.getMask2());
	}

	@Test
	void s16ContainsAll256Cells() {
		SudokuSetBase s = new SudokuSetBase(BoardSpec.S16);
		s.setAll();
		for (int c = 0; c < 256; c++) {
			assertTrue(s.contains(c), "S16 setAll missing cell " + c);
		}
	}

	@Test
	void cloneIndependentlyTracks() {
		SudokuSetBase a = new SudokuSetBase();
		a.add(0);
		SudokuSetBase b = a.clone();
		assertEquals(a, b);
		b.add(80);
		assertNotEquals(a, b);
		assertFalse(a.contains(80));
	}

	@Test
	void intersectsAndContainsBetweenSets() {
		SudokuSetBase a = new SudokuSetBase();
		SudokuSetBase b = new SudokuSetBase();
		a.add(5); a.add(50);
		b.add(50); b.add(75);
		assertTrue(a.intersects(b));
		b.remove(50);
		assertFalse(a.intersects(b));
		// a now {5, 50}, b {75}; a does not contain b
		assertFalse(a.contains(b));
		// b contains itself
		assertTrue(b.contains(b));
	}
}
