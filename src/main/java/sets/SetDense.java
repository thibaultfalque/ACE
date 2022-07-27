/*
 * This file is part of the constraint solver ACE (AbsCon Essence). 
 *
 * Copyright (c) 2021. All rights reserved.
 * Christophe Lecoutre, CRIL, Univ. Artois and CNRS. 
 * 
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package sets;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A dense set is basically composed of an array (of integers) and a limit: at any time, it contains the elements
 * (typically, indexes of values) in the array at indexes ranging from 0 to the limit (included).
 * 
 * @author Christophe Lecoutre
 */
public class SetDense { // implements Iterable<Integer> {

	public static final int UNINITIALIZED = -2;

	/**
	 * The array containing the values. The current set contains values at indexes in range from 0 to limit (included).
	 */
	public int[] dense;

	/**
	 * The limit for the current set.
	 */
	public int limit;

	/**
	 * Builds a dense set from the values in the specified array. These values are those that can be contained at any
	 * time in the set. Most of the time, these values are exactly the indexes 0, 1, 2, ... and the dense set is then
	 * said to be simple. Initially, the set is full or empty depending on the value of the specified boolean.
	 * 
	 * @param dense
	 *            the values that can be contained in the set
	 * @param initiallyFull
	 *            if true, the set is initially full, empty otherwise
	 */
	public SetDense(int[] dense, boolean initiallyFull) {
		assert !initiallyFull || IntStream.range(0, dense.length).allMatch(i -> IntStream.range(i + 1, dense.length).allMatch(j -> dense[i] != dense[j]));
		this.dense = dense;
		this.limit = initiallyFull ? dense.length - 1 : -1;
	}

	/**
	 * Builds a dense set with the specified capacity. The dense set is simple, meaning that it is aimed at containing
	 * indexes 0, 1, 2, ... Initially, the set is full or empty depending on the value of the specified boolean. Note
	 * that we build a canonical dense set, i.e. a set with values 0, 1, 2 ... (even if this is only useful when
	 * initialyFull is true or a sparse set is built).
	 * 
	 * @param capacity
	 *            the capacity of the dense set
	 * @param initiallyFull
	 *            if true, the set is initially full, empty otherwise
	 */
	public SetDense(int capacity, boolean initiallyFull) {
		this(IntStream.range(0, capacity).toArray(), initiallyFull);
	}

	/**
	 * Builds a dense set with the specified capacity. The dense set is simple, meaning that it is aimed at containing
	 * indexes 0, 1, 2, ... Initially, the set is empty.
	 * 
	 * @param capacity
	 *            the capacity of the dense set
	 */
	public SetDense(int capacity) {
		this(capacity, false);
	}

	/**
	 * Increases the capacity of the set (ratio set to 2)
	 */
	public void increaseCapacity() {
		dense = IntStream.range(0, capacity() * 2).map(i -> i < dense.length ? dense[i] : i).toArray();
	}

	/**
	 * Returns the capacity (maximum number of values that it can contain) of the set
	 * 
	 * @return the capacity (maximum number of values) of the set
	 */
	public final int capacity() {
		return dense.length;
	}

	/**
	 * @return the current size of the set
	 */
	public final int size() {
		return limit + 1;
	}

	/**
	 * @return true if the set is empty
	 */
	public final boolean isEmpty() {
		return limit == -1;
	}

	/**
	 * @return true if the set is full
	 */
	public final boolean isFull() {
		return limit == dense.length - 1;
	}

	/**
	 * Clears the set (emptying it)
	 */
	public void clear() {
		limit = -1;
	}

	/**
	 * Fills up the set.
	 */
	public void fill() {
		limit = dense.length - 1;
	}

	/**
	 * @return the first value of the set
	 */
	public final int first() {
		assert limit >= 0;
		return dense[0];
	}

	/**
	 * @return the last value of the set
	 */
	public final int last() {
		return dense[limit];
	}

