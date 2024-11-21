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
    private int[] queueBFS; // Queue of the variables to explore during the BFS
    private int headBFS;
    private int tailBFS;
    private final int t_node; // Symbol representing the artificial sink node of the Residual Graph
    private TrackingList complementSCC; // List of the values that are not in the discovered SCC
    private int[] tarjanStack;  // The stack used in Tarjan's algorithm to find the SCCs
    private int topTarjan;
    private boolean[] inStack; // Boolean array informing the presence of a value in the stack
    private int[] pre; // Pre visit order of the values
    private int[] low; // Low point of the values
    private int numVisit; // Current visit number of the DFS in Tarjan's algorithm
    private boolean atLeastTwo; // True if there are potentially two SCCs
    private String mode; // Indicating the mode in which we are using the procedure (Classic, Complement, Hybrid or Tuned)
    private boolean pruned; // True if some variable-value pairs were pruned
//    private long timeMatchingNano;
//    private long timeSCCNano;
//    private long timePruneNano;
//    private long timeTotalNano;

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
        this.queueBFS = new int[R];
        this.headBFS = 0;
        this.tailBFS = 0;

        // Specific data structures for computing the strongly connected components
        this.t_node = minValue - 1;
        this.complementSCC = new TrackingList(minValue, maxValue);
        refineUniverse(complementSCC);
        this.tarjanStack = new int[D];
        this.topTarjan = 0;
        this.inStack = new boolean[D];
        this.pre = new int[D];
        this.low = new int[D];
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
//        System.out.println("============================================================");//DEBUG
//        System.out.println("------------------------ Begin Node ------------------------");//DEBUG

        this.pruned = false;

        updateDynamicStructuresOpening();
//        System.out.println("Universes of variables: " + variablesDynamic);//DEBUG
//        System.out.println("Universes of values: " + valuesDynamic + " and  " + complementSCC);//DEBUG
//        System.out.println("Maximum matching before update:");//DEBUG
//        System.out.println(matching);//DEBUG
        boolean foundMatching = findMaximumMatching();
        //System.out.println("Found a maximum matching: " + foundMatching);//DEBUG
        if(foundMatching) {
//            System.out.println("Maximum matching after update:");//DEBUG
//            System.out.println(matching);//DEBUG
//            System.out.println("The matching is correct: " + matching.isValid());//DEBUG
//            System.out.println("The matching is maximum: " + matching.isMaximum());//DEBUG
            filter();
            updateDynamicStructuresEnding();
        }

        //DEBUG
//        if (pruned) {
//            System.out.println("Current instanciation:");
//            for (IntVar var : vars) {
//                if(var.isInstantiated()) {
//                    System.out.println(var.getName() + " --> " + var.getValue());
//                }
//            }
//        }


//        System.out.println("------------------------- End Node -------------------------");//DEBUG
//        System.out.println("============================================================");//DEBUG

        else {
//            System.out.println(matching);//DEBUG
            vars[0].instantiateTo(vars[0].getLB() - 1, aCause);
        }

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
            if (!matching.inMatchingU(var)) {
                valuesDynamic.refill();   // We refill the list with the recently removed elements, instead of recreating it from scratch
                int val = augmentingPath(var);
                if (val != fail) {
                    augmentMatching(val);
                }
                else {
                    // It is not possible to get a maximum matching --> the constraint can not be satisfied

                    valuesDynamic.refill(); // valuesDynamic is a backtrackable TrackingList, we must refill it to avoid breaking its structure during the backtrack 

//                    timeMatchingNano += System.nanoTime() - timeStart;
//                    timeTotalNano += System.nanoTime() - timeStart;
                    return false;
                }
            }
        }
        valuesDynamic.refill(); // valuesDynamic is a global variable used in the whole filtering procedure, so we must refill it

