/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.constraints.nary.alldifferent.algo;

import gnu.trove.map.hash.TIntIntHashMap;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.graphOperations.connectivity.StrongConnectivityFinder;
import org.chocosolver.util.objects.BipartiteMatching;
import org.chocosolver.util.objects.IntCircularQueue;
import org.chocosolver.util.objects.TrackingList;
import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;
import org.chocosolver.util.objects.setDataStructures.SetType;
import org.jgrapht.alg.interfaces.MatchingAlgorithm.Matching;

import java.util.BitSet;
import java.util.Stack;

/**
 * Algorithm of Alldifferent ensuring GAC
 *
 * Uses a variant of Regin algorithm based on the partially complemented (PC) approach
 * <p/>
 * Keeps track of previous matching for further calls
 * <p/>
 * 
 * @author Sulian Le Bozec-Chiffoleau
 */
public class AlgoAllDiffPC implements IAlldifferentAlgorithm {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    ICause aCause;
    protected IntVar[] vars;
    private final int R;
    private TrackingList variablesDynamic;
    private int minValue;
    private int maxValue;
    private final int D;
    private TrackingList valuesDynamic;
    private final int fail;
    private BipartiteMatching matching;
    private int[] parentBFS;
    private IntCircularQueue queueBFS;
    private IntCircularQueue SCC;
    private TrackingList complementSCC;
    private IntCircularQueue tarjanStack;
    private boolean[] inStack;
    private int[] pre;
    private int[] low;
    private int numVisit;
    private boolean atLeastTwo;
    private IntCircularQueue toRemoveFromUniverse;
    private int mode;
    private long timeMMNano;
    private long timeSCCNano;
    private long timePruneNano;
    private long timeTotalNano;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    public AlgoAllDiffPC(IntVar[] variables, ICause cause) {
        // Variables and data structures for the whole procedure
        aCause = cause;
        this.vars = variables;
        this.R = variables.length;
        this.variablesDynamic = new TrackingList(0, R-1);
        this.minValue = vars[0].getLB();
        this.maxValue = vars[0].getUB();
        for (IntVar x : vars) {
            minValue = minValue < x.getLB() ? minValue : x.getLB();
            maxValue = maxValue > x.getUB() ? maxValue : x.getUB();
        }
        this.D = maxValue - minValue + 1;
        this.valuesDynamic = new TrackingList(minValue, maxValue);
        this.fail = minValue - 1;
        this.matching = new BipartiteMatching(0, R-1, minValue, maxValue);

        // Specific data structures for finding the maximum matching
        this.parentBFS = new int[D];
        this.queueBFS = new IntCircularQueue(R);

        // Specific data structures for computing the strongly connected components
        this.SCC = new IntCircularQueue(D);
        this.complementSCC = new TrackingList(0, D-1);
        this.tarjanStack = new IntCircularQueue(D);
        this.inStack = new boolean[D];
        this.pre = new int[D];
        this.low = new int[D];

        // Specific data structures for pruning
        this.toRemoveFromUniverse = new IntCircularQueue(D);
    }

    //***********************************************************************************
    // PROPAGATION
    //***********************************************************************************

    public boolean propagate() throws ContradictionException {
        findMaximumMatching();
        return filter();
    }

    //***********************************************************************************
    // MAXIMUM MATCHING
    //***********************************************************************************


    public boolean findMaximumMatching(){
        int var = variablesDynamic.getSource();
        while (variablesDynamic.hasNext(var)) { // We increase the size of the current matching until no unmatched variable remains
            var = variablesDynamic.getNext(var);
            if (!matching.inMatchingU(var) || !vars[var].contains(matching.getMatchU(var))) {
                if (!vars[var].contains(matching.getMatchU(var))) { // Repair the matching by deleting the pairs that were pruned outside the constraint
                    matching.unMatch(var, matching.getMatchU(var));
                }
                valuesDynamic.refill();   // We refill the list with the recently removed elements, instead of recreating it from scratch
                int val = augmentingPath(var);
                if (val != fail) {
                    augmentMatching(val);
                }
                else {
                    System.out.println("The constraint can not be satisfied");
                    return false;
                }
            }
        }
        valuesDynamic.refill(); // valuesDynamic is a global variable used in the whole filtering procedure, so we must refill it
        return true;
    }


    public void augmentMatching(int root) { // By knowing the parent of each value in the BFS tree and the current match of the variables, we can retrieve the augmenting path from the last value in the path
        int v = root;
        while (matching.inMatchingU(getParent(v))) {
            int v_next = matching.getMatchU(getParent(v));
            // We swith the edges of the matching on the augmenting path
            matching.unMatch(getParent(v), v_next);
            matching.setMatch(getParent(v), v);
            v = v_next;
        }
        // The last variable we ecounter is unmatched
        matching.setMatch(getParent(v), v);
    }

