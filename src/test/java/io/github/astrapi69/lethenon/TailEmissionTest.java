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

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The tail emission on a chain (#133), on both chains alike.
 * <p>
 * The decided schedule reaches its tail after 3.4 million blocks, so these chains run a schedule
 * that gets there in about thirty: half the pool per block, a tail of 1 LETH. The rules that
 * matter do not depend on the numbers: what the pool's share does not cover is minted, the minted
 * part is counted, and every balance adds up to the genesis supply plus that count.
 */
class TailEmissionTest
{

	private static final EmissionSchedule FAST = new EmissionSchedule(2L, Amount.ofLeth(1L));

	private static final ConsensusRules RULES = new ConsensusRules(
		ConsensusRules.LETHENON.activations(), List.of(), List.of(), FAST);

	private static final Bytes MINER = Bytes.of("miner".getBytes());

	private static final Bytes SOMEONE = Bytes.of("someone".getBytes());

	private static final int BLOCKS = 40;

	/** The first block whose reward is in the tail under {@link #FAST}, without fees */
	private static final int FIRST_TAIL_BLOCK = firstTailBlock();

	private static final Amount FEE = Amount.ofLeth(10L);

	private final KeyPair holder = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private static int firstTailBlock()
	{
		Amount pool = Emission.MINING_POOL;
		int height = 0;
		// bounded by the chain's length, so a schedule without a tail fails the tests that need
		// one instead of never returning
		while (FAST.rewardFor(pool).minted().equals(Amount.ZERO) && height < BLOCKS)
		{
			pool = pool.minus(FAST.rewardFor(pool).fromPool());
			height++;
		}
		return height;
	}

	private Bytes holderKey()
	{
		return TransactionSigner.asBytes(holder.getPublic());
	}

	private SignedTransaction aTransferWithAFee(final String chainIdentifier)
	{
		return TransactionSigner.sign(new TransactionBody(chainIdentifier, 0L, holderKey(),
			Destination.direct(SOMEONE), Amount.ofLeth(1L), FEE, "a fee in the tail"),
			SignatureSuite.ED25519, holder.getPrivate());
	}

	private List<BlockBody> chain(final String chainIdentifier,
		final Map<Integer, SignedTransaction> transfersAt)
	{
		List<BlockBody> chain = new ArrayList<>();
		for (int height = 0; height < BLOCKS; height++)
		{
			List<SignedTransaction> waiting = transfersAt.containsKey(height)
				? List.of(transfersAt.get(height))
				: List.of();
			// block 0 pays the burn account whoever mines it (#148), block 1 the holder
			chain.add(Blocks.mine(Mining.nextBlock(chainIdentifier, chain,
				height == 1 ? holderKey() : MINER, waiting, "block " + height,
				1_759_000_000_000L + 120_000L * height), 1_000_000L).orElseThrow());
		}
		return chain;
	}

	private static ChainState stateAt(final List<BlockBody> chain, final int height)
	{
		return TestChains.replay(chain.subList(0, height + 1), RULES).finalState();
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void inTheTail_everyBalanceAddsUpToTheGenesisSupplyPlusWhatWasMinted(
		final String chainIdentifier)
	{
		ChainState state = TestChains.replay(chain(chainIdentifier, Map.of()), RULES).finalState();

		Amount pool = Emission.MINING_POOL;
		Amount minted = Amount.ZERO;
		for (int height = 0; height < BLOCKS; height++)
		{
			BlockReward reward = FAST.rewardFor(pool);
			pool = pool.minus(reward.fromPool());
			minted = minted.plus(reward.minted());
		}
		assertTrue(FIRST_TAIL_BLOCK < BLOCKS, "the chain reaches the tail: " + FIRST_TAIL_BLOCK);
		assertEquals(minted, state.minted(), "every minted part is counted");
		assertTrue(state.minted().compareTo(Amount.ZERO) > 0, "the tail has minted");
		assertEquals(Emission.GENESIS_SUPPLY.plus(minted), state.supply());
		assertEquals(state.supply(), state.total(),
			"the sum of every balance is the genesis supply plus what was minted");
		assertEquals(pool, state.balanceOf(ChainState.POOL));
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void whereThePoolHoldsLessThanTheOldFlatReward_everyBlockStillPays(
		final String chainIdentifier)
	{
		List<BlockBody> chain = chain(chainIdentifier, Map.of());

		for (int height = FIRST_TAIL_BLOCK; height < BLOCKS; height++)
		{
			ChainState before = stateAt(chain, height - 1);
			ChainState after = stateAt(chain, height);
			Amount paid = after.balanceOf(MINER).minus(before.balanceOf(MINER));
			assertTrue(before.balanceOf(ChainState.POOL).compareTo(Amount.ofLeth(1_984L)) < 0,
				"0.3.0 paid nothing from a pool this small until it held 1,984 LETH again");
			assertEquals(FAST.rewardFor(before.balanceOf(ChainState.POOL)).total(), paid,
				"block " + height + " pays the pool's share plus what is minted");
			assertTrue(paid.compareTo(FAST.tailReward()) >= 0, "never less than the tail");
		}
	}

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void aFeeInTheTail_reachesTheMinersAShareAtATime_andLowersWhatIsMinted(
		final String chainIdentifier)
	{
		int carrier = FIRST_TAIL_BLOCK + 2;
		List<BlockBody> withFee = chain(chainIdentifier,
			Map.of(carrier, aTransferWithAFee(chainIdentifier)));
		List<BlockBody> withoutFee = chain(chainIdentifier, Map.of());

		Amount poolBefore = stateAt(withFee, carrier - 1).balanceOf(ChainState.POOL);
		Amount paid = stateAt(withFee, carrier).balanceOf(MINER)
			.minus(stateAt(withFee, carrier - 1).balanceOf(MINER));
		ChainState end = TestChains.replay(withFee, RULES).finalState();
		ChainState endWithout = TestChains.replay(withoutFee, RULES).finalState();

		assertEquals(FAST.rewardFor(poolBefore.plus(FEE)).total(), paid,
			"the block carrying the fee pays its share of the pool the fee went into");
		assertTrue(paid.compareTo(FEE) < 0, "and not the fee as a lump: " + paid);
		assertTrue(end.minted().compareTo(endWithout.minted()) < 0,
			"in the tail the fee replaces minting: " + end.minted() + " against "
				+ endWithout.minted());
		assertEquals(end.supply(), end.total());
		assertEquals(Emission.GENESIS_SUPPLY.plus(end.minted()), end.total());
	}
}
