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

import org.chocosolver.solver.ICause;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.BipartiteMatching;
import org.chocosolver.util.objects.IntCircularQueue;
import org.chocosolver.util.objects.TrackingList;
import org.chocosolver.memory.IEnvironment;



/**
 * Algorithm of Alldifferent ensuring GAC
 *
 * Uses a variant of Regin algorithm based on the partially complemented (PC) approach
 * <p/>
 * Keeps track of previous matching and the sets of relevant variables and values for further calls
 * <p/>
 * 
 * @author Sulian Le Bozec-Chiffoleau
 */
public class AlgoAllDiffHybrid implements IAlldifferentAlgorithm {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    public static final String CLASSIC = "AC_CLASSIC";
    public static final String COMPLEMENT = "AC_COMPLEMENT";
    public static final String HYBRID = "AC_HYBRID";
    public static final String TUNED = "AC_TUNED";

    ICause aCause;
    Model model;
    protected IntVar[] vars;
    private final int R;    // Total number of variables
    private TrackingList variablesDynamic;  // The dynamic list of uninstanciated variables
    private final int minValue;
    private final int maxValue;
    private final int D;    // Total number of values
    private TrackingList valuesDynamic;  // The dynamic list of values present in the domain of at least one variable and not matched to an instantiated variable
    private final int fail; // Symbol signifying we couldn't find an augmenting path
    private BipartiteMatching matching; // The matching used dynamically
    private int[] parentBFS; // Array storing the parent of each value-node in the BFS tree
    private IntCircularQueue queueBFS; // Queue of the variables to explore during the BFS
    private final int t_node; // Symbol representing the artificial sink node of the Residual Graph
    private IntCircularQueue SCC; // A stack used to store the last SCC found (only containing the value-nodes)
    private TrackingList complementSCC; // List of the values that are not in SCC
    private IntCircularQueue tarjanStack; // The stack used in Tarjan's algorithm to costruct the SCCs
    private boolean[] inStack; // Boolean array informing the presence of a value in the stack
    private int[] pre; // Pre visit order of the values
    private int[] low; // Low point of the values
    private int numVisit; // Current visit number of the DFS in Tarjan's algorithm
    private boolean atLeastTwo; // Allows to check wether there is at least two SCCs
    private IntCircularQueue toRemoveFromVariableUniverse; // The variables detected during the procedure that will be removed from variablesDynamic
    private IntCircularQueue toRemoveFromValueUniverse; // The values detected during the procedure that will be removed from valuesDynamic and complementSCC
    private String mode; // Indiating the mode in which we are using the procedure (Classic, Complement, Hybrid or Tuned)
    private boolean pruned; // True if some variable-value pairs were pruned
    private long timeMatchingNano;
    private long timeSCCNano;
    private long timePruneNano;
    private long timeTotalNano;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    public AlgoAllDiffHybrid(IntVar[] variables, ICause cause, String ACmode) {
        // Variables and data structures for the whole procedure
        this.aCause = cause;
        this.model = variables[0].getModel();
        this.vars = variables;
        this.R = variables.length;
        this.variablesDynamic = new TrackingList(0, R-1);
        int tempMinValue = vars[0].getLB();
        int tempMaxValue = vars[0].getUB();
        for (IntVar x : vars) {
            tempMinValue = tempMinValue < x.getLB() ? tempMinValue : x.getLB();
            tempMaxValue = tempMaxValue > x.getUB() ? tempMaxValue : x.getUB();
        }
        this.minValue = tempMinValue;
        this.maxValue = tempMaxValue;
        this.D = maxValue - minValue + 1;
        this.valuesDynamic = new TrackingList(minValue, maxValue);
        refineUniverse(valuesDynamic);
        this.fail = minValue - 1;
        this.matching = new BipartiteMatching(0, R-1, minValue, maxValue);

        this.mode = ACmode;

        // Specific data structures for finding the maximum matching
        this.parentBFS = new int[D];
        this.queueBFS = new IntCircularQueue(R);

        // Specific data structures for computing the strongly connected components
        this.t_node = minValue - 1;
        this.SCC = new IntCircularQueue(D);
        this.complementSCC = new TrackingList(minValue, maxValue);
        refineUniverse(complementSCC);
        this.tarjanStack = new IntCircularQueue(D + 1); // This stack also contains the artificial node t_node
        this.inStack = new boolean[D];
        this.pre = new int[D];
        this.low = new int[D];

        // Specific data structures for the decrementality and backtrackability of the universes of variables and values
        this.toRemoveFromVariableUniverse = new IntCircularQueue(R);
        this.toRemoveFromValueUniverse = new IntCircularQueue(D);
    }

