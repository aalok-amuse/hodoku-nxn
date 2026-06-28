/*
 * Copyright (C) 2008-12  Bernhard Hobiger
 *
 * This file is part of HoDoKu.
 *
 * HoDoKu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HoDoKu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HoDoKu. If not, see <http://www.gnu.org/licenses/>.
 */

package sudoku;

/**
 * Hilfsklasse für die Fischsuche:
 *
 * Ein SudokuSet ist ein Integer-Array der Größe 81, das Werte zwischen 0 und
 * 81 aufnehmen kann. Die Werte innerhalb des Arrays werden sortiert eingefügt.
 *
 * Aus Performancegründen werden die Werte in einer Bitmask dupliziert.
 * Operationen wie merge() oder contains() können damit wesentlich schneller
 * ausgeführt werden. In der Bitmap wird der Wert "0" als "0x00000001"
 * abgebildet. Der größte darstellbare Wert pro int ist "0x80000000" und steht
 * für "31", 3 int ergeben daher die Werte 0 - 95. int mask1: 0 - 31 int mask2:
 * 32 - 63 int mask3: 64 - 95
 *
 * Mehrere Instanzen von SudokuSet können miteinander verglichen werden.
 * Speziell ist es möglich zu prüfen, ob Werte eines SudokuSet in einem
 * anderen enthalten sind. Außerdem können effizient Vereinigungen von
 * SudokuSets gebildet werden (entspricht mischen).
 *
 * @author hobiwan
 */
public class SudokuSet extends SudokuSetBase implements Cloneable {
	// für jede der 256 möglichen Kombinationen von Bits das entsprechende Array
	private static int[][] possibleValues = new int[256][8];
	// und zu jeder Zahl die Länge des Arrays
	public static int[] anzValues = new int[256];
	private static final long serialVersionUID = 1L;

	private int[] values = null;
	private int anz = 0;

	static {
		// possibleValues initialisieren
		for (int i = 0; i < 256; i++) {
			int index = 0;
			int mask = 1;
			for (int j = 0; j < 8; j++) {
				if ((i & mask) != 0) {
					possibleValues[i][index++] = j;
				}
				mask <<= 1;
			}
			anzValues[i] = index;
		}
	}

	/** Creates a new instance of SudokuSet */
	public SudokuSet() {
	}

	public SudokuSet(SudokuSetBase init) {
		super(init);
	}

	public SudokuSet(boolean full) {
		super(full);
	}

	/** Empty set sized for the given board spec. */
	public SudokuSet(BoardSpec spec) {
		super(spec);
	}

	@Override
	public SudokuSet clone() {
		SudokuSet newSet = null;
		newSet = (SudokuSet) super.clone();
		// dont clone the array (for performance reasons - might not be necessary)
		values = null;
		initialized = false;
//        if (values != null) {
//            newSet.values = Arrays.copyOf(values, values.length);
//        }
		return newSet;
	}

	public int get(int index) {
		if (!isInitialized()) {
			initialize();
		}
		return values[index];
	}

	public int size() {
		if (isEmpty()) {
			return 0;
		}
		if (!isInitialized()) {
			initialize();
		}
		return anz;
	}

	@Override
	public void clear() {
		super.clear();
		anz = 0;
	}

	public int[] getValues() {
		if (!initialized) {
			initialize();
		}
		return values;
	}

	/**
	 * pr�ft, ob alle Elemente in values im Set s1 vorkommen. Alle nicht
	 * enthaltenen Kandidaten werden in das SudokuSet fins geschrieben
	 * 
	 * @param s1
	 * @param fins
	 * @return
	 */
	public boolean isCovered(SudokuSet s1, SudokuSet fins) {
		long m1 = ~s1.words[0] & words[0];
		long m2 = (words.length > 1 && s1.words.length > 1)
				? (~s1.words[1] & words[1]) : 0L;
		boolean covered = true;
		if (m1 != 0) {
			covered = false;
			fins.words[0] = m1;
			fins.initialized = false;
		}
		if (m2 != 0) {
			covered = false;
			fins.words[1] = m2;
			fins.initialized = false;
		}
		return covered;
	}

	private void initialize() {
		// Buffer must fit the largest supported board (16x16 = 256 cells).
		int capacity = words.length * 64;
		if (values == null || values.length < capacity) {
			values = new int[capacity];
		}
		int index = 0;
		// Iterate every word, not just words[0..1]. For 9x9 (2 words) this is
		// unchanged; for 16x16 (4 words) it now covers all 256 cells.
		for (int w = 0; w < words.length; w++) {
			long word = words[w];
			if (word == 0) continue;
			int base = w * 64;
			for (int i = 0; i < 64; i += 8) {
				int mIndex = (int) ((word >>> i) & 0xFF);
				for (int j = 0; j < anzValues[mIndex]; j++) {
					values[index++] = possibleValues[mIndex][j] + base + i;
				}
			}
		}
		setInitialized(true);
		setAnz(index);
	}

	@Override
	public String toString() {
		if (!isInitialized()) {
			initialize();
		}
		if (anz == 0) {
			return "empty!";
		}
		StringBuilder tmp = new StringBuilder();
		tmp.append(Integer.toString(values[0]));
		for (int i = 1; i < anz; i++) {
			tmp.append(" ").append(Integer.toString(values[i]));
		}
		tmp.append(" ").append(pM(words[0])).append("/")
		    .append(pM(words.length > 1 ? words[1] : 0L));
		return tmp.toString();
	}

	/*
	public static void main(String[] args) {
		SudokuSet a = new SudokuSet();
		a.add(5);
		a.add(1);
		a.add(7);
		a.add(3);
		a.add(0);
		System.out.println("a: " + a);
		SudokuSet b = new SudokuSet();
		b.add(2);
		b.add(4);
		b.add(3);
		System.out.println("b: " + b);
		System.out.println(a.intersects(b));
		SudokuSet c = new SudokuSet();
		c.add(0);
		c.add(1);
		c.add(5);
		c.add(7);
		c.add(10);
		System.out.println("c: " + c);
		SudokuSet fins = new SudokuSet();
		// System.out.println(a.isCovered(b, c, SudokuSet.EMPTY_SET,
		// SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET,
		// SudokuSet.EMPTY_SET, fins));
//        System.out.println(a.isCovered(c, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, SudokuSet.EMPTY_SET, fins));
		System.out.println("fins: " + fins);
		a.remove(5);
		System.out.println("a: " + a);
		a.remove(0);
		System.out.println("a: " + a);
		a.remove(7);
		System.out.println("a: " + a);
		a.remove(3);
		System.out.println("a: " + a);
		a.remove(12);
		System.out.println("a: " + a);
		a.remove(1);
		System.out.println("a: " + a);
		a.remove(12);
		System.out.println("a: " + a);
		a.add(70);
		a.add(10);
		a.add(80);
		System.out.println("a: " + a);
		a.clear();
		a.add(0);
		System.out.println("a: " + a);
	}*/

	public void setValues(int[] values) {
		this.values = values;
	}

	public int getAnz() {
		return anz;
	}

	public void setAnz(int anz) {
		this.anz = anz;
	}
}
