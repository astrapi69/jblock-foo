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

import java.util.OptionalLong;

/**
 * One line of the consensus rule: a chain admits a scheme from one height on, and either for good
 * or until a later height.
 * <p>
 * The end is how a scheme that turns out to be broken is switched off without starting the chain
 * again: blocks before it stay valid, blocks from it on may no longer use the scheme.
 *
 * @param chainIdentifier
 *            {@link Chain#IDENTIFIER} or {@link Chain#TEST_IDENTIFIER}
 * @param scheme
 *            the scheme admitted
 * @param fromHeight
 *            the first height at which a transfer may use it
 * @param untilHeight
 *            the first height at which it may no longer, or empty for no end
 */
public record SchemeActivation(String chainIdentifier, Scheme scheme, long fromHeight,
	OptionalLong untilHeight)
{

	/**
	 * Checks the line
	 *
	 * @throws IllegalArgumentException
	 *             for a chain that does not exist, a negative first height, or an end that is not
	 *             after the start
	 */
	public SchemeActivation
	{
		if (!Chain.isKnown(chainIdentifier))
		{
			throw new IllegalArgumentException("'" + chainIdentifier + "' names no chain; there are '"
				+ Chain.IDENTIFIER + "' and '" + Chain.TEST_IDENTIFIER + "'");
		}
		if (fromHeight < 0L)
		{
			throw new IllegalArgumentException("a scheme is admitted from height 0 at the earliest, "
				+ "not from " + fromHeight);
		}
		if (untilHeight.isPresent() && untilHeight.getAsLong() <= fromHeight)
		{
			throw new IllegalArgumentException("a scheme admitted from height " + fromHeight
				+ " cannot end at height " + untilHeight.getAsLong());
		}
	}

	/**
	 * A scheme admitted from a height on, for good
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @param scheme
	 *            the scheme
	 * @param fromHeight
	 *            the first height
	 * @return the line
	 */
	public static SchemeActivation from(final String chainIdentifier, final Scheme scheme,
		final long fromHeight)
	{
		return new SchemeActivation(chainIdentifier, scheme, fromHeight, OptionalLong.empty());
	}

	/**
	 * A scheme admitted from one height until, not including, a later one
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @param scheme
	 *            the scheme
	 * @param fromHeight
	 *            the first height
	 * @param untilHeight
	 *            the first height at which it is no longer admitted
	 * @return the line
	 */
	public static SchemeActivation between(final String chainIdentifier, final Scheme scheme,
		final long fromHeight, final long untilHeight)
	{
		return new SchemeActivation(chainIdentifier, scheme, fromHeight,
			OptionalLong.of(untilHeight));
	}

	/**
	 * Whether a block at this height may use the scheme
	 *
	 * @param height
	 *            the block height
	 * @return true inside the admitted range
	 */
	public boolean admits(final long height)
	{
		return height >= fromHeight
			&& (untilHeight.isEmpty() || height < untilHeight.getAsLong());
	}
}
