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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The numbers the chain is built on, as executable facts rather than prose in an issue.
 * <p>
 * The supply is fixed and nothing mints: the block reward is paid out of a pool that exists in the
 * genesis block, so the sum of every balance equals the supply at every height.
 */
class EmissionTest
{

	@Test
	@DisplayName("the supply is 1,984,000,000 LETH")
	void theSupply_isTheDecidedNumber()
	{
		assertEquals(Amount.ofLeth(1_984_000_000L), Emission.TOTAL_SUPPLY);
	}

	@Test
	@DisplayName("all of it is the mining pool, and the reward divides it exactly")
	void thePool_isTheWholeSupply_andLastsExactlyOneMillionBlocks()
	{
		assertEquals(Emission.TOTAL_SUPPLY, Emission.MINING_POOL,
			"no share is allocated before the first block is mined (#111)");
		assertEquals(1_000_000L, Emission.BLOCKS_WITH_A_REWARD,
			"1,984 LETH per block out of 1,984,000,000 LETH is exactly 1,000,000 blocks, the "
				+ "genesis block the first of them - about 3.8 years at two minute blocks, and "
				+ "then fees only");
		assertEquals(0L, Emission.MINING_POOL.lethe() % Emission.BLOCK_REWARD.lethe(),
			"the pool divides without a remainder, so the last block pays the same as the first "
				+ "and nothing is left stranded in the pool");
	}

	@Test
	@DisplayName("nothing is minted: the reward comes out of the pool")
	void theReward_comesOutOfThePool()
	{
		Amount pool = Emission.MINING_POOL;

		Amount afterOneBlock = pool.minus(Emission.BLOCK_REWARD);

		assertEquals(Emission.MINING_POOL.lethe() - Emission.BLOCK_REWARD.lethe(),
			afterOneBlock.lethe(),
			"paying a reward is a transfer out of the pool, which is what keeps the supply fixed");
	}
}
