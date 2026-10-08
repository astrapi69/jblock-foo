/*
 * The MIT License
 *
 * Copyright (C) 2015 Asterios Raptis
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.astrapi69.lethenon;

import java.util.Objects;

/**
 * An amount of LETH, counted in lethe - the base unit, of which 10^8 make one LETH.
 * <p>
 * A long, never a floating point number: money in a {@code double} is the mistake that shows up on
 * the third transaction and is unfixable afterwards. Every operation is checked, so an overflow
 * throws where the plain operator would wrap around and turn a balance into its opposite, and an
 * amount is never negative - a transfer that cannot be paid is refused rather than owed.
 * <p>
 * The genesis supply is 1.984 x 10^17 lethe against a long's 9.22 x 10^18, a factor of 46 of
 * headroom, which is what makes a 64 bit integer enough and {@code BigInteger} unnecessary. The
 * tail emission adds at most 66 LETH a block (#133), so the headroom lasts at least 5,200 years,
 * and an overflow throws rather than wraps.
 */
public final class Amount implements Comparable<Amount>
{

	/** How many lethe make one LETH */
	public static final long LETHE_PER_LETH = 100_000_000L;

	/** How many digits the text form has after the point, which is what makes it sortable as text */
	public static final int DECIMALS = 8;

	/** Nothing */
	public static final Amount ZERO = new Amount(0L);

	private final long lethe;

	private Amount(final long lethe)
	{
		this.lethe = lethe;
	}

	/**
	 * An amount of the given number of lethe
	 *
	 * @param lethe
	 *            the count of base units, never negative
	 * @return the amount
	 */
	public static Amount ofLethe(final long lethe)
	{
		if (lethe < 0L)
		{
			throw new IllegalArgumentException(
				"an amount is never negative, and " + lethe + " lethe is");
		}
		return new Amount(lethe);
	}

	/**
	 * An amount of the given whole LETH
	 *
	 * @param leth
	 *            the count of LETH, never negative
	 * @return the amount
	 */
	public static Amount ofLeth(final long leth)
	{
		return ofLethe(Math.multiplyExact(leth, LETHE_PER_LETH));
	}

	/**
	 * Reads back what {@link #toString()} wrote
	 *
	 * @param text
	 *            the amount as text, with exactly eight decimals
	 * @return the amount
	 */
	public static Amount parse(final String text)
	{
		int point = text.indexOf('.');
		if (point < 0 || text.length() - point - 1 != DECIMALS)
		{
			throw new IllegalArgumentException(
				"an amount is written with exactly " + DECIMALS + " decimals, unlike '" + text + "'");
		}
		long whole = Long.parseLong(text.substring(0, point));
		long fraction = Long.parseLong(text.substring(point + 1));
		return ofLethe(Math.addExact(Math.multiplyExact(whole, LETHE_PER_LETH), fraction));
	}

	/**
	 * An amount as a person types it - "12.5", "1984", "0.00000001" - turned into lethe exactly.
	 * {@link #parse(String)} stays the strict form with all eight decimals; this is the forgiving
	 * one for people, and it still never rounds (lethenon#32)
	 *
	 * @param text
	 *            the amount in LETH
	 * @return the amount
	 * @throws IllegalArgumentException
	 *             for more than eight decimals, a negative amount, or no number at all
	 */
	public static Amount parseLeth(final String text)
	{
		try
		{
			long lethe = new java.math.BigDecimal(text.strip()).movePointRight(DECIMALS)
				.longValueExact();
			return ofLethe(lethe);
		}
		catch (ArithmeticException | NumberFormatException unreadable)
		{
			throw new IllegalArgumentException("'" + text + "' is not an amount of LETH with at "
				+ "most " + DECIMALS + " decimals", unreadable);
		}
	}

	/**
	 * The count of base units
	 *
	 * @return the lethe
	 */
	public long lethe()
	{
		return lethe;
	}

	/**
	 * This amount and the other one
	 *
	 * @param other
	 *            the amount to add
	 * @return the sum
	 * @throws ArithmeticException
	 *             if the sum leaves the range of a long
	 */
	public Amount plus(final Amount other)
	{
		return new Amount(Math.addExact(lethe, other.lethe));
	}

	/**
	 * This amount less the other one
	 *
	 * @param other
	 *            the amount to take away
	 * @return the difference
	 * @throws ArithmeticException
	 *             if the difference would be negative, because there is no negative balance
	 */
	public Amount minus(final Amount other)
	{
		long difference = Math.subtractExact(lethe, other.lethe);
		if (difference < 0L)
		{
			throw new ArithmeticException(
				"cannot take " + other + " from " + this + ": there is no negative balance");
		}
		return new Amount(difference);
	}

	@Override
	public int compareTo(final Amount other)
	{
		return Long.compare(lethe, other.lethe);
	}

	@Override
	public boolean equals(final Object other)
	{
		return other instanceof Amount amount && amount.lethe == lethe;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(lethe);
	}

	/**
	 * The amount with exactly eight decimals, so that two amounts compare as text the way they
	 * compare as numbers
	 */
	@Override
	public String toString()
	{
		return lethe / LETHE_PER_LETH + "." + String.format("%08d", lethe % LETHE_PER_LETH);
	}
}