//        timeMatchingNano += System.nanoTime() - timeStart;
//        timeTotalNano += System.nanoTime() - timeStart;
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
        // The last variable we encounter is the one we performed the BFS from, and is then unmatched
        matching.setMatch(getParent(v), v);
    }

    private int augmentingPath(int root) {
        headBFS = 0;
        tailBFS = 1;
        queueBFS[0] = root;
        while (headBFS != tailBFS) {
            int var = queueBFS[headBFS];
            headBFS++;
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

    private boolean stop(int var, int val) {
        setParent(var, val);
        if (matching.inMatchingV(val)) { // If the value is already matched, we continue the exploration from its matched variable
            //System.out.println("current list: " + valuesDynamic + ", value to remove: " + val + ", is it present ? " + valuesDynamic.isPresent(val));//DEBUG
            //System.out.println("Left element of the sink: " + valuesDynamic.getPrevious(valuesDynamic.getSink()));//DEBUG
            valuesDynamic.remove(val);
            queueBFS[tailBFS] = matching.getMatchV(val);
            tailBFS++;
            return false;
        } else {return true;} // If the value is not matched, we can stop the exploration because we found an augmenting path
    }

    //***********************************************************************************
    // SCC + PRUNING
    //***********************************************************************************

    private void filter() throws ContradictionException {
        long timeStart = System.nanoTime();
        this.numVisit = 1;
        this.atLeastTwo = false;
        int var = variablesDynamic.getSource();

        //System.out.println("Check the list of unvisited values before the filtering: " + valuesDynamic);//DEBUG

        while(variablesDynamic.hasNext(var)) {
            var = variablesDynamic.getNext(var);
            if (valuesDynamic.isPresent(matching.getMatchU(var))) {
                hyDFS(var);
            }
        }
        if (atLeastTwo && topTarjan != 0) {prune(t_node);} // If there is only one SCC, no pruning is possible so there is no point to call the Prune procedure.

        // Clear Tarjan's stack in case the prune method was not called above
        while(topTarjan != 0) {
            declareInStack(tarjanStack[topTarjan - 1], false);
            topTarjan--;
        }
        
        //System.out.println("Check the list of unvisited values after the filtering: " + valuesDynamic);//DEBUG

        // Time monitoring
//        timeSCCNano += System.nanoTime() - timeStart;
//        timeTotalNano += System.nanoTime() - timeStart;
    }

    private void hyDFS(int var) throws ContradictionException {
        setPre(matching.getMatchU(var), numVisit);
        setLow(matching.getMatchU(var), numVisit);
        numVisit++;
        valuesDynamic.remove(matching.getMatchU(var));
        tarjanStack[topTarjan] = matching.getMatchU(var);
        topTarjan++;
        declareInStack(matching.getMatchU(var), true);
        int val;

        if(choiceHyDFS(var)) {   // If var has a small domain then iterate over the domain
            int ub = vars[var].getUB();
            for (val = vars[var].getLB(); val <= ub; val = vars[var].nextValue(val)) {
                // ======================= Case 1 : explore a non-visited value =======================
                if (val != matching.getMatchU(var) && valuesDynamic.isPresent(val)) {process(var, val);}

                // ======================= Case 2 :  update M(var).low via an already visited and unassigned value =======================
                else if (val != matching.getMatchU(var) && isInStack(val)) {setLow(matching.getMatchU(var), min(getLow(matching.getMatchU(var)), getPre(val)));} // M(var).low = min(M(var).low, val.pre)
            }

        } else { // If var has a large domain then iterate over the unvisited values and over the values in Tarjan's stack

            // ======================= Step 1: explore the non-visited values =======================
//            int pointerVar = valuesDynamic.getSource();
            int pointerVar = valuesDynamic.getPrevious(vars[var].getLB()); //Optimisation
            int var_ub = vars[var].getUB(); //Optimisation
            //System.out.println("Ensure the source is not in the domain of var: " + vars[var].contains(pointerVar));//DEBUG
            while (valuesDynamic.hasNext(pointerVar) && pointerVar < var_ub) { // Explore all the branches going out of var in the DFS tree
                pointerVar = valuesDynamic.trackLeft(pointerVar); // Go back in the list of unvisited values
                while(valuesDynamic.hasNext(pointerVar) && pointerVar < var_ub && !vars[var].contains(valuesDynamic.getNext(pointerVar))) { // Go to the last consecutive non-domain value
                    pointerVar = valuesDynamic.getNext(pointerVar);
                }
                //System.out.println("Ensure pointerVar is not in the domain of var: " + vars[var].contains(pointerVar));//DEBUG
                if (valuesDynamic.hasNext(pointerVar) && pointerVar < var_ub) {// If we did not reach the end of the list of unvisited values, the next value is a domain value
                    process(var, valuesDynamic.getNext(pointerVar));
                    var_ub = vars[var].getUB(); //Optimisation
                }
            }

            // ======================= Step 2 : update M(var).low thanks to the most ancient visited and unassigned value =======================
            for (int index = 0; index < topTarjan; index++) { // Iterate over tarjan's stack from the bottom until you find a value in the domain of var, or until it is not possible to decrease M(var).low
                val = tarjanStack[index];
                if (vars[var].contains(val) || getPre(val) >= getLow(matching.getMatchU(var))) {
                    setLow(matching.getMatchU(var), min(getLow(matching.getMatchU(var)), getPre(val))); // M(var).low = min(M(var).low, val.pre)
                    break;
                }
            }
        }
        if (getPre(matching.getMatchU(var)) == getLow(matching.getMatchU(var))) {   // If M(var) is the root of its SCC, then we run the pruning procedure
            //System.out.println("SCC root: " + matching.getMatchU(var));//DEBUG
            prune(matching.getMatchU(var));
        }

    }


    private void process(int var, int val) throws ContradictionException {
        if (matching.inMatchingV(val)) {    // If the value is already matched, we continue the exploration from its matched variable
            hyDFS(matching.getMatchV(val));
            setLow(matching.getMatchU(var), min(getLow(matching.getMatchU(var)), getLow(val))); // M(var).low = min(M(var).low, val.low)
        } else {    // If the value is not matched it leads to the artificial node t_node, so we artificially explore it
            setPre(val, numVisit);
            setLow(val, 0);
            numVisit++;
            setLow(matching.getMatchU(var), 0); // M(var).low = 0
            valuesDynamic.remove(val);
            tarjanStack[topTarjan] = val;
            topTarjan++;
            declareInStack(val, true);
        }
    }

    private void prune(int root) throws ContradictionException {
        long timeStart = System.nanoTime();
        atLeastTwo = true;
        complementSCC.refill();
        int var;
        int val;

        /**
         * ======================= Step 1 : Get all the values of the discovered SCC and construct the complement =======================
        */

        int rootIndex = topTarjan;
        do {
            rootIndex--;
            val = tarjanStack[rootIndex];
            declareInStack(val, false);
            complementSCC.remove(val);
        } while (val != root && rootIndex != 0);

        /**
         * ======================= Step 2 : For each variable of the SCC, we prune their domain values that are not in the SCC =======================
        */

        // Particular case where we can force the instanciation of the matched variable to the unique value of the SCC, and remove them from the universes of variables and values
        if (topTarjan - rootIndex == 1) {
            // The unique value of the SCC is necessarily matched, otherwise it would have been in the same SCC as t_node
            val = tarjanStack[rootIndex];
            var = matching.getMatchV(val);

//            System.out.println("Pair instanciated: x_" + var + " -- " + val);//DEBUG
            vars[var].instantiateTo(val, aCause); //TODO: will the pruning also be managed by the Forward Checking ? Because it is not necessary, everything is done in the filtering procedure
            if (vars[var].getDomainSize() > 1) {this.pruned = true;} // TODO not clean
        }

        for (int index = rootIndex; index < topTarjan; index++) {
            val = tarjanStack[index];
            if (matching.inMatchingV(val)) {
                var = matching.getMatchV(val);
                if (choicePrune(var)) {  // If var has a small domain then iterate over the domain and prune the values that are in the complement
                    int ub = vars[var].getUB();
                    for (int domainValue = vars[var].getLB(); domainValue <= ub; domainValue = vars[var].nextValue(domainValue)) {
                        if (complementSCC.isPresent(domainValue)) {
                            // Prune the pair (var, domainValue)
                            vars[var].removeValue(domainValue, aCause);
                            pruned = true;
//                            System.out.println("Pair pruned: x_" + var + " -- " + domainValue);//DEBUG
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
//                            System.out.println("Pair pruned: x_" + var + " -- " + complementValue);//DEBUG
                        }
                    }
                }
            }
        }

        topTarjan = rootIndex; // Remove the discovered SCC from Tarjan's stack

//        timePruneNano += System.nanoTime() - timeStart;
//        timeSCCNano -= System.nanoTime() - timeStart;
    }

    //***********************************************************************************
    // Dynamic Structures and Backtrack Management
    //      In this section we manage the decrementality and backtrack of the universe of variables variablesDynamic
    //      and the universes of values valuesDynamic and complementSCC
    //      The backtrack operations are managed within the removeFromUniverse method of the TrackingList 
    //***********************************************************************************


    private void updateDynamicStructuresOpening(){
        IEnvironment env = model.getEnvironment();
        int var  = variablesDynamic.getSource();
        while (variablesDynamic.hasNext(var)) {
            var = variablesDynamic.getNext(var);
            if (vars[var].isInstantiated()) {
                variablesDynamic.removeFromUniverse(var, env); // The instanciated variables are removed from the universe

                if (valuesDynamic.isPresent(vars[var].getValue())) { // The values of the instanciated variables are removed from the universe
                    valuesDynamic.removeFromUniverse(vars[var].getValue(), env);
                    complementSCC.removeFromUniverse(vars[var].getValue(), env);
                }

                if (matching.inMatchingU(var)) { // Unmatch the instanciated variable from its current matched value
                    matching.unMatch(var, matching.getMatchU(var));
                }
                if (matching.inMatchingV(vars[var].getValue())) { // Unmatch the value of the instanciated variable from its current matched variable
                    matching.unMatch(matching.getMatchV(vars[var].getValue()), vars[var].getValue());
                }
                matching.setMatch(var, vars[var].getValue()); //Match the instanciated variable and its value together
            } else if (matching.inMatchingU(var) && !vars[var].contains(matching.getMatchU(var))) { // Unmatch a variable from its matched value if it does not belong to the domain anymore
                matching.unMatch(var, matching.getMatchU(var));
            }
        }
    }

    private void updateDynamicStructuresEnding() {
        IEnvironment env = model.getEnvironment();

        // The remaining unvisited values are present in the domain of no variables, thus we can remove them from the universe of values for the next call to the propagator
        int val = valuesDynamic.getSource();
        while (valuesDynamic.hasNext(val)) {
            val = valuesDynamic.getNext(val);
            // Here we reuse tarjan's stack instead of creating a new data structure
            tarjanStack[topTarjan] = val;
            topTarjan++;
        }

        valuesDynamic.refill();
        complementSCC.refill();

        while (topTarjan != 0) {
            valuesDynamic.removeFromUniverse(tarjanStack[topTarjan - 1], env);
            complementSCC.removeFromUniverse(tarjanStack[topTarjan - 1], env);
            topTarjan--;
        }
    }

    //***********************************************************************************
    // Choice Functions for the Search Algorithms
    //      These functions decide whether the domain is considered as small or large
    //***********************************************************************************


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
    // Getter and Setter for Internal Data Structures
    //***********************************************************************************

    private int getParent(int val) {
        return parentBFS[val - minValue];
    }

    private void setParent(int var, int val) {
        parentBFS[val - minValue] = var;
    }

    private int min(int a, int b) {return a < b ? a : b;}

    private int getPre(int val) {
        return pre[val - minValue];
    }

    private void setPre(int val, int order) {
        pre[val - minValue] = order;
    }

    private int getLow(int val) {
        return low[val - minValue];
    }

    private void setLow(int val, int point) {
        low[val - minValue] = point;
    }

    private boolean isInStack(int val) {
        return inStack[val - minValue];
    }

    private void declareInStack(int val, boolean present) {
        inStack[val - minValue] = present;
    }



    //***********************************************************************************
    // Getter for Time Monitoring
    //***********************************************************************************


//    public long getTimeMatchingNanoSeconds() {return timeMatchingNano;}
//
//    public long getTimeSCCNanoSeconds() {return timeSCCNano;}
//
//    public long getTimePruneNanoSeconds() {return timePruneNano;}
//
//    public long getTimeTotalNanoSeconds() {return timeTotalNano;}
}