    private void refineUniverse(TrackingList valueUniverse) { // The tracking list initially contains an interval, so we refine it by removing the values that are present in no variables' domain (which may contain holes)
        boolean valuePresent = false;
        for (int value = minValue; value <= maxValue; value++) {
            for (IntVar variable : vars) {
                if (variable.contains(value)) {
                    valuePresent = true;
                    break;
                }
            }
            if (!valuePresent) {valueUniverse.removeFromUniverse(value);}
        }
    }

    //***********************************************************************************
    // PROPAGATION
    //***********************************************************************************

    public boolean propagate() throws ContradictionException {
        updateUniverseOpening();
        if (!findMaximumMatching()) {throw new Error("Error: the AllDifferent constraint can not be satisfied.");}
        this.pruned = false;
        filter();
        updateUniverseEnding();
        return this.pruned;
    }

    //***********************************************************************************
    // MAXIMUM MATCHING
    //***********************************************************************************


    private boolean findMaximumMatching(){
        long timeStart = System.nanoTime();
        int var = variablesDynamic.getSource();
        while (variablesDynamic.hasNext(var)) { // We increase the size of the current matching until no unmatched variable remains
            var = variablesDynamic.getNext(var);
            if (!matching.inMatchingU(var) || !vars[var].contains(matching.getMatchU(var))) {
                if (matching.inMatchingU(var)) { // Repair the matching by deleting the pairs that were pruned outside the constraint
                    matching.unMatch(var, matching.getMatchU(var));
                }
                valuesDynamic.refill();   // We refill the list with the recently removed elements, instead of recreating it from scratch
                int val = augmentingPath(var);
                if (val != fail) {
                    augmentMatching(val);
                }
                else {
                    // It is not possible to get a maximum matching --> the constraint can not be satisfied
                    timeMatchingNano += System.nanoTime() - timeStart;
                    timeTotalNano += System.nanoTime() - timeStart;
                    return false;
                }
            }
        }
        valuesDynamic.refill(); // valuesDynamic is a global variable used in the whole filtering procedure, so we must refill it

        timeMatchingNano += System.nanoTime() - timeStart;
        timeTotalNano += System.nanoTime() - timeStart;
        return true;
    }


    private void augmentMatching(int root) { // By knowing the parent of each value in the BFS tree and the current match of the variables, we can retrieve the augmenting path from the last value in the path
        int v = root;
        while (matching.inMatchingU(getParent(v))) {
            int v_next = matching.getMatchU(getParent(v));
            // We switch the edges of the matching on the augmenting path
            matching.unMatch(getParent(v), v_next);
            matching.setMatch(getParent(v), v);
            v = v_next;
        }
        // The last variable we ecounter is the one we performed the BFS from, and is then unmatched
        matching.setMatch(getParent(v), v);
    }

