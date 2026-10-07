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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A block is at most 300,000 bytes on the test chain, Monero's full reward zone taken as a hard
 * limit, and a block that is mined carries the longest prefix of the waiting transfers that fits
 * (#99)
 */
class BlockSizeTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	private static final long GENESIS_TIME = 1_759_000_000_000L;

	private static final long STEP = DifficultyRule.TARGET_BLOCK_MILLIS;

	private static Wallet holder;

	private static List<BlockBody> funded;

	private static List<SignedTransaction> postQuantum;

	/**
	 * A test chain whose holder funded its ML-DSA-65 account, and 60 ML-DSA-65 transfers from it,
	 * more than one block takes: 60 times 5,380 bytes is past 300,000
	 */
	@BeforeAll
	static void aFundedPostQuantumAccount()
	{
		holder = Wallet.create();
		Bytes classical = holder.spendKey(SignatureSuite.ED25519);
		Bytes pq = holder.spendKey(SignatureSuite.ML_DSA_65);
		List<BlockBody> chain = new ArrayList<>(List.of(Blocks.mine(Mining.nextBlock(
			Chain.TEST_IDENTIFIER, List.of(), classical, List.of(), "genesis", GENESIS_TIME),
			1_000_000L).orElseThrow()));
		SignedTransaction funding = holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, 0L,
			classical, Destination.direct(pq), Amount.ofLeth(1_000L), Amount.ZERO, ""),
			SignatureSuite.ED25519);
		chain.add(Blocks.mine(Mining.nextBlock(chain, MINER, List.of(funding), "funding",
			GENESIS_TIME + STEP), 1_000_000L).orElseThrow());
		funded = List.copyOf(chain);
		postQuantum = new ArrayList<>();
		for (long nonce = 0; nonce < 60; nonce++)
		{
			postQuantum.add(holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce, pq,
				SOMEONE, Amount.ofLethe(1L), Amount.ZERO, ""), SignatureSuite.ML_DSA_65));
		}
	}

	private static List<BlockBody> withBlockOf(final List<SignedTransaction> transfers)
	{
		BlockBody unmined = Mining.nextBlock(Chain.IDENTIFIER, List.of(), MINER, List.of(), "",
			0L);
		BlockBody next = new BlockBody(Chain.TEST_IDENTIFIER, 2L,
			Blocks.hashOf(funded.getLast()), MINER, transfers, GENESIS_TIME + 2 * STEP,
			unmined.difficulty(), "too large");
		List<BlockBody> chain = new ArrayList<>(funded);
		chain.add(Blocks.mine(next, 1_000_000L).orElseThrow());
		return List.copyOf(chain);
	}

	@Test
	@DisplayName("the test chain's limit is 300,000 bytes")
	void theLimit_is300000Bytes()
	{
		assertEquals(300_000, ConsensusRules.LETHENON.limitsFor(Chain.TEST_IDENTIFIER)
			.orElseThrow().maximumBytes());
	}

	@Test
	@DisplayName("a block's size is what it takes in the chain encoding")
	void aBlocksSize_isWhatItTakesInTheChainEncoding()
	{
		BlockBody block = withBlockOf(postQuantum.subList(0, 3)).getLast();

		assertEquals(CanonicalEncoding.encodeChain(List.of(block)).length - 1 - Long.BYTES,
			CanonicalEncoding.blockSize(block));
	}

	@Test
	@DisplayName("a block larger than the limit does not verify")
	void aBlockLargerThanTheLimit_doesNotVerify()
	{
		List<BlockBody> chain = withBlockOf(postQuantum);

		ChainRejected rejected = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertTrue(rejected.getMessage().contains("block 2 is "), rejected.getMessage());
		assertTrue(rejected.getMessage().contains("allows 300000"), rejected.getMessage());
	}

	@Test
	@DisplayName("the limit holds to the byte")
	void theLimitHoldsToTheByte()
	{
		List<BlockBody> chain = withBlockOf(postQuantum.subList(0, 10));
		int size = CanonicalEncoding.blockSize(chain.getLast());

		assertEquals(3L, Replay.verify(chain, rulesWithLimit(size), GENESIS_TIME).blocks());
		assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, rulesWithLimit(size - 1), GENESIS_TIME));
	}

	private static ConsensusRules rulesWithLimit(final int bytes)
	{
		return new ConsensusRules(ConsensusRules.LETHENON.activations(),
			List.of(new BlockLimits(Chain.TEST_IDENTIFIER, BlockLimits.TWO_HOURS, bytes)));
	}

	@Test
	@DisplayName("the main chain carries no size limit until the decision that starts it")
	void theMainChain_carriesNoSizeLimitYet()
	{
		assertTrue(ConsensusRules.LETHENON.limitsFor(Chain.IDENTIFIER).isEmpty());
	}

	@Test
	@DisplayName("a mined block carries the longest prefix that fits, and it verifies")
	void aMinedBlock_carriesTheLongestPrefixThatFits()
	{
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
		BlockBody empty = Mining.nextBlock(funded, MINER, List.of(), "a pun",
			GENESIS_TIME + 2 * STEP);
		SignedTransaction first = postQuantum.getFirst();
		int exactly = CanonicalEncoding.blockSize(empty) + CanonicalEncoding.sizeInBlock(first)
			+ Mining.MINING_SUFFIX_BYTES;

		assertEquals(List.of(first), Mining.fitting(empty, postQuantum, exactly));
		assertEquals(List.of(), Mining.fitting(empty, postQuantum, exactly - 1));
		assertEquals(21, Mining.MINING_SUFFIX_BYTES, "' #' and 19 digits of a long");
	}
}
