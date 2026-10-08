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
 * The numbers of the chain's money, as constants rather than as prose in an issue.
 * <p>
 * The genesis block puts {@link #GENESIS_SUPPLY} into the mining pool, and every block reward, the
 * genesis block's own included, comes out of that pool first (#111). A block pays one
 * {@link #EMISSION_DIVISOR}-th of the pool, so the first reward is exactly 1,984 LETH and the
 * reward declines from there. It never falls below {@link #TAIL_REWARD}: once the pool's share is
 * smaller, the difference is minted, the tail emission after Monero's (#133). So the supply is
 * {@link #GENESIS_SUPPLY} plus what has been minted, and the sum of every balance equals exactly
 * that at every height. The tail starts at block 3,403,214, after about 12.9 years; the
 * calculation is in #133.
 */
public final class Emission
{

	/**
	 * 1,984,000,000 LETH, the supply the genesis block puts into the pool. The year is the protest;
	 * the magnitude keeps balances readable. The tail emission adds to it
	 */
	public static final Amount GENESIS_SUPPLY = Amount.ofLeth(1_984_000_000L);

	/** The whole genesis supply, allocated at genesis to the pool the block reward is paid from */
	public static final Amount MINING_POOL = GENESIS_SUPPLY;

	/** A block pays this fraction of the pool: the first reward is exactly 1,984 LETH */
	public static final long EMISSION_DIVISOR = 1_000_000L;

	/**
	 * The least a block pays. 66 LETH a block is 0.8748 % of the genesis supply a year at
	 * two-minute blocks, against Monero's 0.8702 % at the start of its tail (#133)
	 */
	public static final Amount TAIL_REWARD = Amount.ofLeth(66L);

	/** The schedule both chains run */
	public static final EmissionSchedule SCHEDULE = new EmissionSchedule(EMISSION_DIVISOR,
		TAIL_REWARD);

	/** What the genesis block pays, the largest reward there is: 1,984 LETH */
	public static final Amount FIRST_REWARD = rewardFor(MINING_POOL).total();

	private Emission()
	{
	}

	/**
	 * What a block pays under {@link #SCHEDULE}
	 *
	 * @param pool
	 *            the pool before the reward
	 * @return the reward
	 */
	public static BlockReward rewardFor(final Amount pool)
	{
		return SCHEDULE.rewardFor(pool);
	}
}
