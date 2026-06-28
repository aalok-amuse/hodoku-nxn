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

package solver;

import java.util.ArrayList;
import java.util.List;
import sudoku.SolutionStep;
import sudoku.Sudoku2;
import sudoku.SudokuSet;

/**
 *
 * @author hobiwan
 */
public class GroupNode {
	public SudokuSet indices = new SudokuSet(); // indices as bit mask
	public SudokuSet buddies = new SudokuSet(); // all buddies that can see all cells in the group node
	public int cand; // candidate for grouped link
	public int line = -1; // row (index in rows), -1 if not applicable
	public int col = -1; // col (index in cols), -1 if not applicable
	public int block; // block (index in boxes)
	public int index1; // index of first cell
	public int index2; // index of second cell
	public int index3; // index of third cell or -1, if grouped node consists only of two cells

	private static SudokuSet candInHouse = new SudokuSet(); // all positions for a given candidate in a given house
	private static SudokuSet tmpSet = new SudokuSet(); // for check with blocks

	/**
	 * Creates a new instance of GroupNode bound to the given Sudoku's spec.
	 *
	 * @param sudoku  the active Sudoku — used for row/col/box decomposition and per-cell buddies
	 * @param cand    candidate for grouped link
	 * @param indices indices in the group node (2 or 3 cells)
	 */
	public GroupNode(Sudoku2 sudoku, int cand, SudokuSet indices) {
		this.cand = cand;
		this.indices.set(indices);
		index1 = indices.get(0);
		index2 = indices.get(1);
		index3 = -1;
		if (indices.size() > 2) {
			index3 = indices.get(2);
		}
		block = sudoku.boxOf(index1);
		if (sudoku.rowOf(index1) == sudoku.rowOf(index2)) {
			line = sudoku.rowOf(index1);
		}
		if (sudoku.colOf(index1) == sudoku.colOf(index2)) {
			col = sudoku.colOf(index1);
		}
		// calculate the buddies
		buddies.set(sudoku.getSpecData().buddies[index1]);
		buddies.and(sudoku.getSpecData().buddies[index2]);
		if (index3 >= 0) {
			buddies.and(sudoku.getSpecData().buddies[index3]);
		}
	}

	@Override
	public String toString() {
		return "GroupNode: " + cand + " - " + SolutionStep.getCompactCellPrint(index1, index2, index3) + "  - " + index1
				+ "/" + index2 + "/" + index3 + " (" + line + "/" + col + "/" + block + ")";
	}

	/**
	 * Gets all group nodes from the given sudoku and puts them in an ArrayList.
	 *
	 * For all candidates in all lines and all cols do: - check if they have a
	 * candidate left - if so, check if an intersection of line/col and a block
	 * contains more than one candidate; if yes -> group node found
	 *
	 * @param finder
	 * @return
	 */
	public static List<GroupNode> getGroupNodes(SudokuStepFinder finder) {
		List<GroupNode> groupNodes = new ArrayList<GroupNode>();
		Sudoku2 sudoku = finder.getSudoku();
		getGroupNodesForHouseType(groupNodes, finder, sudoku.getSpecData().rowTemplates);
		getGroupNodesForHouseType(groupNodes, finder, sudoku.getSpecData().colTemplates);

		return groupNodes;
	}

	private static void getGroupNodesForHouseType(List<GroupNode> groupNodes, SudokuStepFinder finder,
			SudokuSet[] houses) {
		Sudoku2 sudoku = finder.getSudoku();
		SudokuSet[] blockTemplates = sudoku.getSpecData().blockTemplates;
		int n = sudoku.getUnitCount();
		for (int i = 0; i < houses.length; i++) {
			for (int cand = 1; cand <= n; cand++) {
				candInHouse.set(houses[i]);
				candInHouse.and(finder.getCandidates()[cand]);
				if (candInHouse.isEmpty()) {
					// no candidates left in this house -> proceed
					continue;
				}

				// candidates left in house -> check blocks
				for (int j = 0; j < blockTemplates.length; j++) {
					tmpSet.set(candInHouse);
					tmpSet.and(blockTemplates[j]);
					if (tmpSet.isEmpty()) {
						// no candidates in this house -> proceed with next block
						continue;
					} else {
						// rather complicated for performance reasons (isEmpty() is much faster than
						// size())
						if (tmpSet.size() >= 2) {
							// group node found
							groupNodes.add(new GroupNode(sudoku, cand, tmpSet));
						}
					}
				}
			}
		}
	}
}
