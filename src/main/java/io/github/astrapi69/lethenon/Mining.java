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

import java.util.List;

/**
 * The block a miner looks for a pun for (lethenon#32).
 * <p>
 * The difficulty is the one {@link DifficultyRule} requires at this height - a block cannot choose
 * how hard it is (lethenon#24) - and the timestamp is the given time, or one millisecond after the
 * median of the blocks before when that time is not later: a clock behind the chain still has to
 * produce a block the replay accepts. The time is a parameter so that a test, or a replay of a
 * mining run, can fix it.
 */
public final class Mining
{

	private Mining()
	{
	}

	/**
	 * The next block of the chain, before it was mined
	 *
	 * @param chain
	 *            the chain so far, genesis first; empty for a new chain
	 * @param beneficiary
	 *            whom the block pays: on an empty chain the genesis holder, otherwise the miner
	 * @param waiting
	 *            the transfers to carry; ignored for the genesis block, which carries the
	 *            allocation instead
	 * @param pun
	 *            the words mining starts from
	 * @param now
	 *            the current time, milliseconds since the epoch
	 * @return the block to mine with {@link Blocks#mine}
	 */
	public static BlockBody nextBlock(final List<BlockBody> chain, final Bytes beneficiary,
		final List<SignedTransaction> waiting, final String pun, final long now)
	{
		int difficulty = DifficultyRule.requiredFor(chain);
		if (chain.isEmpty())
		{
			return new BlockBody(Chain.IDENTIFIER, 0L, Bytes.of(new byte[32]), beneficiary,
				List.of(), now, difficulty, pun);
		}
		long timestamp = Math.max(now, DifficultyRule.medianTimePast(chain) + 1L);
		BlockBody last = chain.getLast();
		return new BlockBody(Chain.IDENTIFIER, last.height() + 1L, Blocks.hashOf(last), beneficiary,
			waiting, timestamp, difficulty, pun);
	}
}
