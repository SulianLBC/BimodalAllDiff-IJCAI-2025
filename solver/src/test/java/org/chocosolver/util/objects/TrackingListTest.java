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

import static org.testng.Assert.assertEquals;

/**
 * @author Sulian LE BOZEC-CHIFFOLEAU
 */
public class TrackingListTest {


    public TrackingList create() {
        return new TrackingList(1, 10);
    }

    @Test(groups = "1s", timeOut=60000)
    public void testInitialState() {
        TrackingList Tlist = create();
        assertEquals(Tlist.getSize(), 10);

        // Traverse from left to right
        int e = Tlist.getSource();
        int num = 0;
        while (Tlist.hasNext(e)) {
            e = Tlist.getNext(e);
            num++;
            assertEquals(e, num);
        }
        assertEquals(num, 10);

        // Traverse from right to left
        e = Tlist.getSink();
        num = 11;
        while (Tlist.hasPrevious(e)) {
            e = Tlist.getPrevious(e);
            num--;
            assertEquals(e, num);
        }
        assertEquals(num, 1);

        assertEquals(Tlist.isPresent(1), true);
        assertEquals(Tlist.isPresent(5), true);
        assertEquals(Tlist.isPresent(10), true);
    }

    @Test(groups = "1s", timeOut=60000)
    public void testRemoveAllAndRefill() {
        TrackingList Tlist = create();

        // Remove all
        int e = Tlist.getSource();
        while (Tlist.hasNext(e)) {
            e = Tlist.getNext(e);
            Tlist.remove(e);
        }
        assertEquals(Tlist.getNext(Tlist.getSource()), Tlist.getSink());
        assertEquals(Tlist.getPrevious(Tlist.getSink()), Tlist.getSource());
        assertEquals(Tlist.getSize(), 0);
        assertEquals(Tlist.isEmpty(), true);

        // Refill
        Tlist.refill();
        assertEquals(Tlist.getSize(), 10);
        assertEquals(Tlist.isEmpty(), false);

        // Traverse from left to right
        e = Tlist.getSource();
        int num = 0;
        while (Tlist.hasNext(e)) {
            e = Tlist.getNext(e);
            num++;
            assertEquals(e, num);
        }
        assertEquals(num, 10);

        // Traverse from right to left
        e = Tlist.getSink();
        num = 11;
        while (Tlist.hasPrevious(e)) {
            e = Tlist.getPrevious(e);
            num--;
            assertEquals(e, num);
        }
        assertEquals(num, 1);

        assertEquals(Tlist.isPresent(1), true);
        assertEquals(Tlist.isPresent(5), true);
        assertEquals(Tlist.isPresent(10), true);
    }


    @Test(groups = "1s", timeOut=60000)
    public void testRemoveAllAndRefillRandomOrder() {
        TrackingList Tlist = create();

        // Remove all
        Tlist.remove(5);
        Tlist.remove(3);
        Tlist.remove(9);
        Tlist.remove(7);
        Tlist.remove(6);
        Tlist.remove(10);
        Tlist.remove(2);
        Tlist.remove(8);
        Tlist.remove(1);
        Tlist.remove(4);


        assertEquals(Tlist.getNext(Tlist.getSource()), Tlist.getSink());
        assertEquals(Tlist.getPrevious(Tlist.getSink()), Tlist.getSource());
        assertEquals(Tlist.getSize(), 0);
        assertEquals(Tlist.getUniverseSize(), 10);
        assertEquals(Tlist.isEmpty(), true);

        for(int i = 1; i <= 10; i++) {
            assertEquals(Tlist.isPresent(i), false);
            assertEquals(Tlist.trackLeft(i), Tlist.getSource());
        }

        // Refill
        Tlist.refill();
        assertEquals(Tlist.getSize(), 10);
        assertEquals(Tlist.getUniverseSize(), 10);
        assertEquals(Tlist.isEmpty(), false);

        // Traverse from left to right
        int e = Tlist.getSource();
        int num = 0;
        while (Tlist.hasNext(e)) {
            e = Tlist.getNext(e);
            num++;
            assertEquals(e, num);
        }
        assertEquals(num, 10);

        // Traverse from right to left
        e = Tlist.getSink();
        num = 11;
        while (Tlist.hasPrevious(e)) {
            e = Tlist.getPrevious(e);
            num--;
            assertEquals(e, num);
        }
        assertEquals(num, 1);

        assertEquals(Tlist.isPresent(1), true);
        assertEquals(Tlist.isPresent(5), true);
        assertEquals(Tlist.isPresent(10), true);
    }


    @Test(groups = "1s", timeOut=60000)
    public void testTrackLeftRight() {
        TrackingList Tlist = create();

        Tlist.remove(5);
        Tlist.remove(6);
        assertEquals(Tlist.trackLeft(5), 4);
        assertEquals(Tlist.trackLeft(6), 4);
        assertEquals(Tlist.trackLeft(7), 7);

        assertEquals(Tlist.isPresent(5), false);
        assertEquals(Tlist.isPresent(6), false);

        Tlist.refill();
        Tlist.remove(5);

        assertEquals(Tlist.trackLeft(5), 4);
        assertEquals(Tlist.trackLeft(6), 6);
        assertEquals(Tlist.trackLeft(7), 7);

        assertEquals(Tlist.isPresent(5), false);
        assertEquals(Tlist.isPresent(6), true);

        Tlist.refill();

        assertEquals(Tlist.isPresent(5), true);
        assertEquals(Tlist.isPresent(6), true);
    }

    @Test(groups = "1s", timeOut=60000)
    public void testRemoveUniverse() {
        TrackingList Tlist = create();

        Tlist.removeFromUniverse(4);
        assertEquals(Tlist.getSize(), 9);
        assertEquals(Tlist.getUniverseSize(), 9);
        assertEquals(Tlist.trackLeft(4), 3);

        Tlist.removeFromUniverse(1);
        Tlist.removeFromUniverse(2);
        Tlist.removeFromUniverse(3);
        Tlist.removeFromUniverse(5);
        assertEquals(Tlist.getSize(), 5);;
        assertEquals(Tlist.getUniverseSize(), 5);

        Tlist.remove(8);
        assertEquals(Tlist.isPresent(8), false);
        assertEquals(Tlist.getSize(), 4);
        assertEquals(Tlist.getUniverseSize(), 5);

        Tlist.refill();
        assertEquals(Tlist.isPresent(8), true);
        assertEquals(Tlist.getSize(), 5);
        assertEquals(Tlist.getUniverseSize(), 5);

        assertEquals(Tlist.isPresent(1), false);
        assertEquals(Tlist.isPresent(5), false);
        assertEquals(Tlist.isPresent(10), true);
    }
}
