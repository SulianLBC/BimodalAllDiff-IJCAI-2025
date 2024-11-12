package org.chocosolver.util.objects;

import org.chocosolver.memory.IEnvironment;

/**
 * The Tracking list is similar to a doubly linked list
 * for which each element has a predecessor and a successor, implemented by arrays
 * An artifical source node is added at the beginning of the list
 * An artificial sink node is added at the end of the list
 * The tracking list allows to call the functions TrackPrev and TrackNext that are specific to this data structure
 * Furthermore, it is possible to distinguish the elements removed/reinserted to the list and to the universe in case the tracking list is used in a dynamic environment
 * @author Sulian Le Bozec-Chiffoleau
 * @since 17 Oct. 2024
 */

public class TrackingList {
    private int[] successor;
    private int[] predecessor;
    private IntCircularQueue removed;
    private IntCircularQueue removedUniverse;
    private final int source;
    private final int sink;
    private final int minValue;
    private final int maxValue;
    private final int maxSize;
    private int size;
    private int universeSize;

    public TrackingList(int a, int b) {
        this.minValue = a;
        this.maxValue = b;
        this.maxSize = maxValue - minValue + 1;
        this.source = minValue -1;
        this.sink = maxValue + 1;
        this.successor = new int[maxSize+1]; // Position 0 indicates the successor of the source node
        this.predecessor = new int[maxSize+1]; // Position maxSize indicates the predecessor of the sink node
        for (int i = 0; i < maxSize+1; i++) {
            this.successor[i] = i;
            this.predecessor[i] = i-1;
        }
        this.size = maxSize;
        this.universeSize = maxSize;
        this.removed = new IntCircularQueue(maxSize);
        this.removedUniverse = new IntCircularQueue(maxSize);
    }


    public int getSize() {return size;}

    public int getUniverseSize() {return universeSize;}

    /**
     * Returns the next element in the in-list
     * Starts by converting the element into its equivalent index and then gets the next index in the in-list
     * Ends by converting the found index into the actual element and returns it
     */
    public int getNext(int e) {return convertToValue(successor[convertToIndex(e) +1]);}

    /**
     * Returns the previous element in the in-list
     * Starts by converting the element into its equivalent index and then gets the previous index in the in-list
     * Ends by converting the found index into the actual element and returns it
     */
    public int getPrevious(int e) {return convertToValue(predecessor[convertToIndex(e)]);}

    /**
     * Returns the artificial source element
     */
    public int getSource(){return source;}

    /**
     * Returns the artificial sink element
     */
    public int getSink(){return sink;}

    public boolean isEmpty(){return size == 0;}

    /**
     * Returns true iff the element is present in the in-list
     */
    public boolean isPresent(int e) {
        int i = convertToIndex(e);
        if (e == source || e == sink) {return true;}
        return predecessor[successor[i + 1]] == i;
    }

    /**
     * Returns true iff the element is not the first element in the in-list
     */
    public boolean hasPrevious(int e) {
        return (getPrevious(e) != source);
    }

    /**
     * Returns true iff the element is not the last element in the in-list
     */
    public boolean hasNext(int e) {
        return (getNext(e) != sink);
    }

    /**
     * Removes an element from the in-list
     */
    public void remove(int e) {
        int i = convertToIndex(e);
        if (e == source || e == sink) {throw new Error("Error: You can not remove the source nor the sink");}
        else if (predecessor[successor[i + 1]] == i) {throw new Error("Error: You can not remove an element that is not present in the in-list");} 
        else {
            successor[predecessor[i] + 1] = successor[i + 1];
            predecessor[successor[i + 1]] = predecessor[i];
            removed.addLast(i);
            size--;
        }
    }

    /**
     * Removes an element from the universe
     */
    public void removeFromUniverse(int e) {
        int i = convertToIndex(e);
        if (e == source || e == sink) {throw new Error("Error: You can not remove from the universe the source nor the sink");}
        else if (predecessor[successor[i + 1]] == i) {throw new Error("Error: You can not remove from the universe an element that is not present in the in-list");}
        else if (!removed.isEmpty()) {throw new Error("Error: You can not remove an element from the universe if some elements of the universe are not present in the in-list");}
        else {
            successor[predecessor[i] + 1] = successor[i + 1];
            predecessor[successor[i + 1]] = predecessor[i];
            size--;
            universeSize--;
            removedUniverse.addLast(i);
        }
    }

