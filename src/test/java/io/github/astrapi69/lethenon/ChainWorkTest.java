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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The work a chain carries, which the network compares to decide between two chains (ADR 0003): the
 * sum of 2^difficulty over its blocks
 */
class ChainWorkTest
{

	@Test
	@DisplayName("an empty chain carries no work")
	void anEmptyChain_hasNoWork()
	{
		assertEquals(BigInteger.ZERO, ChainWork.of(List.of()));
	}

	@Test
	@DisplayName("each block adds 2 to the power of its difficulty")
	void eachBlock_addsTwoToItsDifficulty()
	{
		List<BlockBody> chain = new ArrayList<>(List.of(block(0, 8), block(1, 8), block(2, 10)));

		assertEquals(BigInteger.valueOf(256 + 256 + 1024), ChainWork.of(chain));
	}

	@Test
	@DisplayName("the largest difficulty does not overflow")
	void theLargestDifficulty_doesNotOverflow()
	{
		assertEquals(BigInteger.TWO.pow(DifficultyRule.MAXIMUM),
			ChainWork.of(List.of(block(0, DifficultyRule.MAXIMUM))));
	}

	private static BlockBody block(final long height, final int difficulty)
	{
		return new BlockBody(Chain.TEST_IDENTIFIER, height, Bytes.of(new byte[32]),
			Bytes.of(new byte[] { 1 }), List.of(), 1_759_000_000_000L + height, difficulty, "work");
	}
}
