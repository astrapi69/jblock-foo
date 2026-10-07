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

import java.math.BigInteger;
import java.util.List;

/**
 * The work a chain carries: the sum of 2^difficulty over its blocks, difficulty being the leading
 * zero bits {@link DifficultyRule} requires (ADR 0003).
 * <p>
 * Two chains that share a genesis block are compared by this number, and the one with more work is
 * the chain. It is a BigInteger because a single block may require up to
 * {@link DifficultyRule#MAXIMUM} bits.
 */
public final class ChainWork
{

	private ChainWork()
	{
	}

	/**
	 * The work of a chain
	 *
	 * @param chain
	 *            the blocks, in any order
	 * @return the sum of 2^difficulty, zero for an empty chain
	 */
	public static BigInteger of(final List<BlockBody> chain)
	{
		BigInteger work = BigInteger.ZERO;
		for (BlockBody block : chain)
		{
			work = work.add(BigInteger.TWO.pow(block.difficulty()));
		}
		return work;
	}
}
