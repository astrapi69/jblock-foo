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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test chain of lethenon#50: a chain is {@code lethenon-1} or {@code lethenon-test-1}, its
 * genesis block decides which, and nothing - no block, no transfer, no signature - crosses from one
 * to the other.
 */
class ChainIdentifierTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final long NOW = 1_759_500_000_000L;

	private final Wallet holder = Wallet.create();

	private final Bytes holderKey = holder.spendKey(SignatureSuite.ED25519);

	@Test
	@DisplayName("the test identifier is lethenon-test-1, and the main one stays lethenon-1")
	void theTwoIdentifiers()
	{
		assertEquals("lethenon-test-1", Chain.TEST_IDENTIFIER);
		assertEquals("lethenon-1", Chain.IDENTIFIER);
	}

	@Test
	@DisplayName("a genesis block mined for the test chain carries its identifier")
	void aTestGenesis_carriesTheTestIdentifier()
	{
		BlockBody genesis = Mining.nextBlock(Chain.TEST_IDENTIFIER, List.of(), holderKey,
			List.of(), "a test", NOW);

		assertEquals(Chain.TEST_IDENTIFIER, genesis.chainIdentifier());
	}

	@Test
	@DisplayName("on a test chain the next block carries the genesis identifier without being told")
	void onATestChain_theNextBlockFollowsTheGenesis()
	{
		List<BlockBody> chain = testChain();

		BlockBody next = Mining.nextBlock(chain, MINER, List.of(), "a pun", NOW + 60_000L);

		assertEquals(Chain.TEST_IDENTIFIER, next.chainIdentifier());
	}

	@Test
	@DisplayName("an empty chain without an identifier still starts the main chain, as in 0.1.0")
	void withoutAnIdentifier_anEmptyChainStartsTheMainChain()
	{
		assertEquals(Chain.IDENTIFIER,
			Mining.nextBlock(List.of(), holderKey, List.of(), "a pun", NOW).chainIdentifier());
	}

	@ParameterizedTest(name = "a block for {1} on a chain whose genesis says {0} is refused")
	@CsvSource({ "lethenon-1, lethenon-test-1", "lethenon-test-1, lethenon-1" })
	void aBlockForTheOtherChain_isRefusedBeforeMining(final String genesisIdentifier,
		final String asked)
	{
		List<BlockBody> chain = chainOf(genesisIdentifier);

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Mining.nextBlock(asked, chain, MINER, List.of(), "a pun", NOW + 60_000L));

		assertTrue(refused.getMessage().contains("'" + genesisIdentifier + "'"),
			refused.getMessage());
		assertTrue(refused.getMessage().contains("'" + asked + "'"), refused.getMessage());
	}

	@ParameterizedTest(name = "the identifier \"{0}\" names no chain")
	@ValueSource(strings = { "", "lethenon-2", "lethenon-test-2", "Lethenon-1", "lethenon-test-1 " })
	void anUnknownIdentifier_isRefused(final String unknown)
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Mining.nextBlock(unknown, List.of(), holderKey, List.of(), "a pun", NOW));

		assertTrue(refused.getMessage().contains("'" + unknown + "'"), refused.getMessage());
	}

	@ParameterizedTest(name = "a genesis block for \"{0}\" is refused by the replay")
	@ValueSource(strings = { "", "lethenon-2", "Lethenon-test-1" })
	void aGenesisForAnUnknownChain_isRefusedByTheReplay(final String unknown)
	{
		BlockBody genesis = Blocks.mine(new BlockBody(unknown, 0L, Bytes.of(new byte[32]),
			holderKey, List.of(), NOW, DifficultyRule.MINIMUM, "nowhere"), 1_000_000L).orElseThrow();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(genesis)));

		assertTrue(refused.getMessage().startsWith("block 0 belongs to chain '" + unknown + "'"),
			refused.getMessage());
	}

	@Test
	@DisplayName("a transfer on the test chain is signed for it, mined into it and replayed")
	void aTransferOnTheTestChain_isReplayed()
	{
		List<BlockBody> chain = testChain();
		SignedTransaction transfer = Transfers.prepare(holder, SignatureSuite.ED25519, chain,
			List.of(), Destination.direct(MINER), Amount.ofLeth(3L), Amount.ZERO, "on the test");

		assertEquals(Chain.TEST_IDENTIFIER, transfer.body().chainIdentifier());

		Replay replay = Replay.verify(minedOnto(chain, List.of(transfer)));

		assertEquals(Chain.TEST_IDENTIFIER, replay.finalState().chainIdentifier());
		assertEquals(Amount.ofLeth(3L).plus(Emission.BLOCK_REWARD),
			replay.finalState().balanceOf(MINER));
	}

	@Test
	@DisplayName("what arrives at a one-time destination on the test chain is swept on it")
	void aSweepOnTheTestChain_isReplayed()
	{
		Wallet payee = Wallet.create();
		Destination paid = OneTimeAddresses.destinationFor(payee.address(),
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = testChain();
		chain = minedOnto(chain, List.of(Transfers.prepare(holder, SignatureSuite.ED25519, chain,
			List.of(), paid, Amount.ofLeth(4L), Amount.ZERO, "to an address")));

		List<SignedTransaction> sweep = Sweeps.prepare(payee, chain, new ArrayList<>(),
			Amount.ZERO, "swept");

		assertEquals(Chain.TEST_IDENTIFIER, sweep.getFirst().body().chainIdentifier());
		Replay replay = Replay.verify(minedOnto(chain, sweep));
		assertEquals(Amount.ofLeth(4L),
			replay.finalState().balanceOf(payee.spendKey(SignatureSuite.ED25519)));
	}

	@ParameterizedTest(name = "block 1 for {1} after a genesis for {0} is refused")
	@CsvSource({ "lethenon-1, lethenon-test-1", "lethenon-test-1, lethenon-1" })
	void aBlockOfTheOtherChain_isRefusedByTheReplay(final String genesisIdentifier,
		final String foreign)
	{
		List<BlockBody> chain = new ArrayList<>(chainOf(genesisIdentifier));
		BlockBody genesis = chain.getFirst();
		chain.add(Blocks.mine(new BlockBody(foreign, 1L, Blocks.hashOf(genesis), MINER, List.of(),
			NOW + 60_000L, DifficultyRule.requiredFor(chain), "smuggled"), 1_000_000L).orElseThrow());

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertEquals("block 1 belongs to chain '" + foreign + "', and its genesis block to '"
			+ genesisIdentifier + "'", refused.getMessage());
	}

	@ParameterizedTest(name = "a transfer signed for {1} inside a {0} chain is refused")
	@CsvSource({ "lethenon-1, lethenon-test-1", "lethenon-test-1, lethenon-1" })
	void aTransferSignedForTheOtherChain_isRefusedByTheReplay(final String genesisIdentifier,
		final String foreign)
	{
		List<BlockBody> chain = chainOf(genesisIdentifier);
		SignedTransaction crossing = holder.sign(new TransactionBody(foreign, 0L, holderKey,
			Destination.direct(MINER), Amount.ofLeth(1L), Amount.ZERO, "crossing over"),
			SignatureSuite.ED25519);

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(minedOnto(chain, List.of(crossing))));

		assertEquals("a transfer for chain '" + foreign + "' in a " + genesisIdentifier + " block",
			refused.getMessage());
	}

	private List<BlockBody> testChain()
	{
		return chainOf(Chain.TEST_IDENTIFIER);
	}

	private List<BlockBody> chainOf(final String identifier)
	{
		return List.of(Blocks.mine(Mining.nextBlock(identifier, List.of(), holderKey, List.of(),
			"in the beginning", NOW), 1_000_000L).orElseThrow());
	}

	private static List<BlockBody> minedOnto(final List<BlockBody> chain,
		final List<SignedTransaction> waiting)
	{
		List<BlockBody> extended = new ArrayList<>(chain);
		extended.add(Blocks.mine(Mining.nextBlock(chain, MINER, waiting, "block " + chain.size(),
			NOW + 60_000L * chain.size()), 1_000_000L).orElseThrow());
		return extended;
	}
}
