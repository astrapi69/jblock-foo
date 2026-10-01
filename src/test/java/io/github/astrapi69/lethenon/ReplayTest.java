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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The acceptance of milestone 1: a chain produced here is replayed from genesis by code that was
 * given nothing but the bytes, and every hash, signature and transfer is checked again.
 * <p>
 * And the other half, without which a verifier means nothing: a chain that was tampered with has to
 * be refused, with a reason that names what was wrong.
 */
class ReplayTest
{

	private static final int DIFFICULTY = 8;

	private final KeyPair holder = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final KeyPair miner = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private Bytes holderKey()
	{
		return TransactionSigner.asBytes(holder.getPublic());
	}

	private Bytes minerKey()
	{
		return TransactionSigner.asBytes(miner.getPublic());
	}

	private SignedTransaction aTransferOf(final long nonce, final Amount amount)
	{
		TransactionBody body = new TransactionBody(Chain.IDENTIFIER, nonce, holderKey(),
			new Destination(AddressScheme.DIRECT, minerKey(), Bytes.of(new byte[0]), 0), amount,
			Amount.ofLethe(100L), "no permanent record about people");
		return TransactionSigner.sign(body, SignatureSuite.ED25519, holder.getPrivate());
	}

	/** A genesis block and one mined block carrying one transfer */
	private List<BlockBody> aChain(final List<SignedTransaction> transactions)
	{
		BlockBody genesis = Blocks
			.mine(new BlockBody(Chain.IDENTIFIER, 0L, Bytes.of(new byte[32]), new ArrayList<>(),
				1_759_000_000_000L, DIFFICULTY, "in the beginning was the pun"), 1_000_000L)
			.orElseThrow();
		BlockBody second = Blocks.mine(new BlockBody(Chain.IDENTIFIER, 1L, Blocks.hashOf(genesis),
			transactions, 1_759_000_060_000L, DIFFICULTY, "surveillance is not security"), 1_000_000L)
			.orElseThrow();
		return List.of(genesis, second);
	}

	private List<Bytes> miners()
	{
		return List.of(minerKey(), minerKey());
	}

	@Test
	@DisplayName("a chain written here is replayed from its bytes alone, and the counts are reported")
	void aChain_isReplayedFromItsBytes()
	{
		List<BlockBody> chain = aChain(List.of(aTransferOf(0L, Amount.ofLeth(5L))));

		// nothing but the bytes crosses to the verifier, the way a file would
		byte[] file = CanonicalEncoding.encodeChain(chain);
		List<BlockBody> readBack = CanonicalEncoding.readChain(file);

		Replay replay = Replay.verify(readBack, miners(), holderKey());

		assertEquals(2L, replay.blocks());
		assertEquals(1L, replay.transactions());
		assertEquals(1L, replay.signatures());
		assertEquals(Emission.TOTAL_SUPPLY, replay.finalState().total(),
			"the sum of every balance is the supply, at every height - nothing is ever minted");
		assertTrue(replay.describe().contains("replayed 2 blocks"), replay.describe());
	}

	@Test
	@DisplayName("the miner is paid out of the pool, and the pool is shorter by exactly that")
	void theReward_comesOutOfThePool()
	{
		Replay replay = Replay.verify(aChain(new ArrayList<>()), miners(), holderKey());

		assertEquals(Emission.BLOCK_REWARD, replay.finalState().balanceOf(minerKey()),
			"one block after genesis, so one reward");
		assertEquals(Emission.MINING_POOL.minus(Emission.BLOCK_REWARD),
			replay.finalState().balanceOf(ChainState.POOL));
	}

	@Test
	@DisplayName("a tampered transfer is refused, and the reason names the signature")
	void aTamperedTransfer_isRefused()
	{
		SignedTransaction honest = aTransferOf(0L, Amount.ofLeth(5L));
		TransactionBody tampered = new TransactionBody(honest.body().chainIdentifier(),
			honest.body().nonce(), honest.body().sender(), honest.body().recipient(),
			Amount.ofLeth(500L), honest.body().fee(), honest.body().memo());
		List<BlockBody> chain = aChain(
			List.of(new SignedTransaction(tampered, honest.suite(), honest.signature())));

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, miners(), holderKey()));

		assertTrue(refused.getMessage().contains("signature"), refused.getMessage());
	}

	@Test
	@DisplayName("a block that was not mined is refused, with both numbers in the reason")
	void anUnminedBlock_isRefused()
	{
		BlockBody genesis = new BlockBody(Chain.IDENTIFIER, 0L, Bytes.of(new byte[32]),
			new ArrayList<>(), 1_759_000_000_000L, 24, "nobody looked for this one");

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(genesis), List.of(minerKey()), holderKey()));

		assertTrue(refused.getMessage().contains("not mined"), refused.getMessage());
		assertTrue(refused.getMessage().contains("24"), refused.getMessage());
	}

	@Test
	@DisplayName("a block that names the wrong predecessor is refused")
	void aBrokenLink_isRefused()
	{
		List<BlockBody> chain = aChain(new ArrayList<>());
		BlockBody second = chain.get(1);
		BlockBody detached = Blocks
			.mine(new BlockBody(second.chainIdentifier(), 1L, Bytes.of(new byte[32]),
				second.transactions(), second.timestamp(), second.difficulty(), "a loose block"),
				1_000_000L)
			.orElseThrow();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(chain.get(0), detached), miners(), holderKey()));

		assertTrue(refused.getMessage().contains("previous hash"), refused.getMessage());
	}

	@Test
	@DisplayName("a transfer replayed under the next nonce is refused")
	void aReplayedTransfer_isRefused()
	{
		SignedTransaction once = aTransferOf(0L, Amount.ofLeth(5L));
		List<BlockBody> chain = aChain(List.of(once, once));

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, miners(), holderKey()));

		assertTrue(refused.getMessage().contains("nonce"), refused.getMessage());
	}

	@Test
	@DisplayName("a transfer of more than the account holds is refused")
	void anOverdraft_isRefused()
	{
		List<BlockBody> chain = aChain(List.of(aTransferOf(0L, Emission.TOTAL_SUPPLY)));

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, miners(), holderKey()));

		assertTrue(refused.getMessage().contains("holding"), refused.getMessage());
	}
}