    /**
     * This method is used when using the Tracking List as a backtrackable structure in Choco
     */
    public void removeFromUniverse(int e, IEnvironment env) {
        int i = convertToIndex(e);
        if (e == source || e == sink) {throw new Error("Error: You can not remove from the universe the source nor the sink");}
        else if (predecessor[successor[i + 1]] == i) {throw new Error("Error: You can not remove from the universe an element that is not present in the in-list");}
        else if (!removed.isEmpty()) {throw new Error("Error: You can not remove an element from the universe if some elements of the universe are not present in the in-list");}
        else {
            int pi = predecessor[i];
            int si = successor[i + 1];
            successor[pi + 1] = si;
            predecessor[si] = pi;
            size--;
            universeSize--;
            removedUniverse.addLast(i);

            // Here we store the operations to call during the backtrack
            env.save(() -> {
                successor[pi + 1] = i;
                predecessor[si] = i;
                size++;
                universeSize++;
                removedUniverse.pollLast();
            });
        }
    }

    /**
     * Reinserts an element in the in-list
     * @param e // The element to reinsert 
     * @param universe // A boolean indicating if the element is reinserted in the universe
     * Warning: Don't use this method outside the class unless you know what you are doing
     */
    public void reinsert(int e, boolean universe) {
        int i = convertToIndex(e);
        if (e == source || e == sink || predecessor[successor[i + 1]] == i) {throw new Error("Error: You can not reinsert an element that is already present in the in-list");}
        successor[predecessor[i] + 1] = i;
        predecessor[successor[i + 1]] = i;
        size++;
        if(universe) {universeSize++;}
    }

    /**
     * Reinserts in the in-list the last removed element
     */
    public void reinsertLastRemoved() {
        int i = removed.pollLast();
        successor[predecessor[i] + 1] = i;
        predecessor[successor[i + 1]] = i;
        size++;
    }

    /**
     * Reinserts in the universe the last removed element
     */
    public void reinsertLastRemovedUniverse() {
        if (!removed.isEmpty()) {throw new Error("Error: You must refill the in-list before the universe");}
        int i = removedUniverse.pollLast();
        successor[predecessor[i] + 1] = i;
        predecessor[successor[i + 1]] = i;
        size++;
        universeSize++;
    }

    /**
     * Refills the tracking list with all the elements of the universe
     */
    public void refill() {
        while (!removed.isEmpty()) {
            int i = removed.pollLast();
            successor[predecessor[i] + 1] = i;
            predecessor[successor[i + 1]] = i;
            size++;
        }
    }

    /**
     * Refills the universe of the trackig list as it was at the initialisation
     */
    public void refillUniverse() {
        if (!removed.isEmpty()) {throw new Error("Error: You must refill the in-list before the universe");}
        while (!removedUniverse.isEmpty()) {
            int i = removedUniverse.pollLast();
            successor[predecessor[i] + 1] = i;
            predecessor[successor[i + 1]] = i;
            size++;
            universeSize++;
        }
    }

    /**
     * Special elementary function of the tracking list
     * Returns the first element in the in-list from a given element and toward the predecessors
     */
    public int trackLeft(int e) {
            int i = convertToIndex(e);
            while (e != source && e != sink && predecessor[successor[i + 1]] != i) {
                i = predecessor[i];
            }
            return convertToValue(i);
    }

    /**
     * Special elementary function of the tracking list
     * Returns the first element in the in-list from a given element and toward the successors
     */
    public int trackRight(int e) {
        int i = convertToIndex(e);
            while (e != source && e != sink && predecessor[successor[i + 1]] != i) {
                i = successor[i + 1];
            }
            return convertToValue(i);
    }

    @Override
    public String toString() {
        int node = getSource();
        String printedString = ""; 
        while (hasNext(node)) {
            node = getNext(node);
            printedString += node + "  ";            
        }
        return printedString;
    }

    //////////////////////////////////////////
    // Private functions of the Tracking List
    //////////////////////////////////////////

    private int convertToIndex(int e) {return e - minValue;}

    private int convertToValue(int i) {return i + minValue;}

}