	private int mark = UNINITIALIZED;

	/**
	 * Records the current limit as a mark that can be restored later
	 */
	public final void setMark() {
		assert mark == UNINITIALIZED;
		mark = limit;
	}

	/**
	 * Restores the limit that was recorded as a mark earlier
	 */
	public final void restoreAtMark() {
		assert mark != UNINITIALIZED;
		limit = mark;
		mark = UNINITIALIZED;
	}

	/**
	 * Returns true if the set contains the specified element. BE CAREFUL: for a pure dense set (i.e., not a sparse
	 * set), this is not O(1)
	 * 
	 * @param a
	 *            an element (typically, index of value)
	 * @return true if the set contains the specified element
	 */
	public boolean contains(int a) {
		for (int i = limit; i >= 0; i--)
			if (dense[i] == a)
				return true;
		return false;
	}

	/**
	 * Adds the specified element. For a pure dense set (i.e., not a sparse set), the absence of the element is not
	 * tested, and so the element is always systematically added.
	 * 
	 * @param a
	 *            an element (typically, index of value)
	 * @return true if the element has been added
	 */
	public boolean add(int a) {
		assert !contains(a);
		dense[++limit] = a;
		return true;
	}

	/**
	 * Clears the set and add the specified element.
	 * 
	 * @param a
	 *            an element (typically, index of value)
	 */
	public final void resetTo(int a) {
		clear();
		add(a);
	}

	/**
	 * Clears the set and adds the elements from the specified dense set
	 * 
	 * @param set
	 *            a dense set
	 */
	public final void resetTo(SetDense set) {
		assert capacity() >= set.size();
		clear();
		for (int i = 0; i <= set.limit; i++)
			add(set.dense[i]);
	}

	/**
	 * Swaps the elements of the set at the two specified positions.
	 * 
	 * @param i
	 *            the position of the first element to be swapped
	 * @param j
	 *            the position of the second element to be swapped
	 */
	public void swapAtPositions(int i, int j) {
		int tmp = dense[i];
		dense[i] = dense[j];
		dense[j] = tmp;
	}

	/**
	 * Removes the element at the specified position. Technically, this element is swapped with the last one, before
	 * decrementing the limit of the set.
	 * 
	 * @param i
	 *            the position of the element to be removed
	 * @return the element that has been removed
	 */
	public int removeAtPosition(int i) {
		assert 0 <= i && i <= limit;
		int a = dense[i];
		dense[i] = dense[limit];
		dense[limit] = a; // not always necessary (but this is just one instruction)
		limit--;
		return a;
	}

	/**
	 * Removed the first element of the set. Technically, this element is swapped with the last one, before decrementing
	 * the limit of the set.
	 * 
	 * @return the element that has been removed
	 */
	public final int shift() {
		return removeAtPosition(0);
	}

	/**
	 * Removes the last element of the set
	 * 
	 * @return the element that has been removed
	 */
	public final int pop() {
		assert limit >= 0;
		limit--;
		return dense[limit + 1];
	}

	@Override
	public String toString() {
		return "dense={" + IntStream.range(0, size()).mapToObj(i -> dense[i] + "").collect(Collectors.joining(",")) + "}";
	}

	// class SimpleIteration implements Iterator<Integer> {
	//
	// private int cursor = -1;
	//
	// @Override
	// public boolean hasNext() {
	// return cursor >= 0;
	// }
	//
	// @Override
	// public Integer next() {
	// return dense[cursor--];
	// }
	//
	// @Override
	// public void remove() {
	// removeElementAtPosition(cursor + 1);// +1 because has been decremented by next()
	// }
	// }
	//
	// private SimpleIteration iteration = new SimpleIteration();
	//
	// @Override
	// public Iterator<Integer> iterator() {
	// control(iteration.cursor == -1, () -> "Simple (not nested) Iteration can only be used ");
	// iteration.cursor = limit;
	// return iteration;
	// }

}
