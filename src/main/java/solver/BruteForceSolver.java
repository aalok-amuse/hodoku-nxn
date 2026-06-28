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

import generator.SudokuGeneratorFactory;
import sudoku.DpllSolver;
import sudoku.SolutionStep;
import sudoku.SolutionType;
import sudoku.Sudoku2;
import sudoku.SudokuSet;

/**
 *
 * @author hobiwan
 */
public class BruteForceSolver extends AbstractSolver {

	/**
	 * Creates a new instance of BruteForceSolver
	 * 
	 * @param finder
	 */
	public BruteForceSolver(SudokuStepFinder finder) {
		super(finder);
	}

	@Override
	protected SolutionStep getStep(SolutionType type) {
		
		SolutionStep result = null;
		sudoku = finder.getSudoku();
		
		switch (type) {
		case BRUTE_FORCE:
			result = getBruteForce();
			break;
		default:
			break;
		}
		
		return result;
	}

	@Override
	protected boolean doStep(SolutionStep step) {
		
		boolean handled = true;
		sudoku = finder.getSudoku();
		
		switch (step.getType()) {
		case BRUTE_FORCE:
			int value = step.getValues().get(0);
			for (int index : step.getIndices()) {
				sudoku.setCell(index, value);
			}
			break;
		default:
			handled = false;
		}
		
		return handled;
	}

	/**
	 * Das Sudoku2 wird mit Dancing-Links gelöst. Anschließend wird aus den nicht
	 * gesetzten Zellen die mittlere ausgesucht und gesetzt.<br>
	 * If the sudoku is invalid, no result is returned.
	 */
	private SolutionStep getBruteForce() {

		// can happen, when command line mode is used (no brute force solving is done)
		// sets the solution in the sudoku
		if (!sudoku.isSolutionSet()) {
			boolean isValid = false;
			// The generator's DPLL is hardwired to 9x9; only ask it for that size.
			if (sudoku.getUnitCount() == 9) {
				isValid = SudokuGeneratorFactory.getDefaultGeneratorInstance().validSolution(sudoku);
			}
			if (!isValid) {
				// Spec-aware fallback for non-9x9 (and for any 9x9 the generator couldn't validate).
				int[] solved = DpllSolver.solve(sudoku);
				if (solved == null) {
					return null;
				}
				sudoku.setSolution(solved);
				sudoku.setSolutionSet(true);
			}
		}

		// alle Positionen ermitteln, die im ungelösten Sudoku2 noch nicht gesetzt sind
		SudokuSet unsolved = new SudokuSet();
		for (int i = 0; i < sudoku.getLength(); i++) {
			if (sudoku.getValue(i) == 0) {
				unsolved.add(i);
			}
		}

		// jetzt die mittlere Zelle aussuchen
		int index = unsolved.size() / 2;
		index = unsolved.get(index);

		// Step zusammenbauen
		SolutionStep step = new SolutionStep(SolutionType.BRUTE_FORCE);
		step.addIndex(index);
		step.addValue(sudoku.getSolution(index));

		return step;
	}
}
