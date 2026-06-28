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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A bitset over cell indices. Each instance stores a {@code long[] words} array
 * sized for its board: cell index {@code i} lives in bit {@code i & 63} of
 * {@code words[i >>> 6]}.
 *
 * <p>Defaults to 9x9 (2 long words covering 81 cells) to preserve the original
 * Hodoku behaviour. Other sizes can be selected via
 * {@link #SudokuSetBase(int, long)} or {@link #SudokuSetBase(BoardSpec)}.</p>
 *
 * <p>Backward-compatible getters {@link #getMask1()} / {@link #getMask2()} still
 * work on the underlying word array, so existing solver code keeps compiling.</p>
 *
 * @author hobiwan (original 9x9 implementation), generalised in hodoku-nxn
 */
public class SudokuSetBase implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Serialised form is the legacy 9x9 layout {@code long mask1; long mask2;
	 * boolean initialized}. {@link #readObject} translates that into the new
	 * {@code words[]} representation so existing {@code templates.dat} keeps
	 * loading; {@link #writeObject} writes the legacy form back out.
	 */
	private static final ObjectStreamField[] serialPersistentFields = {
		new ObjectStreamField("mask1", long.class),
		new ObjectStreamField("mask2", long.class),
		new ObjectStreamField("initialized", boolean.class),
	};

	public static final SudokuSet EMPTY_SET = new SudokuSet();

	/** Precomputed bit masks for indices 0..63. {@code MASKS[i] == 1L << i}. */
	public static final long[] MASKS = { 0x0000000000000001L, 0x0000000000000002L, 0x0000000000000004L,
			0x0000000000000008L, 0x0000000000000010L, 0x0000000000000020L, 0x0000000000000040L, 0x0000000000000080L,
			0x0000000000000100L, 0x0000000000000200L, 0x0000000000000400L, 0x0000000000000800L, 0x0000000000001000L,
			0x0000000000002000L, 0x0000000000004000L, 0x0000000000008000L, 0x0000000000010000L, 0x0000000000020000L,
			0x0000000000040000L, 0x0000000000080000L, 0x0000000000100000L, 0x0000000000200000L, 0x0000000000400000L,
			0x0000000000800000L, 0x0000000001000000L, 0x0000000002000000L, 0x0000000004000000L, 0x0000000008000000L,
			0x0000000010000000L, 0x0000000020000000L, 0x0000000040000000L, 0x0000000080000000L, 0x0000000100000000L,
			0x0000000200000000L, 0x0000000400000000L, 0x0000000800000000L, 0x0000001000000000L, 0x0000002000000000L,
			0x0000004000000000L, 0x0000008000000000L, 0x0000010000000000L, 0x0000020000000000L, 0x0000040000000000L,
			0x0000080000000000L, 0x0000100000000000L, 0x0000200000000000L, 0x0000400000000000L, 0x0000800000000000L,
			0x0001000000000000L, 0x0002000000000000L, 0x0004000000000000L, 0x0008000000000000L, 0x0010000000000000L,
			0x0020000000000000L, 0x0040000000000000L, 0x0080000000000000L, 0x0100000000000000L, 0x0200000000000000L,
			0x0400000000000000L, 0x0800000000000000L, 0x1000000000000000L, 0x2000000000000000L, 0x4000000000000000L,
			0x8000000000000000L };

	/** Mask for the low word when all 64 bits are valid (used by 9x9 and 16x16). */
	public static final long MAX_MASK1 = 0xFFFFFFFFFFFFFFFFL;
	/** Mask for the top word in the legacy 9x9 layout (17 bits: cells 64..80). */
	public static final long MAX_MASK2 = 0x1FFFFL;

	/** Cell-membership bits. Length matches the board's word count. */
	protected long[] words;
	/** Mask of valid bits in {@code words[words.length - 1]}. */
	protected long topMask;

	/** {@code true} if the {@link SudokuSet} cache of int-indices is in sync. */
	protected boolean initialized = true;

	/**
	 * Default constructor: empty 9x9 set (two long words, 81 valid cells).
	 * Preserves the original Hodoku behaviour for backward compatibility.
	 */
	public SudokuSetBase() {
		this(2, MAX_MASK2);
	}

	/**
	 * Copy constructor.
	 *
	 * @param init source set; this instance is sized to match
	 */
	public SudokuSetBase(SudokuSetBase init) {
		this(init.words.length, init.topMask);
		set(init);
	}

	/**
	 * Constructs an empty 9x9 set, optionally pre-filled with all cells.
	 *
	 * @param full if {@code true}, all 81 cells are added
	 */
	public SudokuSetBase(boolean full) {
		this();
		if (full) {
			setAll();
		}
	}

	/**
	 * Constructs a set sized for the given board descriptor.
	 *
	 * @param spec board descriptor
	 */
	public SudokuSetBase(BoardSpec spec) {
		this(spec.wordsPerBitset, topMaskFor(spec));
	}

	/**
	 * General-purpose constructor: empty set with the given storage size.
	 *
	 * @param wordCount number of {@code long} words (each holds 64 cell bits)
	 * @param topMask   mask of valid bits in the top word
	 */
	public SudokuSetBase(int wordCount, long topMask) {
		if (wordCount < 1) {
			throw new IllegalArgumentException("wordCount must be >= 1");
		}
		this.words = new long[wordCount];
		this.topMask = topMask;
	}

	/** topMask for a given BoardSpec — bits 0..(totalCells-1) in the top word are valid. */
	private static long topMaskFor(BoardSpec spec) {
		int leftover = spec.length & 63;
		return leftover == 0 ? MAX_MASK1 : ((1L << leftover) - 1L);
	}

	@Override
	public SudokuSetBase clone() {
		SudokuSetBase newSet = null;
		try {
			newSet = (SudokuSetBase) super.clone();
			newSet.words = words.clone();
		} catch (CloneNotSupportedException ex) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error while cloning", ex);
		}
		return newSet;
	}

	public boolean isEmpty() {
		for (long w : words) {
			if (w != 0) return false;
		}
		return true;
	}

	public void add(int value) {
		words[value >>> 6] |= 1L << (value & 63);
		initialized = false;
	}

	public void remove(int value) {
		words[value >>> 6] &= ~(1L << (value & 63));
		initialized = false;
	}

	public final void set(SudokuSetBase set) {
		// Both sets must agree on storage size; assertion-style check.
		System.arraycopy(set.words, 0, words, 0, words.length);
		initialized = false;
	}

	public void set(int[] data) {
		Arrays.fill(words, 0L);
		initialized = false;
		for (int i = 0; i < data.length; i++) {
			add(data[i]);
		}
	}

	/**
	 * Legacy 9x9 setter: sets the two low words.
	 *
	 * @param m1 low word (cells 0..63)
	 * @param m2 high word (cells 64..127, masked by topMask)
	 */
	public void set(long m1, long m2) {
		words[0] = m1;
		if (words.length > 1) words[1] = m2;
		for (int i = 2; i < words.length; i++) words[i] = 0;
		initialized = false;
	}

	/**
	 * Sets only the first 32 bits of the set.
	 *
	 * @param data
	 */
	public void set(int data) {
		Arrays.fill(words, 0L);
		words[0] = data & 0xFFFFFFFFL;
		initialized = false;
	}

	public boolean contains(int value) {
		return (words[value >>> 6] & (1L << (value & 63))) != 0;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (!(o instanceof SudokuSetBase)) {
			return false;
		}
		SudokuSetBase s = (SudokuSetBase) o;
		return Arrays.equals(words, s.words);
	}

	@Override
	public int hashCode() {
		int hash = 3;
		for (long w : words) {
			hash = 71 * hash + (int) (w ^ (w >>> 32));
		}
		return hash;
	}

	public void clear() {
		Arrays.fill(words, 0L);
		initialized = false;
	}

	public final void setAll() {
		int last = words.length - 1;
		for (int i = 0; i < last; i++) words[i] = MAX_MASK1;
		words[last] = topMask;
		initialized = false;
	}

	public boolean intersects(SudokuSetBase b) {
		int n = Math.min(words.length, b.words.length);
		for (int i = 0; i < n; i++) {
			if ((words[i] & b.words[i]) != 0) return true;
		}
		return false;
	}

	/**
	 * If {@code this} and {@code b} overlap, the common candidates are added to
	 * {@code c}.
	 */
	public boolean intersects(SudokuSetBase b, SudokuSetBase c) {
		boolean result = false;
		int n = Math.min(words.length, b.words.length);
		for (int i = 0; i < n; i++) {
			long mask = words[i] & b.words[i];
			if (mask != 0) {
				result = true;
				c.words[i] |= mask;
				c.initialized = false;
			}
		}
		return result;
	}

	/**
	 * @return {@code true} if {@code b} is entirely contained in {@code this}
	 */
	public boolean contains(SudokuSetBase b) {
		int n = Math.min(words.length, b.words.length);
		for (int i = 0; i < n; i++) {
			if ((b.words[i] & ~words[i]) != 0) return false;
		}
		// Any bits set in b past this's storage means b has elements not in this.
		for (int i = n; i < b.words.length; i++) {
			if (b.words[i] != 0) return false;
		}
		return true;
	}

	public void or(SudokuSetBase b) {
		int n = Math.min(words.length, b.words.length);
		for (int i = 0; i < n; i++) words[i] |= b.words[i];
		initialized = false;
	}

	public void orNot(SudokuSetBase set) {
		// Matches the original Hodoku semantics: bit-for-bit `mask |= ~set.mask`,
		// without trimming ghost bits past the topMask. Callers either compose
		// further operations that re-mask, or don't depend on ghost bits.
		int n = Math.min(words.length, set.words.length);
		for (int i = 0; i < n; i++) words[i] |= ~set.words[i];
		initialized = false;
	}

	public void and(SudokuSetBase set) {
		int n = Math.min(words.length, set.words.length);
		for (int i = 0; i < n; i++) words[i] &= set.words[i];
		// If `set` is shorter, the missing words are zero, so clear ours past n.
		for (int i = n; i < words.length; i++) words[i] = 0;
		initialized = false;
	}

	public void andNot(SudokuSetBase set) {
		int n = Math.min(words.length, set.words.length);
		for (int i = 0; i < n; i++) words[i] &= ~set.words[i];
		initialized = false;
	}

	/**
	 * @return {@code (this & set) == this}
	 */
	public boolean andEquals(SudokuSetBase set) {
		int n = Math.min(words.length, set.words.length);
		for (int i = 0; i < n; i++) {
			if ((words[i] & set.words[i]) != words[i]) return false;
		}
		for (int i = n; i < words.length; i++) {
			if (words[i] != 0) return false;
		}
		return true;
	}

	/**
	 * @return {@code (this & ~set) == this}
	 */
	public boolean andNotEquals(SudokuSetBase set) {
		int n = Math.min(words.length, set.words.length);
		for (int i = 0; i < n; i++) {
			if ((words[i] & ~set.words[i]) != words[i]) return false;
		}
		return true;
	}

	/**
	 * @return {@code (this & set) == 0}
	 */
	public boolean andEmpty(SudokuSetBase set) {
		int n = Math.min(words.length, set.words.length);
		for (int i = 0; i < n; i++) {
			if ((words[i] & set.words[i]) != 0) return false;
		}
		return true;
	}

	public void not() {
		int last = words.length - 1;
		for (int i = 0; i < last; i++) words[i] = ~words[i];
		words[last] = ~words[last] & topMask;
		initialized = false;
	}

	/**
	 * Calculates {@code this |= (s1 & s2)}.
	 */
	public void orAndAnd(SudokuSetBase s1, SudokuSetBase s2) {
		int n = Math.min(words.length, Math.min(s1.words.length, s2.words.length));
		for (int i = 0; i < n; i++) words[i] |= (s1.words[i] & s2.words[i]);
		initialized = false;
	}

	/**
	 * Calculates {@code this = (s1 & s2)}.
	 */
	public void setAnd(SudokuSetBase s1, SudokuSetBase s2) {
		int n = Math.min(words.length, Math.min(s1.words.length, s2.words.length));
		for (int i = 0; i < n; i++) words[i] = (s1.words[i] & s2.words[i]);
		for (int i = n; i < words.length; i++) words[i] = 0;
		initialized = false;
	}

	/**
	 * Computes {@code (s1 & s2) == 0} without modifying either argument.
	 */
	public static boolean andEmpty(SudokuSetBase s1, SudokuSetBase s2) {
		int n = Math.min(s1.words.length, s2.words.length);
		for (int i = 0; i < n; i++) {
			if ((s1.words[i] & s2.words[i]) != 0) return false;
		}
		return true;
	}

	/**
	 * Calculates {@code this = (s1 | s2)}.
	 */
	public void setOr(SudokuSetBase s1, SudokuSetBase s2) {
		int n = Math.min(words.length, Math.min(s1.words.length, s2.words.length));
		for (int i = 0; i < n; i++) words[i] = (s1.words[i] | s2.words[i]);
		for (int i = n; i < words.length; i++) words[i] = 0;
		initialized = false;
	}

	protected String pM(long mask) {
		return Long.toHexString(mask);
	}

	@Override
	public String toString() {
		int totalBits = words.length * 64;
		int[] values = new int[totalBits];
		int anz = 0;
		int index = 0;
		// Iterate the legacy 9x9 layout when present, to preserve the original
		// hex-suffix formatting that test fixtures may compare against.
		long m1 = words[0];
		long m2 = words.length > 1 ? words[1] : 0;
		for (int i = 0; i < 64; i++) {
			if ((m1 & MASKS[i]) != 0) {
				values[index++] = i;
			}
		}
		int topBits = words.length > 1 ? 17 : 0;
		for (int i = 0; i < topBits; i++) {
			if ((m2 & MASKS[i]) != 0) {
				values[index++] = i + 64;
			}
		}
		// For larger boards, include the remaining bits past the legacy 81-cell range.
		for (int w = 2; w < words.length; w++) {
			long word = words[w];
			for (int i = 0; i < 64; i++) {
				if ((word & MASKS[i]) != 0) {
					values[index++] = w * 64 + i;
				}
			}
		}
		initialized = true;
		anz = index;
		if (anz == 0) {
			return "empty!";
		}
		StringBuilder tmp = new StringBuilder();
		tmp.append(Integer.toString(values[0]));
		for (int i = 1; i < anz; i++) {
			tmp.append(" ").append(Integer.toString(values[i]));
		}
		tmp.append(" ").append(pM(m1)).append("/").append(pM(m2));
		return tmp.toString();
	}

	// -----------------------------------------------------------------
	// Backward-compatible accessors (legacy mask1/mask2 view).
	// Only meaningful for 9x9-style sets (words.length <= 2).
	// -----------------------------------------------------------------

	public long getMask1() {
		return words[0];
	}

	public void setMask1(long mask1) {
		this.words[0] = mask1;
		this.initialized = false;
	}

	public long getMask2() {
		return words.length > 1 ? words[1] : 0L;
	}

	public void setMask2(long mask2) {
		if (words.length > 1) {
			this.words[1] = mask2;
			this.initialized = false;
		} else if (mask2 != 0) {
			throw new IllegalStateException("setMask2 on a 1-word set");
		}
	}

	// -----------------------------------------------------------------
	// New general-purpose accessors.
	// -----------------------------------------------------------------

	/** @return how many {@code long} words this set uses */
	public int getWordCount() {
		return words.length;
	}

	/** Read access to the underlying word array (do not mutate without invalidating). */
	public long[] getWords() {
		return words;
	}

	/** Read the i'th word. */
	public long getWord(int i) {
		return words[i];
	}

	/** Write the i'th word; marks the SudokuSet index cache stale. */
	public void setWord(int i, long w) {
		words[i] = w;
		initialized = false;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	/**
	 * Serialisation: read the legacy two-long form and reconstruct {@code words}.
	 * Legacy streams encode 9x9 sets, so words.length is 2 and topMask is
	 * {@link #MAX_MASK2}.
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		ObjectInputStream.GetField fields = in.readFields();
		long mask1 = fields.get("mask1", 0L);
		long mask2 = fields.get("mask2", 0L);
		boolean init = fields.get("initialized", true);
		this.words = new long[]{ mask1, mask2 };
		this.topMask = MAX_MASK2;
		this.initialized = init;
	}

	/**
	 * Serialisation: write the legacy two-long form so {@code templates.dat}
	 * regenerated by this class is still readable by other tools / older builds.
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		ObjectOutputStream.PutField fields = out.putFields();
		fields.put("mask1", words.length > 0 ? words[0] : 0L);
		fields.put("mask2", words.length > 1 ? words[1] : 0L);
		fields.put("initialized", initialized);
		out.writeFields();
	}
}
