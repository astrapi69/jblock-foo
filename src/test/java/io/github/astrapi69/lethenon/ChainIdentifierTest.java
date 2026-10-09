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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The test chain of lethenon#50: a chain is the main chain or the test chain, its genesis block
 * decides which, and nothing - no block, no transfer, no signature - crosses from one to the other.
 * Since #137 they are {@code lethenon-2} and {@code lethenon-test-2}, and a chain under the
 * identifiers of the rules before 0.4.0 is refused with a reason that says so.
 */
class ChainIdentifierTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final long NOW = 1_759_500_000_000L;

	private final Wallet holder = Wallet.create();

	private final Bytes holderKey = holder.spendKey(SignatureSuite.ED25519);

	@Test
	@DisplayName("under the rules from 0.4.0 on the chains are lethenon-2 and lethenon-test-2 (#137)")
	void theTwoIdentifiers()
	{
		assertEquals("lethenon-2", Chain.IDENTIFIER);
		assertEquals("lethenon-test-2", Chain.TEST_IDENTIFIER);
	}

	/**
	 * The identifiers of the rules before 0.4.0, each with the one that took its place
	 */
	static Stream<Arguments> retiredIdentifiers()
	{
		return Stream.of(Arguments.of("lethenon-1", Chain.IDENTIFIER),
			Arguments.of("lethenon-test-1", Chain.TEST_IDENTIFIER));
	}

	@ParameterizedTest(name = "a chain under {0} is refused by the replay, naming {1}")
	@MethodSource("retiredIdentifiers")
	void aChainUnderARetiredIdentifier_isRefusedByTheReplay(final String retired,
		final String successor)
	{
		List<BlockBody> chain = List.of(underARetiredIdentifier(retired));

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertSaysStartedBefore040(refused.getMessage(), retired, successor);
	}

	@ParameterizedTest(name = "nothing is mined onto a chain under {0}")
	@MethodSource("retiredIdentifiers")
	void aChainUnderARetiredIdentifier_isNotMinedOnto(final String retired,
		final String successor)
	{
		List<BlockBody> chain = List.of(underARetiredIdentifier(retired));

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Mining.nextBlock(chain, MINER, List.of(), "a pun", NOW + 60_000L));

		assertSaysStartedBefore040(refused.getMessage(), retired, successor);
	}

	/**
	 * The decided refusal (#137): the chain was started under the rules before 0.4.0, and the message
	 * names both the retired identifier and the one that took its place
	 */
	static void assertSaysStartedBefore040(final String message, final String retired,
		final String successor)
	{
		assertTrue(message.contains("'" + retired + "'"), message);
		assertTrue(message.contains("started under the rules before lethenon 0.4.0"), message);
		assertTrue(message.contains("'" + successor + "'"), message);
	}

	/**
	 * A genesis block under a retired identifier, as a version before #137 mined it: built here
	 * directly, since this version's {@code Mining} refuses the identifier
	 */
	private BlockBody underARetiredIdentifier(final String retired)
	{
		return Blocks.mine(new BlockBody(retired, 0L, Bytes.of(new byte[32]), holderKey,
			List.of(), NOW, DifficultyRule.MINIMUM, "in the beginning"), 1_000_000L).orElseThrow();
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
	@CsvSource({ Chain.IDENTIFIER + ", " + Chain.TEST_IDENTIFIER,
		Chain.TEST_IDENTIFIER + ", " + Chain.IDENTIFIER })
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
	@ValueSource(strings = { "", "lethenon-3", "lethenon-test-3", "Lethenon-2", "lethenon-test-2 " })
	void anUnknownIdentifier_isRefused(final String unknown)
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Mining.nextBlock(unknown, List.of(), holderKey, List.of(), "a pun", NOW));

		assertTrue(refused.getMessage().contains("'" + unknown + "'"), refused.getMessage());
	}

	@ParameterizedTest(name = "a genesis block for \"{0}\" is refused by the replay")
	@ValueSource(strings = { "", "lethenon-3", "Lethenon-test-2" })
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
		assertEquals(Amount.ofLeth(3L).plus(TestChains.rewardOfBlock(2)),
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
	@CsvSource({ Chain.IDENTIFIER + ", " + Chain.TEST_IDENTIFIER,
		Chain.TEST_IDENTIFIER + ", " + Chain.IDENTIFIER })
	void aBlockOfTheOtherChain_isRefusedByTheReplay(final String genesisIdentifier,
		final String foreign)
	{
		List<BlockBody> chain = new ArrayList<>(chainOf(genesisIdentifier).subList(0, 1));
		BlockBody genesis = chain.getFirst();
		chain.add(Blocks.mine(new BlockBody(foreign, 1L, Blocks.hashOf(genesis), MINER, List.of(),
			NOW + 60_000L, DifficultyRule.requiredFor(chain), "smuggled"), 1_000_000L).orElseThrow());

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertEquals("block 1 belongs to chain '" + foreign + "', and its genesis block to '"
			+ genesisIdentifier + "'", refused.getMessage());
	}

	@ParameterizedTest(name = "a transfer signed for {1} inside a {0} chain is refused")
	@CsvSource({ Chain.IDENTIFIER + ", " + Chain.TEST_IDENTIFIER,
		Chain.TEST_IDENTIFIER + ", " + Chain.IDENTIFIER })
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

	/**
	 * A chain of its genesis block, which pays the burn account (#148), and block 1, which pays the
	 * holder
	 */
	private List<BlockBody> chainOf(final String identifier)
	{
		return TestChains.funding(identifier, holderKey, NOW);
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
