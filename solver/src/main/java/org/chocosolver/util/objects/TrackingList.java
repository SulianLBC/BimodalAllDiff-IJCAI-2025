package org.chocosolver.util.objects;

/**
 * The Tracking list is similar to a doubly linked list
 * for which each element has a predecessor and a successor, implemented by arrays
 * An artifical source node is added at the beginning of the list
 * An artificial sink node is added at the end of the list
 * The tracking list allows to call the functions TrackPrev and TrackNext that are specific to this data structure
 * Furthermore, it is possible to distinguish the elements removed/reinserted to the list and to the universe, in case the tracking list is used in a dynamic environment
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
    private final int sourceIndex;
    private final int sinkIndex;
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
        this.sourceIndex = -1;
        this.sinkIndex = maxSize;
        this.successor = new int[maxSize+1];
        this.predecessor = new int[maxSize+1];
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
        if (isSourceIndex(i) || isSinkIndex(i)) {return true;}
        return getNextIndex(getPreviousIndex(i)) == i;
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
        if (isSourceIndex(i) || isSinkIndex(i)) {throw new Error("Error: You can not remove the source nor the sink");}
        else if (!isPresentIndex(i)) {throw new Error("Error: You can not remove an element that is not present in the in-list");} 
        else {
        setNextIndex(getPreviousIndex(i), getNextIndex(i));
        setPreviousIndex(getNextIndex(i), getPreviousIndex(i));
        removed.addLast(i);
        size--;
        }
    }

    /**
     * Removes an element from the universe
     */
    public void removeFromUniverse(int e) {
        int i = convertToIndex(e);
        if (isSourceIndex(i) || isSinkIndex(i)) {throw new Error("Error: You can not remove from the universe the source nor the sink");}
        else if (!isPresentIndex(i)) {throw new Error("Error: You can not remove from the universe an element that is not present in the in-list");}
        else if (!removed.isEmpty()) {throw new Error("Error: You can not remove an element from the universe if some elements of the universe are not present in the in-list");}
        else {
        setNextIndex(getPreviousIndex(i), getNextIndex(i));
        setPreviousIndex(getNextIndex(i), getPreviousIndex(i));
        size--;
        universeSize--;
        removedUniverse.addLast(i);
        }
    }

    /**
     * Reinserts an element in the in-list
     * @param e // The element to reinsert 
     * @param universe // A boolean indicating if the element is reinserted in the universe
     */
    public void reinsert(int e, boolean universe) {
        int i = convertToIndex(e);
        setNextIndex(getPreviousIndex(i), i);
        setPreviousIndex(getNextIndex(i), i);
        size++;
        if(universe) {universeSize++;}
    }

    /**
     * Refills the tracking list with all the elements of the universe
     */
    public void refill() {
        while (!removed.isEmpty()) {
            reinsertIndex(removed.pollLast(), false);
        }
    }

    /**
     * Refills the universe of the trackig list as it was at the initialisation
     */
    public void refillUniverse() {
        if (!removed.isEmpty()) {throw new Error("Error: You must refill the in-list before the universe");}
        while (!removedUniverse.isEmpty()) {
            reinsertIndex(removedUniverse.pollLast(), true);
        }
    }

    /**
     * Special elementary function of the tracking list
     * Returns the first element in the in-list from a given element and toward the predecessors
     */
    public int trackLeft(int e) {
            int i = convertToIndex(e);
            while (!isPresentIndex(i)) {
                i = getPreviousIndex(i);
            }
            return convertToValue(i);
    }

    /**
     * Special elementary function of the tracking list
     * Returns the first element in the in-list from a given element and toward the successors
     */
    public int trackRight(int e) {
        int i = convertToIndex(e);
        while (!isPresentIndex(i)) {
            i = getNextIndex(i);
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
    // The main objective of these functions if to perform several queries on the tracking list without having to convert the indices and elements every time
    //////////////////////////////////////////

    private int convertToIndex(int e) {return e - minValue;}

    private int convertToValue(int i) {return i + minValue;}

    private int getNextIndex(int i) {return successor[i+1];}

    private int getPreviousIndex(int i) {return predecessor[i];}

    private void setNextIndex(int i, int x) {successor[i+1] = x;}

    private void setPreviousIndex(int i, int x) {predecessor[i] = x;}

    private void reinsertIndex(int i, boolean universe) {
        setNextIndex(getPreviousIndex(i), i);
        setPreviousIndex(getNextIndex(i), i);
        size++;
        if(universe) {universeSize++;}
    }

    private boolean isSourceIndex(int i){return i == sourceIndex;}

    private boolean isSinkIndex(int i){return i == sinkIndex;}

    private boolean isPresentIndex(int i) {
        if (i == sourceIndex || i == sinkIndex) {return true;}
        return getNextIndex(getPreviousIndex(i)) == i;
    }

}
