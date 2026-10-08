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
 * How the block reward declines, and what it never falls below (#133).
 * <p>
 * A block pays one {@link #divisor()}-th of the mining pool, so the reward falls as the pool
 * empties, after Monero's {@code (MONEY_SUPPLY - already_generated_coins) >> emission_speed_factor}
 * ({@code get_block_reward}, {@code src/cryptonote_basic/cryptonote_basic_impl.cpp}). It never
 * pays less than {@link #tailReward()}, Monero's tail emission: where the pool's share is below it,
 * the difference is minted. A fee goes into the pool, so it reaches the miners of the following
 * blocks a share at a time and, in the tail, lowers what is minted.
 *
 * @param divisor
 *            what the pool is divided by, at least 1
 * @param tailReward
 *            the least a block pays
 */
public record EmissionSchedule(long divisor, Amount tailReward)
{

	/**
	 * Checks the schedule
	 *
	 * @throws IllegalArgumentException
	 *             when the divisor is below 1
	 */
	public EmissionSchedule
	{
		if (divisor < 1L)
		{
			throw new IllegalArgumentException(
				"the pool is divided by " + divisor + ", and the divisor has to be at least 1");
		}
	}

	/**
	 * What a block pays when the pool holds the given amount before the reward
	 *
	 * @param pool
	 *            the pool, after the block's transfers paid their fees into it
	 * @return the pool's share, and what is minted to bring it up to the tail reward
	 */
	public BlockReward rewardFor(final Amount pool)
	{
		long share = pool.lethe() / divisor;
		long minted = Math.max(0L, tailReward.lethe() - share);
		return new BlockReward(Amount.ofLethe(share), Amount.ofLethe(minted));
	}
}
