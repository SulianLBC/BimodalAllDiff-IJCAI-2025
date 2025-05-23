/*
 * This file is part of examples, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.examples.nqueen;


import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;

import static org.chocosolver.solver.search.strategy.Search.failureLengthBasedSearch;
import static org.chocosolver.solver.search.strategy.Search.minDomLBSearch;

/**
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 31/03/11
 */
public class NQueenGlobal extends AbstractNQueen {

    @Override
    public void buildModel() {
        model = new Model("NQueen");
        vars = new IntVar[n];
        IntVar[] diag1 = new IntVar[n];
        IntVar[] diag2 = new IntVar[n];

        for (int i = 0; i < n; i++) {
            vars[i] = model.intVar("Q_" + i, 1, n, false);
            diag1[i] = model.offset(vars[i], i);
            diag2[i] = model.offset(vars[i], -i);
        }
        String consistency = "AC_TUNED";
        model.allDifferent(vars, consistency).post();
        model.allDifferent(diag1, consistency).post();
        model.allDifferent(diag2, consistency).post();
    }

    @Override
    public void configureSearch() {
        model.getSolver().setSearch(minDomLBSearch(vars));
    }

    public static void main(String[] args) {
        new NQueenGlobal().execute(args);
    }
}
