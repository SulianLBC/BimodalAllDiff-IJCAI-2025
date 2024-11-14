/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.util.objects;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author Sulian LE BOZEC-CHIFFOLEAU
 */
public class BipartiteMatchingTest {


    public BipartiteMatching create() {
        return new BipartiteMatching(1, 10, 6, 20);
    }

    @Test(groups = "1s", timeOut=60000)
    public void testInitialState() {
        BipartiteMatching matching = create();

        assertEquals(matching.getsizeU(), 10);
        assertEquals(matching.getsizeV(), 15);
        assertEquals(matching.getSize(), 0);

        assertEquals(matching.inMatchingU(4), false);
        assertEquals(matching.inMatchingV(15), false);

        assertEquals(matching.isMaximum(), false);
        assertEquals(matching.isValid(), true);
    }

    @Test(groups = "1s", timeOut=60000)
    public void testMatchUnmatch() {
        BipartiteMatching matching = create();

        matching.setMatch(6, 9);
        matching.setMatch(10, 10);
        matching.setMatch(1, 7);
        matching.setMatch(9, 6);
        matching.setMatch(4, 20);

        assertEquals(matching.getSize(), 5);
        assertEquals(matching.isMaximum(), false);
        assertEquals(matching.isValid(), true);

        assertEquals(matching.inMatchingU(4), true);
        assertEquals(matching.inMatchingU(8), false);
        assertEquals(matching.inMatchingV(7), true);
        assertEquals(matching.inMatchingV(11), false);

        assertEquals(matching.getMatchU(1), 7);
        assertEquals(matching.getMatchV(20), 4);

        matching.unMatch(6, 9);
        matching.unMatch(9, 6);
        matching.unMatch(4, 20);

        assertEquals(matching.getSize(), 2);
        assertEquals(matching.isMaximum(), false);
        assertEquals(matching.isValid(), true);

        assertEquals(matching.inMatchingU(4), false);
        assertEquals(matching.inMatchingU(8), false);
        assertEquals(matching.inMatchingV(7), true);
        assertEquals(matching.inMatchingV(11), false);
    }

    @Test(groups = "1s", timeOut=60000)
    public void testMaximumMatching() {
        BipartiteMatching matching = create();

        for (int i = 1; i <= 10; i++) {
            matching.setMatch(i, i+5);
        }

        assertEquals(matching.getSize(), 10);
        assertEquals(matching.isMaximum(), true);
        assertEquals(matching.isValid(), true);

        for (int i = 1; i <= 10; i++) {
            assertEquals(matching.inMatchingU(i), true);
            assertEquals(matching.inMatchingV(i+5), true);
        }

        for (int i = 1; i <= 10; i++) {
            matching.unMatch(i, i+5);
        }

        assertEquals(matching.getSize(), 0);
        assertEquals(matching.isMaximum(), false);
        assertEquals(matching.isValid(), true);

        for (int i = 1; i <= 10; i++) {
            assertEquals(matching.inMatchingU(i), false);
            assertEquals(matching.inMatchingV(i+5), false);
        }
    }

}