    public int augmentingPath(int root) {
        queueBFS.clear();   // We use the same queue as in the previous iteration, so we need to unload it (done in O(1) time)
        queueBFS.addLast(root);
        while (!queueBFS.isEmpty()) {
            int var = queueBFS.pollLast();
            int val;
            if (vars[var].getDomainSize() < valuesDynamic.getSize()) {    // If var has a small domain, we iterate over its domain and explore the unvisited values
                int ub = vars[var].getUB();
                for (val = vars[var].getLB(); val <= ub; val = vars[var].nextValue(val)) {
                    if (valuesDynamic.isPresent(val) && stop(var, val)) {return val;}
                }
            } else {    // If var has a large domain, we iterate over the unvisited values and explore the ones that are in var's domain
                val = valuesDynamic.getSource();
                while (valuesDynamic.hasNext(val)) {
                    val = valuesDynamic.getNext(val);
                    if (vars[var].contains(val) && stop(var, val)) {return val;}
                }
            }
        }
        return fail;
    }

    private boolean stop(int var, int val) {
        setParent(var, val);
        if (matching.inMatchingV(val)) { // If the value is already matched, we contine the exploration from its matched variable
            valuesDynamic.remove(val);
            queueBFS.addLast(matching.getMatchV(val));
            return false;
        } else {return true;} // If the value is not matched, we can stop the exploration because we found an augmenting path
    }

    private int getParent(int val) {
        return parentBFS[val - minValue];
    }

    private void setParent(int var, int val) {
        parentBFS[val - minValue] = var;
    }

    private int min(int a, int b) {return a < b ? a : b;}

    //***********************************************************************************
    // SCC + PRUNING
    //***********************************************************************************

    public void filter() {
        long timeStart = System.nanoTime();
        this.numVisit = 1;
        this.atLeastTwo = false;
        for(int val : matching.getMatchedV()) {
            if (valuesDynamic.isPresent(val)) {
                hyDFS(matching.getMatchV(val));
            }
        }
        if (atLeastTwo) {LastPrune();}
        //TODO: the remaining unvisited values are present in the domain of no variables, thus we can remove them from the universe of values for the next iterations in the descent
        //
        //

        // Ensure we properly reinitialise the structures for the next call the AllDifferent procedure (espiacally valuesDynamic which is in fact the universe of values when refilled)
        valuesDynamic.refill();
        SCC.clear();
        complementSCC.refill();
        tarjanStack.clear();
        timeSCCNano += System.nanoTime() - timeStart;
        timeTotalNano += System.nanoTime() - timeStart;
    }

    public void hyDFS(int var) {
        pre[matching.getMatchU(var)] = numVisit;
        low[matching.getMatchU(var)] = numVisit;
        numVisit++;
        valuesDynamic.remove(matching.getMatchU(var));
        tarjanStack.addLast(matching.getMatchU(var));
        inStack[matching.getMatchU(var)] = true;
        //visited[matching.getMatchU(var)] = true;
        int val;

        if(vars[var].getDomainSize() < valuesDynamic.getSize()) {   // If var has a small domain then iterate over the domain
            instance.initDomainIterator(var);
            while (instance.hasNextValue(var)) {
                val = instance.getNextValue(var);
                if (val != matching.getMatchU(var) && valuesDynamic.isPresent(val)) {Process(var, val);}
                else if (val != matching.getMatchU(var) && inStack[val]) {low[matching.getMatchU(var)] = min(low[matching.getMatchU(var)], pre[val]);} // M(var).low = min(M(var).low, val.pre)
            }
        } else { // If var has a large domain then iterate over the unvisited values and over the values in Tarjan's stack
            int pointerVar = valuesDynamic.getSource();
            while (valuesDynamic.hasNext(pointerVar)) { // Explore all the branches going out of var in the DFS tree
                pointerVar = valuesDynamic.trackLeft(pointerVar); // Go back in the in-list
                while(valuesDynamic.hasNext(pointerVar) && vars[var].contains(valuesDynamic.getNext(pointerVar))) { // Go to the last consecutive non-domain value
                    pointerVar = valuesDynamic.getNext(pointerVar);
                }
                if (valuesDynamic.hasNext(pointerVar)) {Process(var, valuesDynamic.getNext(pointerVar));} // If we did not reach the end of the in-list, the next value is a domain value
            }
            tarjanStack.initIterator();
            while (tarjanStack.hasNext()) {
                val = tarjanStack.getNext();
                if (instance.inDomain(var, val) || pre[val] >= low[matching.getMatchU(var)]) {
                    low[matching.getMatchU(var)] = min(low[matching.getMatchU(var)], pre[val]); // M(var).low = min(M(var).low, val.pre)
                    break;
                }
            }
        }
        if (pre[matching.getMatchU(var)] == low[matching.getMatchU(var)]) {Prune(matching.getMatchU(var));}

    }

