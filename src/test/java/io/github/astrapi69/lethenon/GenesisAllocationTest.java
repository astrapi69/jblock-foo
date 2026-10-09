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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the genesis block allocates, on both chains alike (#111): the whole genesis supply goes into
 * the mining pool, and the genesis block is paid the ordinary block reward out of it, like every
 * other block - to the burn account (#148). Nobody holds anything before block 1 is mined.
 */
class GenesisAllocationTest
{

	private static final Bytes MINER = Bytes.of("miner".getBytes());

	/** The rule both chains run, without the main chain's genesis anchor, so a chain can start here */
	private static final ConsensusRules RULES = new ConsensusRules(
		ConsensusRules.LETHENON.activations(), List.of(), List.of(), Emission.SCHEDULE);

	private static List<BlockBody> chain(final String chainIdentifier, final int blocksAfterGenesis)
	{
		List<BlockBody> chain = new ArrayList<>();
		chain.add(Blocks.mine(new BlockBody(chainIdentifier, 0L, Bytes.of(new byte[32]),
			Genesis.NOBODY, new ArrayList<>(), 1_759_000_000_000L, 8,
			"in the beginning was the pun"), 1_000_000L)
			.orElseThrow());
		for (int height = 1; height <= blocksAfterGenesis; height++)
		{
			BlockBody previous = chain.getLast();
			chain.add(Blocks.mine(new BlockBody(chainIdentifier, height, Blocks.hashOf(previous),
				MINER, List.of(), 1_759_000_000_000L + 60_000L * height, 8, "block " + height),
				1_000_000L).orElseThrow());
		}
		return chain;
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void theGenesisBlock_isPaidTheOrdinaryReward_andNothingElse(final String chainIdentifier)
	{
		ChainState state = TestChains.replay(chain(chainIdentifier, 0), RULES).finalState();

		assertEquals(Emission.FIRST_REWARD, state.balanceOf(Genesis.NOBODY),
			"the burn account holds one block reward, not a share of the supply (#148)");
		assertEquals(Emission.GENESIS_SUPPLY.minus(Emission.FIRST_REWARD),
			state.balanceOf(ChainState.POOL),
			"everything else is in the pool the rewards are paid from");
		assertEquals(Emission.GENESIS_SUPPLY, state.total(),
			"the sum of every balance is the supply, from the genesis block on");
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void everyBlock_includingTheGenesisBlock_takesAMillionthOfThePool(final String chainIdentifier)
	{
		ChainState state = TestChains.replay(chain(chainIdentifier, 2), RULES).finalState();

		Amount pool = Emission.MINING_POOL;
		Amount first = Emission.rewardFor(pool).total();
		pool = pool.minus(first);
		Amount second = Emission.rewardFor(pool).total();
		pool = pool.minus(second);
		Amount third = Emission.rewardFor(pool).total();
		pool = pool.minus(third);
		assertEquals(Amount.ofLeth(1_984L), first);
		assertEquals(Amount.ofLethe(198_399_801_600L), second, "a millionth of what was left");
		assertEquals(first, state.balanceOf(Genesis.NOBODY));
		assertEquals(second.plus(third), state.balanceOf(MINER));
		assertEquals(pool, state.balanceOf(ChainState.POOL),
			"three blocks, three rewards out of the pool");
		assertEquals(Amount.ZERO, state.minted(), "nothing is minted before the tail");
		assertEquals(Emission.GENESIS_SUPPLY, state.total());
	}
}
