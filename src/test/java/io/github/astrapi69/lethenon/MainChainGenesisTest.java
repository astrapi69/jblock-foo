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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The genesis block of {@code lethenon-2}, fixed in the code (#137, ADR 0005): every main chain
 * starts with it, it is paid the first block reward like every block (#111), and that reward
 * belongs to nobody, because the block's beneficiary is words that no signature can verify for.
 */
class MainChainGenesisTest
{

	private static final BlockBody ANCHOR = ConsensusRules.LETHENON.anchorFor(Chain.IDENTIFIER)
		.orElseThrow();

	@Test
	@DisplayName("the anchor is the block mined on 2026-10-08, by its hash")
	void theAnchor_isTheBlockItsHashNames()
	{
		assertEquals("009cffa549366d12a7f223be0e90821b7155f215a57ee3b6eed9d1e98cd04e1f",
			Blocks.hashOf(ANCHOR).toString());
		assertEquals(Chain.IDENTIFIER, ANCHOR.chainIdentifier());
		assertEquals(0L, ANCHOR.height());
		assertEquals(List.of(), ANCHOR.transactions());
		assertTrue(ANCHOR.pun().startsWith("in the beginning was the pun"), ANCHOR.pun());
	}

	@Test
	@DisplayName("its beneficiary is nobody: words, not a key")
	void theBeneficiary_isNobody()
	{
		assertEquals(Genesis.NOBODY, ANCHOR.beneficiary());
		assertEquals("nobody holds the genesis reward of lethenon-2",
			new String(Genesis.NOBODY.toByteArray(), StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("replayed alone, nobody holds the first reward and the pool the rest")
	void replayedAlone_nobodyHoldsTheFirstReward()
	{
		ChainState state = Replay.verify(List.of(ANCHOR)).finalState();

		assertEquals(Emission.FIRST_REWARD, state.balanceOf(Genesis.NOBODY));
		assertEquals(Emission.GENESIS_SUPPLY.minus(Emission.FIRST_REWARD),
			state.balanceOf(ChainState.POOL));
		assertEquals(Emission.GENESIS_SUPPLY, state.total());
	}

	@Test
	@DisplayName("every chain starts with it: Genesis.start returns it, whoever asks")
	void everyMainChain_startsWithIt()
	{
		BlockBody started = Genesis.start(Chain.IDENTIFIER, Bytes.of(new byte[] { 7 }), "mine",
			System.currentTimeMillis());

		assertEquals(Blocks.hashOf(ANCHOR), Blocks.hashOf(started));
	}

	@Test
	@DisplayName("a main chain with another genesis block is refused, naming both hashes")
	void anotherMainGenesis_isRefused()
	{
		BlockBody other = Blocks.mine(Mining.nextBlock(Chain.IDENTIFIER, List.of(),
			Bytes.of(new byte[] { 7 }), List.of(), "a genesis of my own", ANCHOR.timestamp()),
			1_000_000L).orElseThrow();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(List.of(other)));

		assertTrue(refused.getMessage().contains(Blocks.hashOf(ANCHOR).toString()),
			refused.getMessage());
		assertTrue(refused.getMessage().contains(Blocks.hashOf(other).toString()),
			refused.getMessage());
	}

	@ParameterizedTest(name = "no {0} signature verifies for nobody")
	@EnumSource(SignatureSuite.class)
	void noSignature_verifiesForNobody(final SignatureSuite suite)
	{
		TransactionBody spending = new TransactionBody(Chain.IDENTIFIER, 0L, Genesis.NOBODY,
			Destination.direct(Bytes.of(new byte[] { 7 })), Amount.ofLeth(1L), Amount.ZERO,
			"the genesis reward");
		SignedTransaction signedBySomebody = TransactionSigner.sign(spending, suite,
			TransactionSigner.newKeyPair(suite).getPrivate());

		assertFalse(TransactionSigner.verify(signedBySomebody),
			"the beneficiary does not decode as a " + suite + " public key");
	}

	@Test
	@DisplayName("a block spending the genesis reward does not verify")
	void spendingTheGenesisReward_isRefused()
	{
		TransactionBody spending = new TransactionBody(Chain.IDENTIFIER, 0L, Genesis.NOBODY,
			Destination.direct(Bytes.of(new byte[] { 7 })), Amount.ofLeth(1L), Amount.ZERO,
			"the genesis reward");
		SignedTransaction forged = TransactionSigner.sign(spending, SignatureSuite.ED25519,
			TransactionSigner.newKeyPair(SignatureSuite.ED25519).getPrivate());
		List<BlockBody> chain = new ArrayList<>(List.of(ANCHOR));
		chain.add(Blocks.mine(Mining.nextBlock(chain, Bytes.of(new byte[] { 7 }), List.of(forged),
			"spend it", ANCHOR.timestamp() + DifficultyRule.TARGET_BLOCK_MILLIS), 1_000_000L)
			.orElseThrow());

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(chain));

		assertTrue(refused.getMessage().contains("signature does not match its sender"),
			refused.getMessage());
	}
}
