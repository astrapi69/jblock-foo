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
		TransactionBody body = new TransactionBody(Chain.TEST_IDENTIFIER, nonce, holderKey(),
			new Destination(AddressScheme.DIRECT, minerKey(), Bytes.of(new byte[0]), 0), amount,
			Amount.ofLethe(100L), "no permanent record about people");
		return TransactionSigner.sign(body, SignatureSuite.ED25519, holder.getPrivate());
	}

	/**
	 * A genesis block paying the holder, one mined block carrying the transfers and paying the
	 * miner, and a third, empty one on top, so that a change to the second block is caught by the
	 * link to the third even in the one case in 256 where the changed block still happens to look
	 * mined
	 */
	private List<BlockBody> aChain(final List<SignedTransaction> transactions)
	{
		BlockBody genesis = Blocks
			.mine(new BlockBody(Chain.TEST_IDENTIFIER, 0L, Bytes.of(new byte[32]), holderKey(),
				new ArrayList<>(), 1_759_000_000_000L, DIFFICULTY, "in the beginning was the pun"),
				1_000_000L)
			.orElseThrow();
		BlockBody second = Blocks.mine(new BlockBody(Chain.TEST_IDENTIFIER, 1L, Blocks.hashOf(genesis),
			minerKey(), transactions, 1_759_000_060_000L, DIFFICULTY,
			"surveillance is not security"), 1_000_000L).orElseThrow();
		BlockBody third = Blocks.mine(new BlockBody(Chain.TEST_IDENTIFIER, 2L, Blocks.hashOf(second),
			minerKey(), new ArrayList<>(), 1_759_000_120_000L, DIFFICULTY, "and nothing to hide"),
			1_000_000L).orElseThrow();
		return List.of(genesis, second, third);
	}

	private static BlockBody paying(final BlockBody block, final Bytes beneficiary)
	{
		return new BlockBody(block.chainIdentifier(), block.height(), block.previousHash(),
			beneficiary, block.transactions(), block.timestamp(), block.difficulty(), block.pun());
	}

	@Test
	@DisplayName("a chain written here is replayed from its bytes alone, and the counts are reported")
	void aChain_isReplayedFromItsBytes()
	{
		List<BlockBody> chain = aChain(List.of(aTransferOf(0L, Amount.ofLeth(5L))));

		// nothing but the bytes crosses to the verifier, the way a file would
		byte[] file = CanonicalEncoding.encodeChain(chain);
		List<BlockBody> readBack = CanonicalEncoding.readChain(file);

		Replay replay = Replay.verify(readBack);

		assertEquals(3L, replay.blocks());
		assertEquals(1L, replay.transactions());
		assertEquals(1L, replay.signatures());
		assertEquals(Emission.GENESIS_SUPPLY, replay.finalState().total(),
			"the sum of every balance is the supply, at every height - and before the tail nothing "
				+ "is minted");
		assertTrue(replay.describe().contains("replayed 3 blocks"), replay.describe());
	}

	@Test
	@DisplayName("the miner is paid out of the pool, and the pool is shorter by exactly that")
	void theReward_comesOutOfThePool()
	{
		Replay replay = Replay.verify(aChain(new ArrayList<>()));

		assertEquals(TestChains.rewardOfBlock(1).plus(TestChains.rewardOfBlock(2)),
			replay.finalState().balanceOf(minerKey()), "two blocks after genesis, two rewards");
		assertEquals(Emission.MINING_POOL.minus(TestChains.rewardOfBlock(0))
			.minus(TestChains.rewardOfBlock(1)).minus(TestChains.rewardOfBlock(2)),
			replay.finalState().balanceOf(ChainState.POOL),
			"three blocks, the genesis block included, three rewards out of the pool");
	}

	@Test
	@DisplayName("the genesis block names who is paid its block reward")
	void theGenesisBlock_namesItsHolder()
	{
		Replay replay = Replay.verify(aChain(new ArrayList<>()));

		assertEquals(Emission.FIRST_REWARD, replay.finalState().balanceOf(holderKey()),
			"the genesis block pays the ordinary reward, and no share of the supply (#111)");
	}

	@Test
	@DisplayName("a block that pays somebody else is not the block that was mined")
	void aBlockPayingSomebodyElse_isRefused()
	{
		List<BlockBody> chain = new ArrayList<>(aChain(new ArrayList<>()));
		chain.set(1, paying(chain.get(1), Bytes.of("somebody else".getBytes())));

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertTrue(refused.getMessage().matches("block 1 is not mined.*|block 2 names .*"),
			refused.getMessage());
	}

	@Test
	@DisplayName("a genesis block that allocates to somebody else is not the genesis of this chain")
	void aGenesisAllocatingToSomebodyElse_isRefused()
	{
		List<BlockBody> chain = new ArrayList<>(aChain(new ArrayList<>()));
		chain.set(0, paying(chain.get(0), minerKey()));

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertTrue(refused.getMessage().matches("block 0 is not mined.*|block 1 names .*"),
			refused.getMessage());
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
			() -> Replay.verify(chain));

		assertTrue(refused.getMessage().contains("signature"), refused.getMessage());
	}

	@Test
	@DisplayName("a block that was not mined is refused, with both numbers in the reason")
	void anUnminedBlock_isRefused()
	{
		BlockBody genesis = anUnminedGenesis();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(genesis)));

		assertTrue(refused.getMessage().contains("not mined"), refused.getMessage());
		assertTrue(refused.getMessage().contains("asks for " + DifficultyRule.MINIMUM),
			refused.getMessage());
	}

	@Test
	@DisplayName("a block of difficulty 0 is refused: the rule, not the miner, says how hard")
	void aBlockOfDifficultyZero_isRefused()
	{
		BlockBody genesis = Blocks.mine(new BlockBody(Chain.TEST_IDENTIFIER, 0L, Bytes.of(new byte[32]),
			holderKey(), new ArrayList<>(), 1_759_000_000_000L, 0, "no work at all"), 1L)
			.orElseThrow();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(genesis)));

		assertTrue(refused.getMessage().contains("declares difficulty 0"), refused.getMessage());
		assertTrue(refused.getMessage().contains("requires 8"), refused.getMessage());
	}

	@Test
	@DisplayName("a timestamp that is not after the median of the ones before is refused")
	void aBackdatedBlock_isRefused()
	{
		BlockBody genesis = aChain(new ArrayList<>()).getFirst();
		BlockBody backdated = Blocks.mine(new BlockBody(Chain.TEST_IDENTIFIER, 1L,
			Blocks.hashOf(genesis), minerKey(), new ArrayList<>(), genesis.timestamp(),
			DifficultyRule.MINIMUM, "same time as genesis"), 1_000_000L).orElseThrow();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(genesis, backdated)));

		assertTrue(refused.getMessage().contains("median"), refused.getMessage());
	}

	@Test
	@DisplayName("after thirty blocks at a second each, block 30 has to be two bits harder")
	void theRetarget_isEnforced()
	{
		List<BlockBody> fast = new ArrayList<>();
		for (int height = 0; height < DifficultyRule.INTERVAL; height++)
		{
			fast.add(mined(fast, height, DifficultyRule.MINIMUM));
		}
		List<BlockBody> unchanged = new ArrayList<>(fast);
		unchanged.add(mined(fast, DifficultyRule.INTERVAL, DifficultyRule.MINIMUM));
		List<BlockBody> retargeted = new ArrayList<>(fast);
		retargeted.add(mined(fast, DifficultyRule.INTERVAL, DifficultyRule.MINIMUM + 2));

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(unchanged));
		Replay replay = Replay.verify(retargeted);

		assertTrue(refused.getMessage().contains("block 30 declares difficulty 8"),
			refused.getMessage());
		assertTrue(refused.getMessage().contains("requires 10"), refused.getMessage());
		assertEquals(31L, replay.blocks(), replay.describe());
	}

	@Test
	@DisplayName("a block that names the wrong predecessor is refused")
	void aBrokenLink_isRefused()
	{
		List<BlockBody> chain = aChain(new ArrayList<>());
		BlockBody second = chain.get(1);
		BlockBody detached = Blocks
			.mine(new BlockBody(second.chainIdentifier(), 1L, Bytes.of(new byte[32]),
				second.beneficiary(), second.transactions(), second.timestamp(),
				second.difficulty(), "a loose block"), 1_000_000L)
			.orElseThrow();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(chain.get(0), detached)));

		assertTrue(refused.getMessage().contains("previous hash"), refused.getMessage());
	}

	@Test
	@DisplayName("a transfer replayed under the next nonce is refused")
	void aReplayedTransfer_isRefused()
	{
		SignedTransaction once = aTransferOf(0L, Amount.ofLeth(5L));
		List<BlockBody> chain = aChain(List.of(once, once));

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain));

		assertTrue(refused.getMessage().contains("nonce"), refused.getMessage());
	}

	@Test
	@DisplayName("a transfer of more than the account holds is refused")
	void anOverdraft_isRefused()
	{
		List<BlockBody> chain = aChain(List.of(aTransferOf(0L, Emission.GENESIS_SUPPLY)));

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain));

		assertTrue(refused.getMessage().contains("holding"), refused.getMessage());
	}

	/** A genesis block of the right difficulty whose hash happens not to meet it */
	private BlockBody anUnminedGenesis()
	{
		for (int attempt = 0;; attempt++)
		{
			BlockBody candidate = new BlockBody(Chain.TEST_IDENTIFIER, 0L, Bytes.of(new byte[32]),
				holderKey(), new ArrayList<>(), 1_759_000_000_000L, DifficultyRule.MINIMUM,
				"nobody looked for this one #" + attempt);
			if (!Blocks.isMined(candidate))
			{
				return candidate;
			}
		}
	}

	/** The next block of a chain, one second after the one before, mined at a difficulty */
	private BlockBody mined(final List<BlockBody> before, final int height, final int difficulty)
	{
		Bytes previous = before.isEmpty()
			? Bytes.of(new byte[32])
			: Blocks.hashOf(before.getLast());
		Bytes beneficiary = before.isEmpty() ? holderKey() : minerKey();
		return Blocks.mine(new BlockBody(Chain.TEST_IDENTIFIER, height, previous, beneficiary,
			new ArrayList<>(), 1_759_000_000_000L + 1_000L * height, difficulty, "fast"),
			1_000_000L).orElseThrow();
	}
}