    private int augmentingPath(int root) {
        queueBFS.clear();   // We use the same queue as in the previous iteration, so we need to clear it (done in O(1) time)
        queueBFS.addLast(root);
        while (!queueBFS.isEmpty()) {
            int var = queueBFS.pollLast();
            int val;
            if (choiceHyBFS(var)) {    // If var has a small domain, we iterate over its domain and explore the unvisited values
                int ub = vars[var].getUB();
                for (val = vars[var].getLB(); val <= ub; val = vars[var].nextValue(val)) {
                    if (valuesDynamic.isPresent(val) && stop(var, val)) {return val;}
                }
            } else {    // If var has a large domain, we iterate over the unvisited values and explore the ones that are in the domain of var
                val = valuesDynamic.getSource();
                while (valuesDynamic.hasNext(val)) {
                    val = valuesDynamic.getNext(val);
                    if (vars[var].contains(val) && stop(var, val)) {return val;}
                }
            }
        }
        return fail;
    }

    /** 
     * This function decides wether the domain is considered as small or large
     */ 
    private boolean choiceHyBFS(int var) {
        switch (mode) {
            case CLASSIC:
                return true;
            case COMPLEMENT:
                return false;
            case HYBRID:
                return vars[var].getDomainSize() < valuesDynamic.getSize();
            case TUNED:
                return vars[var].getDomainSize() < valuesDynamic.getSize();
            default:
                return true;
        }
    
    }

