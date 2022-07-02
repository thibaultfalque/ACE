/*
 * This file is part of the constraint solver ACE (AbsCon Essence). 
 *
 * Copyright (c) 2021. All rights reserved.
 * Christophe Lecoutre, CRIL, Univ. Artois and CNRS. 
 * 
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package variables;

import java.util.stream.IntStream;

import sets.SetLinkedBinary;

/**
 * A binary domain for a variable (entity of a constraint network).
 * 
 * @author Christophe Lecoutre
 */
public final class DomainBinary extends SetLinkedBinary implements Domain {

	private Variable x;

	private Integer typeIdentifier;

	private Boolean indexesMatchValues;

	private int firstValue, secondValue; // typically, 0 and 1

	@Override
	public final Variable var() {
		return x;
	}

	@Override
	public final int typeIdentifier() {
		return typeIdentifier != null ? typeIdentifier : (typeIdentifier = Domain.typeIdentifierFor(firstValue, secondValue));
	}

	@Override
	public final boolean indexesMatchValues() {
		return indexesMatchValues != null ? indexesMatchValues : (indexesMatchValues = IntStream.range(0, initSize()).noneMatch(a -> a != toVal(a)));
	}

	/**
	 * Builds a binary domain for the specified variable from the specified values
	 * 
	 * @param x
	 *            the variable to which the domain is associated
	 * @param firstValue
	 *            the first value of the domain
	 * @param secondValue
	 *            the second value of the domain
	 */
	public DomainBinary(Variable x, int firstValue, int secondValue) {
		this.x = x;
		this.firstValue = firstValue;
		this.secondValue = secondValue;
	}

	@Override
	public int toIdx(int v) {
		return v == firstValue ? 0 : v == secondValue ? 1 : -1;
	}

	@Override
	public int toVal(int a) {
		// assert a == 0 || a == 1;
		return a == 0 ? firstValue : secondValue;
	}

	@Override
	public Object allValues() {
		return new int[] { firstValue, secondValue };
	}

	@Override
	public String toString() {
		return "dom(" + var() + ")";
	}
}
