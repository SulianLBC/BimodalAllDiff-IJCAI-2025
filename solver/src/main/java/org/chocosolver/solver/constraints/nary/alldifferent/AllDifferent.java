/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.constraints.nary.alldifferent;

import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.ConstraintsName;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.binary.PropNotEqualX_Y;
import org.chocosolver.solver.variables.IntVar;

import java.util.Objects;

/**
 * Ensures that all variables from VARS take a different value.
 * The consistency level should be chosen among "AC", "BC", "FC" and "DEFAULT".
 */
public class AllDifferent extends Constraint {

    public static String OPTION_CONSISTENCY = "DEFAULT";
    public static String OPTION_PROPINST = "DEFAULT";

    public static final String AC= "AC";
    public static final String AC_REGIN= "AC_REGIN";
    public static final String AC_ZHANG = "AC_ZHANG";
    public static final String AC_CLASSIC= "AC_CLASSIC";
    public static final String AC_COMPLEMENT = "AC_COMPLEMENT";
    public static final String AC_PARTIAL = "AC_PARTIAL";
    public static final String AC_TUNED = "AC_TUNED";
    public static final String BC= "BC";
    public static final String FC= "FC";
    public static final String NEQS= "NEQS";
    public static final String DEFAULT= "DEFAULT";

    public AllDifferent(IntVar[] vars, String type) {
        super(ConstraintsName.ALLDIFFERENT, createPropagators(vars, type));
    }

    private static Propagator[] createPropagators(IntVar[] VARS, String consistency) {
        if(!Objects.equals(OPTION_CONSISTENCY, "DEFAULT")){
            consistency = OPTION_CONSISTENCY;
        }
        boolean propInst = !Objects.equals(OPTION_PROPINST, "false") && !Objects.equals(OPTION_PROPINST, "FALSE");

        switch (consistency) {
            case NEQS: {
                int s = VARS.length;
                int k = 0;
                Propagator[] props = new Propagator[(s * s - s) / 2];
                for (int i = 0; i < s - 1; i++) {
                    for (int j = i + 1; j < s; j++) {
                        props[k++] = new PropNotEqualX_Y(VARS[i], VARS[j]);
                    }
                }
                return props;
            }
            case FC:
                return new Propagator[]{new PropAllDiffInst(VARS)};
            case BC:
                if (propInst) {return new Propagator[]{new PropAllDiffInst(VARS), new PropAllDiffBC(VARS)};}
                else {return new Propagator[]{new PropAllDiffBC(VARS)};}
            case AC_REGIN:
                if (propInst) {return new Propagator[]{new PropAllDiffInst(VARS), new PropAllDiffAC(VARS, false)};}
                else {return new Propagator[]{new PropAllDiffAC(VARS, false)};}
            case AC:
            case AC_ZHANG:
                if (propInst) {return new Propagator[]{new PropAllDiffInst(VARS), new PropAllDiffAC(VARS, true)};}
                else {return new Propagator[]{new PropAllDiffAC(VARS, true)};}
            case AC_CLASSIC:
            case AC_COMPLEMENT:
            case AC_PARTIAL:
            case AC_TUNED:
                if(propInst) {return new Propagator[]{new PropAllDiffInst(VARS), new PropAllDiffAC(VARS, consistency)};}
                else {return new Propagator[]{new PropAllDiffAC(VARS, consistency)};}
            case DEFAULT:
            default: {
                // adds a Probabilistic AC (only if at least some variables have an enumerated domain)
                boolean enumDom = false;
                for (int i = 0; i < VARS.length && !enumDom; i++) {
                    if (VARS[i].hasEnumeratedDomain()) {
                        enumDom = true;
                    }
                }
                if (enumDom) {
                    return new Propagator[]{new PropAllDiffInst(VARS), new PropAllDiffBC(VARS), new PropAllDiffAdaptative(VARS)};
                } else {
                    return createPropagators(VARS, "BC");
                }
            }
        }
    }
}
