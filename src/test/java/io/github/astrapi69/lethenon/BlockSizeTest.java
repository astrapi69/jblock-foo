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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A block is at most 300,000 bytes, Monero's full reward zone taken as a hard limit, on the test
 * chain (#99) and on the main chain (#109), and a block that is mined carries the longest prefix of
 * the waiting transfers that fits
 */
class BlockSizeTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	private static final long GENESIS_TIME = 1_759_000_000_000L;

	private static final long STEP = DifficultyRule.TARGET_BLOCK_MILLIS;

	private static final Map<String, Funded> FUNDED = new ConcurrentHashMap<>();

	/**
	 * A chain whose holder funded its ML-DSA-65 account, and 60 ML-DSA-65 transfers from it, more
	 * than one block takes: 60 times 5,380 bytes is past 300,000
	 */
	private record Funded(List<BlockBody> chain, List<SignedTransaction> postQuantum)
	{
	}

	private static Funded funded(final String chainIdentifier)
	{
		return FUNDED.computeIfAbsent(chainIdentifier, BlockSizeTest::aFundedPostQuantumAccount);
	}

	private static Funded aFundedPostQuantumAccount(final String chainIdentifier)
	{
		Wallet holder = Wallet.create();
		Bytes classical = holder.spendKey(SignatureSuite.ED25519);
		Bytes pq = holder.spendKey(SignatureSuite.ML_DSA_65);
		List<BlockBody> chain = new ArrayList<>(List.of(Blocks.mine(Mining.nextBlock(
			chainIdentifier, List.of(), classical, List.of(), "genesis", GENESIS_TIME),
			1_000_000L).orElseThrow()));
		SignedTransaction funding = holder.sign(new TransactionBody(chainIdentifier, 0L,
			classical, Destination.direct(pq), Amount.ofLeth(1_000L), Amount.ZERO, ""),
			SignatureSuite.ED25519);
		chain.add(Blocks.mine(Mining.nextBlock(chain, MINER, List.of(funding), "funding",
			GENESIS_TIME + STEP), 1_000_000L).orElseThrow());
		List<SignedTransaction> postQuantum = new ArrayList<>();
		for (long nonce = 0; nonce < 60; nonce++)
		{
			postQuantum.add(holder.sign(new TransactionBody(chainIdentifier, nonce, pq, SOMEONE,
				Amount.ofLethe(1L), Amount.ZERO, ""), SignatureSuite.ML_DSA_65));
		}
		return new Funded(List.copyOf(chain), List.copyOf(postQuantum));
	}

	private static List<BlockBody> withBlockOf(final String chainIdentifier,
		final List<SignedTransaction> transfers)
	{
		List<BlockBody> funded = funded(chainIdentifier).chain();
		BlockBody unmined = Mining.nextBlock(Chain.IDENTIFIER, List.of(), MINER, List.of(), "",
			0L);
		BlockBody next = new BlockBody(chainIdentifier, 2L, Blocks.hashOf(funded.getLast()), MINER,
			transfers, GENESIS_TIME + 2 * STEP, unmined.difficulty(), "too large");
		List<BlockBody> chain = new ArrayList<>(funded);
		chain.add(Blocks.mine(next, 1_000_000L).orElseThrow());
		return List.copyOf(chain);
	}

	private static List<SignedTransaction> postQuantum(final String chainIdentifier)
	{
		return funded(chainIdentifier).postQuantum();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("the limit is 300,000 bytes on both chains")
	void theLimit_is300000Bytes(final String chainIdentifier)
	{
		assertEquals(300_000,
			ConsensusRules.LETHENON.limitsFor(chainIdentifier).orElseThrow().maximumBytes());
	}

	@Test
	@DisplayName("a block's size is what it takes in the chain encoding")
	void aBlocksSize_isWhatItTakesInTheChainEncoding()
	{
		BlockBody block = withBlockOf(Chain.TEST_IDENTIFIER,
			postQuantum(Chain.TEST_IDENTIFIER).subList(0, 3)).getLast();

		assertEquals(CanonicalEncoding.encodeChain(List.of(block)).length - 1 - Long.BYTES,
			CanonicalEncoding.blockSize(block));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("a block larger than the limit does not verify, on either chain")
	void aBlockLargerThanTheLimit_doesNotVerify(final String chainIdentifier)
	{
		List<BlockBody> chain = withBlockOf(chainIdentifier, postQuantum(chainIdentifier));

		ChainRejected rejected = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertTrue(rejected.getMessage().contains("block 2 is "), rejected.getMessage());
		assertTrue(rejected.getMessage().contains("allows 300000"), rejected.getMessage());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("the limit holds to the byte, on either chain")
	void theLimitHoldsToTheByte(final String chainIdentifier)
	{
		List<BlockBody> chain = withBlockOf(chainIdentifier,
			postQuantum(chainIdentifier).subList(0, 10));
		int size = CanonicalEncoding.blockSize(chain.getLast());

		assertEquals(3L,
			Replay.verify(chain, rulesWithLimit(chainIdentifier, size), GENESIS_TIME).blocks());
		assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, rulesWithLimit(chainIdentifier, size - 1), GENESIS_TIME));
	}

	private static ConsensusRules rulesWithLimit(final String chainIdentifier, final int bytes)
	{
		return new ConsensusRules(ConsensusRules.LETHENON.activations(),
			List.of(new BlockLimits(chainIdentifier, BlockLimits.TWO_HOURS, bytes)));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("a mined block carries the longest prefix that fits, and it verifies, on either chain")
	void aMinedBlock_carriesTheLongestPrefixThatFits(final String chainIdentifier)
	{
		List<BlockBody> funded = funded(chainIdentifier).chain();
		List<SignedTransaction> postQuantum = postQuantum(chainIdentifier);
		BlockBody next = Mining.nextBlock(funded, MINER, postQuantum, "a pun",
			GENESIS_TIME + 2 * STEP);
		BlockBody mined = Blocks.mine(next, 1_000_000L).orElseThrow();
		int carried = mined.transactions().size();

		assertTrue(carried > 0 && carried < postQuantum.size(), carried + " carried");
		assertEquals(postQuantum.subList(0, carried), mined.transactions());
		assertTrue(CanonicalEncoding.blockSize(mined) <= BlockLimits.MAXIMUM_BLOCK_BYTES);
		assertTrue(CanonicalEncoding.blockSize(mined) + CanonicalEncoding.sizeInBlock(
			postQuantum.get(carried)) > BlockLimits.MAXIMUM_BLOCK_BYTES - Mining.MINING_SUFFIX_BYTES,
			"the next transfer would not have fitted");
		List<BlockBody> chain = new ArrayList<>(funded);
		chain.add(mined);
		assertEquals((long)carried + 1L, Replay.verify(chain).transactions());
	}

	@Test
	@DisplayName("a transfer is carried only with room left for the characters mining adds")
	void aTransferIsCarried_onlyWithRoomForTheMiningSuffix()
	{
		List<SignedTransaction> postQuantum = postQuantum(Chain.TEST_IDENTIFIER);
		BlockBody empty = Mining.nextBlock(funded(Chain.TEST_IDENTIFIER).chain(), MINER,
			List.of(), "a pun", GENESIS_TIME + 2 * STEP);
		SignedTransaction first = postQuantum.getFirst();
		int exactly = CanonicalEncoding.blockSize(empty) + CanonicalEncoding.sizeInBlock(first)
			+ Mining.MINING_SUFFIX_BYTES;

		assertEquals(List.of(first), Mining.fitting(empty, postQuantum, exactly));
		assertEquals(List.of(), Mining.fitting(empty, postQuantum, exactly - 1));
		assertEquals(21, Mining.MINING_SUFFIX_BYTES, "' #' and 19 digits of a long");
	}
}