    private boolean stop(int var, int val) {
        setParent(var, val);
        if (matching.inMatchingV(val)) { // If the value is already matched, we continue the exploration from its matched variable
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

    private void filter() throws ContradictionException {
        long timeStart = System.nanoTime();
        this.numVisit = 1;
        this.atLeastTwo = false;
        int var = variablesDynamic.getSource();
        while(variablesDynamic.hasNext(var)) {
            var = variablesDynamic.getNext(var);
            if (valuesDynamic.isPresent(matching.getMatchU(var))) {
                hyDFS(var);
            }
        }
        if (atLeastTwo) {prune(t_node);} // If there is only one SCC, no pruning is possible so there is no point to call the Prune procedure.

        // The remaining unvisited values are present in the domain of no variables, thus we can remove them from the universe of values for the next call to the propagator
        int val = valuesDynamic.getSource();
        while (valuesDynamic.hasNext(val)) {
            val = valuesDynamic.getNext(val);
            toRemoveFromValueUniverse.addLast(val);
        }

        // Ensure we properly reinitialise the structures for the next call to the propagator
        valuesDynamic.refill();
        SCC.clear();
        complementSCC.refill();
        for (int index = 0; index < tarjanStack.size(); index++) {
            inStack[tarjanStack.get(index)] = false;
        }
        tarjanStack.clear();

        // Time monitoring
        timeSCCNano += System.nanoTime() - timeStart;
        timeTotalNano += System.nanoTime() - timeStart;
    }

    private void hyDFS(int var) throws ContradictionException {
        pre[matching.getMatchU(var)] = numVisit;
        low[matching.getMatchU(var)] = numVisit;
        numVisit++;
        valuesDynamic.remove(matching.getMatchU(var));
        tarjanStack.addLast(matching.getMatchU(var));
        inStack[matching.getMatchU(var)] = true;
        int val;

        if(choiceHyDFS(var)) {   // If var has a small domain then iterate over the domain
            int ub = vars[var].getUB();
            for (val = vars[var].getLB(); val <= ub; val = vars[var].nextValue(val)) {
                // ======================= Case 1 : explore a non-visited value =======================
                if (val != matching.getMatchU(var) && valuesDynamic.isPresent(val)) {process(var, val);}

                // ======================= Case 2 :  update M(var).low via an already visited and unassigned value =======================
                else if (val != matching.getMatchU(var) && inStack[val]) {low[matching.getMatchU(var)] = min(low[matching.getMatchU(var)], pre[val]);} // M(var).low = min(M(var).low, val.pre)
            }

        } else { // If var has a large domain then iterate over the unvisited values and over the values in Tarjan's stack

            // ======================= Step 1: explore the non-visited values =======================
            int pointerVar = valuesDynamic.getSource();
            while (valuesDynamic.hasNext(pointerVar)) { // Explore all the branches going out of var in the DFS tree
                pointerVar = valuesDynamic.trackLeft(pointerVar); // Go back in the list of unvisited values
                while(valuesDynamic.hasNext(pointerVar) && vars[var].contains(valuesDynamic.getNext(pointerVar))) { // Go to the last consecutive non-domain value
                    pointerVar = valuesDynamic.getNext(pointerVar);
                }
                if (valuesDynamic.hasNext(pointerVar)) {process(var, valuesDynamic.getNext(pointerVar));} // If we did not reach the end of the list of unvisited values, the next value is a domain value
            }

            // ======================= Step 2 : update M(var).low thanks to the most ancient visited and unassigned value =======================
            for (int index = 0; index < tarjanStack.size(); index++) { // Iterate over tarjan's stack from the bottom until you find a value in the domain of var, or until it is not possible to decrease M(var).low
                val = tarjanStack.get(index);
                if (vars[var].contains(val) || pre[val] >= low[matching.getMatchU(var)]) {
                    low[matching.getMatchU(var)] = min(low[matching.getMatchU(var)], pre[val]); // M(var).low = min(M(var).low, val.pre)
                    break;
                }
            }
        }
        if (pre[matching.getMatchU(var)] == low[matching.getMatchU(var)]) {prune(matching.getMatchU(var));} // If M(var) is the root of its SCC, then we run the pruning procedure

    }

    /** 
     * This function decides wether the domain is considered as small or large
     */ 
    private boolean choiceHyDFS(int var) {
        switch (mode) {
            case CLASSIC:
                return true;
            case COMPLEMENT:
                return false;
            case HYBRID:
                return vars[var].getDomainSize() < valuesDynamic.getSize();
            case TUNED:
                return vars[var].getDomainSize() < Math.sqrt(valuesDynamic.getSize());
            default:
                return true;
        }
    }

    private void process(int var, int val) throws ContradictionException {
        if (matching.inMatchingV(val)) {    // If the value is already matched, we continue the exploration from its matched variable
            hyDFS(matching.getMatchV(val));
            low[matching.getMatchU(var)] = min(low[matching.getMatchU(var)], low[val]); // M(var).low = min(M(var).low, val.low)
        } else {    // If the value is not matched it leads to the artificial node t_node, so we artificially explore it
            pre[val] = numVisit;
            low[val] = 0;
            numVisit++;
            low[matching.getMatchU(var)] = 0; // M(var).low = 0
            valuesDynamic.remove(val);
            tarjanStack.addLast(val);
            inStack[val] = true;
        }
    }

    private void prune(int root) throws ContradictionException {
        long timeStart = System.nanoTime();
        atLeastTwo = true;
        SCC.clear();
        complementSCC.refill();
        int var;
        int val;

        /**
         * ======================= Step 1 : Get all the values of the discovered SCC and construct the complement =======================
        */
            do {
                val = tarjanStack.pollLast();
                if (val != t_node) {
                    inStack[val] = false;
                    SCC.addLast(val);
                    complementSCC.remove(val);
                }
            } while (val != root);

        /**
         * ======================= Step 2 : For each variable of the SCC, we prune their domain values that are not in the SCC =======================
        */

        // Particular case where we can force the instanciation of the matched variable to the unique value of the SCC, and remove them from the universes of variables and values
        if (SCC.size() == 1) {
            // The unique value of the SCC is necessarily matched, otherwise it would have been in the same SCC as t_node
            val = SCC.get(0);
            var = matching.getMatchV(val);
            
            vars[var].instantiateTo(val, aCause); //TODO: will the pruning also be managed by the Forward Checking ? Because it is not necessary, everything is done in the filtering procedure

            toRemoveFromVariableUniverse.addLast(var);
            toRemoveFromValueUniverse.addLast(val);
        }

        for (int index = 0; index < SCC.size(); index++) {
            val = SCC.get(index);
            if (matching.inMatchingV(val)) {
                var = matching.getMatchV(val);
                if (choicePrune(var)) {  // If var has a small domain then iterate over the domain and prune the values that are in the complement
                    int ub = vars[var].getUB();
                    for (int domainValue = vars[var].getLB(); domainValue <= ub; domainValue = vars[var].nextValue(domainValue)) {
                        if (complementSCC.isPresent(domainValue)) {
                            // Prune the pair (var, domainValue)
                            vars[var].removeValue(domainValue, aCause);
                            pruned = true;
                        }
                    }

                } else {    // If var has a large domain then iterate over the values in the complement and prune the ones that are in the domain of var
                    int complementValue = complementSCC.getSource();
                    while(complementSCC.hasNext(complementValue)) {
                        complementValue = complementSCC.getNext(complementValue);
                        if (vars[var].contains(complementValue)) {
                            // Prune the pair (var, complementValue)
                            vars[var].removeValue(complementValue, aCause);
                            pruned = true;
                        }
                    }
                }
            }
        }
        timePruneNano += System.nanoTime() - timeStart;
        timeSCCNano -= System.nanoTime() - timeStart;
    }

    /** 
     * This function decides wether the domain is considered as small or large
     */ 
    private boolean choicePrune(int var) {
        switch (mode) {
            case CLASSIC:
                return true;
            case COMPLEMENT:
                return false;
            case HYBRID:
                return vars[var].getDomainSize() < complementSCC.getSize();
            case TUNED:
                return vars[var].getDomainSize() < complementSCC.getSize();
            default:
                return true;
        }
    }

    //***********************************************************************************
    // Dynamic Structures and Backtrack Management
    //      In this section we manage the decrementality and backtrack of the universe of variables variablesDynamic
    //      and the universes of values valuesDynamic and complementSCC
    //      The backtrack operations are managed within the removeFromUniverse method of the TrackingList 
    //***********************************************************************************


    private void updateUniverseOpening(){ // Here we detect the recently instanciated variables and remove them and their values from the universes of variables and values
        IEnvironment env = model.getEnvironment();
        int var  = variablesDynamic.getSource();
        while (variablesDynamic.hasNext(var)) {
            var = variablesDynamic.getNext(var);
            if (vars[var].isInstantiated()) {
                variablesDynamic.removeFromUniverse(var, env);
                if (valuesDynamic.isPresent(vars[var].getValue())) {
                    valuesDynamic.removeFromUniverse(vars[var].getValue(), env);
                    complementSCC.removeFromUniverse(vars[var].getValue(), env);
                }
            }
        }
    }

    private void updateUniverseEnding() { // Here we remove from the universes the variables and values detected during the filtering procedure
        IEnvironment env = model.getEnvironment();

        // Manage the universe of variables
        variablesDynamic.refill();
        for (int index = 0; index < toRemoveFromVariableUniverse.size(); index++) {
            int var = toRemoveFromVariableUniverse.get(index);
            variablesDynamic.removeFromUniverse(var, env);
        }
        toRemoveFromVariableUniverse.clear();

        // Manage the universes of values
        valuesDynamic.refill();
        complementSCC.refill();
        for (int index = 0; index < toRemoveFromValueUniverse.size(); index++) {
            valuesDynamic.removeFromUniverse(toRemoveFromValueUniverse.get(index), env);
            complementSCC.removeFromUniverse(toRemoveFromValueUniverse.get(index), env);
        }
        toRemoveFromValueUniverse.clear();
    }

    //***********************************************************************************
    // Getter for Time Monitoring
    //***********************************************************************************


    public long getTimeMatchingNanoSeconds() {return timeMatchingNano;}

    public long getTimeSCCNanoSeconds() {return timeSCCNano;}

    public long getTimePruneNanoSeconds() {return timePruneNano;}

    public long getTimeTotalNanoSeconds() {return timeTotalNano;}
}
