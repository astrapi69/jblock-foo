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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The block a miner looks for a pun for (lethenon#32): the difficulty the rule requires, a
 * timestamp the median-time rule accepts, the transfers waiting, and once mined a chain that
 * replays (lethenon#24).
 */
class MiningTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final long NOW = 1_759_500_000_000L;

	private final Wallet holder = Wallet.create();

	private final Bytes holderKey = holder.spendKey(SignatureSuite.ED25519);

	@Test
	void onAnEmptyChainTheNextBlockIsTheGenesisBlock()
	{
		BlockBody genesis = Mining.nextBlock(List.of(), MINER, List.of(), "first words", NOW);

		assertEquals(0L, genesis.height());
		assertEquals(Bytes.of(new byte[32]), genesis.previousHash());
		assertEquals(MINER, genesis.beneficiary(), "the genesis holder");
		assertEquals(List.of(), genesis.transactions());
		assertEquals(DifficultyRule.MINIMUM, genesis.difficulty());
		assertEquals(NOW, genesis.timestamp());
		assertEquals("first words", genesis.pun());
	}

	@Test
	void theNextBlockFollowsTheLastOne_andCarriesTheWaitingTransfers()
	{
		List<BlockBody> chain = TestChains.chainWith(holderKey);
		SignedTransaction waiting = Transfers.prepare(holder, SignatureSuite.ED25519, chain,
			List.of(), Destination.direct(MINER), Amount.ofLeth(3L), Amount.ZERO, "to the miner");

		BlockBody next = Mining.nextBlock(chain, MINER, List.of(waiting), "a pun", NOW);

		assertEquals(1L, next.height());
		assertEquals(Blocks.hashOf(chain.getLast()), next.previousHash());
		assertEquals(MINER, next.beneficiary());
		assertEquals(List.of(waiting), next.transactions());
		assertEquals(DifficultyRule.requiredFor(chain), next.difficulty());
		assertEquals(NOW, next.timestamp());
	}

	@Test
	void aClockBehindTheChainStillGivesATimestampTheRuleAccepts()
	{
		List<BlockBody> chain = TestChains.chainWith(holderKey);
		long behind = chain.getLast().timestamp() - 3_600_000L;

		BlockBody next = Mining.nextBlock(chain, MINER, List.of(), "a pun", behind);

		assertEquals(DifficultyRule.medianTimePast(chain) + 1L, next.timestamp());
	}

	@Test
	void aMinedNextBlockExtendsTheChainIntoOneThatReplays()
	{
		List<BlockBody> chain = new ArrayList<>(TestChains.chainWith(holderKey));
		SignedTransaction waiting = Transfers.prepare(holder, SignatureSuite.ED25519, chain,
			List.of(), Destination.direct(MINER), Amount.ofLeth(3L), Amount.ZERO, "to the miner");

		chain.add(Blocks.mine(Mining.nextBlock(chain, MINER, List.of(waiting), "a pun", NOW),
			1_000_000L).orElseThrow());

		Replay replay = Replay.verify(chain);
		assertEquals(Amount.ofLeth(3L).plus(TestChains.rewardOfBlock(1)),
			replay.finalState().balanceOf(MINER));
	}

	@Test
	void afterARetargetTheNextBlockAsksForWhatTheRuleRequires_notForTheMinimum()
	{
		// one block a minute is twice as fast as the two-minute target, so at the first retarget
		// the rule raises the difficulty above the minimum - a Mining that ignored the rule and
		// kept the minimum would only be caught here
		List<SignedTransaction> transfers = new ArrayList<>();
		for (long nonce = 0; nonce < DifficultyRule.INTERVAL - 1; nonce++)
		{
			transfers.add(holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce, holderKey,
				Destination.direct(MINER), Amount.ofLeth(1L), Amount.ZERO, "block " + nonce),
				SignatureSuite.ED25519));
		}
		List<BlockBody> chain = TestChains.chainWith(holderKey,
			transfers.toArray(new SignedTransaction[0]));

		BlockBody next = Mining.nextBlock(chain, MINER, List.of(), "a pun", NOW);

		assertEquals(DifficultyRule.requiredFor(chain), next.difficulty());
		assertTrue(next.difficulty() > DifficultyRule.MINIMUM,
			"the fixture has to reach a retarget that raises the difficulty: " + next.difficulty());
	}
}
