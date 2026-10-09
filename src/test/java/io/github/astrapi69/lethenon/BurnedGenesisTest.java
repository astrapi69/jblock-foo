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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The genesis block's reward is burned, by a rule of the chain rather than by a construction
 * (#148, ADR 0005): block 0 pays it to the burn account, on both chains, and no transfer may
 * spend from there. The burned reward stays in every sum, so the supply invariant still holds.
 */
class BurnedGenesisTest
{

	private static final long NOW = 1_759_000_000_000L;

	private final KeyPair miner = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private Bytes minerKey()
	{
		return TransactionSigner.asBytes(miner.getPublic());
	}

	private static BlockBody genesis(final String chainIdentifier)
	{
		return Genesis.candidate(chainIdentifier, "a headline of the day", NOW);
	}

	private List<BlockBody> withBlockOne(final String chainIdentifier,
		final List<SignedTransaction> transfers)
	{
		List<BlockBody> chain = new ArrayList<>(List.of(genesis(chainIdentifier)));
		chain.add(Blocks.mine(Mining.nextBlock(chain, minerKey(), transfers, "block 1",
			NOW + DifficultyRule.TARGET_BLOCK_MILLIS), 1_000_000L).orElseThrow());
		return chain;
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void theGenesisReward_landsOnTheBurnAccount(final String chainIdentifier)
	{
		BlockBody genesis = genesis(chainIdentifier);
		ChainState state = Replay.verify(List.of(genesis)).finalState();

		assertEquals(Genesis.NOBODY, genesis.beneficiary(), "a genesis block names the burn account");
		assertEquals(Emission.FIRST_REWARD, state.balanceOf(Genesis.NOBODY),
			"1,984 LETH, burned");
		assertEquals(Emission.GENESIS_SUPPLY.minus(Emission.FIRST_REWARD),
			state.balanceOf(ChainState.POOL));
		assertEquals(state.supply(), state.total(),
			"the burned reward counts: all balances, the burn account's included, are the supply");
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void aGenesisBlockPayingAnybodyElse_isRefused(final String chainIdentifier)
	{
		BlockBody paying = Blocks.mine(new BlockBody(chainIdentifier, 0L, Bytes.of(new byte[32]),
			minerKey(), List.of(), NOW, DifficultyRule.MINIMUM, "mine for me"), 1_000_000L)
			.orElseThrow();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(paying)));

		assertTrue(refused.getMessage().startsWith("block 0 pays its reward to " + minerKey()),
			refused.getMessage());
		assertTrue(refused.getMessage().contains("burn account"), refused.getMessage());
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void aTransferFromTheBurnAccount_isRefused(final String chainIdentifier)
	{
		SignedTransaction spending = TransactionSigner.sign(new TransactionBody(chainIdentifier, 0L,
			Genesis.NOBODY, Destination.direct(minerKey()), Amount.ofLeth(1L), Amount.ZERO,
			"the burned reward"), SignatureSuite.ED25519, miner.getPrivate());
		List<BlockBody> chain = new ArrayList<>(List.of(genesis(chainIdentifier)));
		chain.add(Blocks.mine(new BlockBody(chainIdentifier, 1L, Blocks.hashOf(chain.getFirst()),
			minerKey(), List.of(spending), NOW + DifficultyRule.TARGET_BLOCK_MILLIS,
			DifficultyRule.MINIMUM, "spend it"), 1_000_000L).orElseThrow());

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertTrue(refused.getMessage().startsWith("a transfer from the burn account"),
			refused.getMessage());
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void afterTheFirstBlock_theInvariantHolds_andTheBurnedRewardStays(final String chainIdentifier)
	{
		ChainState state = Replay.verify(withBlockOne(chainIdentifier, List.of())).finalState();

		assertEquals(Emission.FIRST_REWARD, state.balanceOf(Genesis.NOBODY));
		assertEquals(TestChains.rewardOfBlock(1), state.balanceOf(minerKey()),
			"block 1 is the first reward anybody holds");
		assertEquals(state.supply(), state.total());
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void aGenesisBlockIsMinedForTheBurnAccount_whoeverAsks(final String chainIdentifier)
	{
		assertEquals(Genesis.NOBODY, Mining.nextBlock(chainIdentifier, List.of(), minerKey(),
			List.of(), "mine", NOW).beneficiary());
	}
}