    public void Process(int var, int val) {
        if (matching.inMatchingV(val)) {    // If the value is already matched, we contine the exploration from its matched variable
            hyDFS(matching.getMatchV(val));
            low[matching.getMatchU(var)] = min(low[matching.getMatchU(var)], low[val]); // M(var).low = min(M(var).low, val.low)
        } else {    // If the value is not matched it leads to the sink node t, so we artificially explore it
            pre[val] = numVisit;
            low[val] = 0;
            numVisit++;
            low[matching.getMatchU(var)] = 0; // M(var).low = 0
            valuesDynamic.remove(val);
            tarjanStack.addLast(val);
            inStack[val] = true;
        }
    }

    public void Prune(int root) {
        long timeStart = System.nanoTime();
        atLeastTwo = true;
        SCC.clear();
        complementSCC.refill();
        int var;
        int val;
        do {    // Get all the values of the discovered SCC and construct the complement
            val = tarjanStack.pollLast();
            inStack[val] = false;
            SCC.addLast(val);
            complementSCC.remove(val);
        } while (val != root);

        // Particular case where we can force the instanciation of the matched variable to the unique value of the SCC, and remove both from the universes of variables and values
        if (SCC.getSize() == 1) {
            //TODO
        }

        //System.out.println("Number of values in the current SCC: " + SCC.getSize()); //DEBUG
        SCC.initIterator();
        while (SCC.hasNext()) { // Check the variables of the SCC to prune their domain values that are in the complement
            val = SCC.getNext();
            if (matching.inMatchingV(val)) {
                var = matching.getMatchV(val);
                if (vars[var].getDomainSize() < complementSCC.getSize()) {  // If var has a small domain then iterate over the domain and prune the values that are in the complement, except the one matched to var
                    int domainValue;
                    instance.initDomainIterator(var);
                    while(instance.hasNextValue(var)) {
                        domainValue = instance.getNextValue(var);
                        if (complementSCC.isPresent(domainValue) && domainValue != matching.getMatchU(var)) {
                            // Prune the pair (var, domainValue)
                            instance.removeFromDomain(var, domainValue);
                            //System.out.println("Pair pruned: (x" + var + ", " + domainValue + ")" ); //DEBUG
                            pruned = true;
                        }
                    }
                } else {    // If var has a large domain then iterate over the values in the complement and prune the ones that are in the domain of var, except the one matched to var 
                    int complementValue = complementSCC.getSource();
                    while(complementSCC.hasNext(complementValue)) {
                        complementValue = complementSCC.getNext(complementValue);
                        if (instance.inDomain(var, complementValue) && complementValue != matching.getMatchU(var)) {
                            // Prune the pair (var, complementValue)
                            instance.removeFromDomain(var, complementValue);
                        }
                    }
                }
            }
        }
        timePruneNano += System.nanoTime() - timeStart;
        timeSCCNano -= System.nanoTime() - timeStart;
    }

    public void LastPrune() { // All the remaining values in Tarjan's stack are in the same SCC, the one containing the artificial node t
    long timeStart = System.nanoTime();
        SCC.clear();
        complementSCC.refill();
        int var;
        int val;
        while (!tarjanStack.isEmpty()) {    // Get all the values of the discovered SCC and construct the complement
            val = tarjanStack.pollLast();
            inStack[val] = false;
            SCC.addLast(val);
            complementSCC.remove(val);
        }

        SCC.initIterator();
        while (SCC.hasNext()) { // Check the variables of the SCC to prune their domain values that are in the complement
            val = SCC.getNext();
            if (matching.inMatchingV(val)) {
                var = matching.getMatchV(val);
                if (vars[var].getDomainSize() < complementSCC.getSize()) {  // If var has a small domain then iterate over the domain and prune the values that are in the complement, except the one matched to var
                    int domainValue;
                    instance.initDomainIterator(var);
                    while(instance.hasNextValue(var)) {
                        domainValue = instance.getNextValue(var);
                        if (complementSCC.isPresent(domainValue) && domainValue != matching.getMatchU(var)) {
                            // Prune the pair (var, domainValue)
                            instance.removeFromDomain(var, domainValue);
                            //System.out.println("Pair pruned: (x" + var + ", " + domainValue + ")" ); //DEBUG
                        }
                    }
                } else {    // If var has a large domain then iterate over the values in the complement and prune the ones that are in the domain of var, except the one matched to var 
                    int complementValue = complementSCC.getSource();
                    while(complementSCC.hasNext(complementValue)) {
                        complementValue = complementSCC.getNext(complementValue);
                        if (instance.inDomain(var, complementValue) && complementValue != matching.getMatchU(var)) {
                            // Prune the pair (var, complementValue)
                            instance.removeFromDomain(var, complementValue);
                            //System.out.println("Pair pruned: (x" + var + ", " + complementValue + ")" ); //DEBUG
                        }
                    }
                }
            }
        }
        timePruneNano += System.nanoTime() - timeStart;
        timeSCCNano -= System.nanoTime() - timeStart;
    }

}
