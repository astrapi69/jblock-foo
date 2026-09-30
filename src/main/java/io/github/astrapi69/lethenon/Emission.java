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

/**
 * The fixed numbers of the chain, as constants rather than as prose in an issue.
 * <p>
 * Nothing mints. Half of the supply is allocated at genesis, the other half is the mining pool, and
 * a block reward is a transfer out of that pool - which is why the supply stays fixed and why the
 * sum of every balance equals {@link #TOTAL_SUPPLY} at every height. When the pool is empty, after
 * exactly {@link #BLOCKS_WITH_A_REWARD} blocks, mining is paid by fees alone.
 */
public final class Emission
{

	/** 1,984,000,000 LETH. The year is the protest; the magnitude keeps balances readable */
	public static final Amount TOTAL_SUPPLY = Amount.ofLeth(1_984_000_000L);

	/** Half the supply, allocated at genesis to the pool the block reward is paid from */
	public static final Amount MINING_POOL = Amount.ofLeth(992_000_000L);

	/** What a block pays its miner, out of {@link #MINING_POOL} */
	public static final Amount BLOCK_REWARD = Amount.ofLeth(1_984L);

	/**
	 * How many blocks carry a reward: the pool divided by the reward, without a remainder, so the
	 * last paid block pays the same as the first and nothing is stranded in the pool
	 */
	public static final long BLOCKS_WITH_A_REWARD = MINING_POOL.lethe() / BLOCK_REWARD.lethe();

	private Emission()
	{
	}
}
