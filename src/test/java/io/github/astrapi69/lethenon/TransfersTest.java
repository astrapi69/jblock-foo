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

import org.junit.jupiter.api.Test;

/**
 * A transfer is prepared against the chain AND the transfers already waiting for the next block
 * (lethenon#32): its nonce comes after theirs, and what they spend is no longer available to it.
 */
class TransfersTest
{

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	private final Wallet sender = Wallet.create();

	private final Bytes account = sender.spendKey(SignatureSuite.ED25519);

	private final List<BlockBody> chain = TestChains.chainWith(account);

	@Test
	void theFirstTransferOfAnAccountTakesTheChainsNextNonce_andIsSignedByIt()
	{
		SignedTransaction prepared = Transfers.prepare(sender, SignatureSuite.ED25519, chain,
			List.of(), SOMEONE, Amount.ofLeth(3L), Amount.ZERO, "a memo");

		assertEquals(0L, prepared.body().nonce());
		assertEquals(account, prepared.body().sender());
		assertEquals("a memo", prepared.body().memo());
		assertTrue(TransactionSigner.verify(prepared), "signed by the wallet's own account");
	}

	@Test
	void aSecondTransferWhileTheFirstWaitsTakesTheNonceAfterIt()
	{
		List<SignedTransaction> waiting = new ArrayList<>();
		waiting.add(Transfers.prepare(sender, SignatureSuite.ED25519, chain, waiting, SOMEONE,
			Amount.ofLeth(3L), Amount.ZERO, "first"));

		SignedTransaction second = Transfers.prepare(sender, SignatureSuite.ED25519, chain,
			waiting, SOMEONE, Amount.ofLeth(4L), Amount.ZERO, "second");

		assertEquals(1L, second.body().nonce());
	}

	@Test
	void whatTheWaitingTransfersSpendIsNotAvailableAgain()
	{
		Amount holding = Replay.verify(chain).finalState().balanceOf(account);
		List<SignedTransaction> waiting = List.of(Transfers.prepare(sender, SignatureSuite.ED25519,
			chain, List.of(), SOMEONE, holding.minus(Amount.ofLeth(1L)), Amount.ZERO, "almost all"));

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Transfers.prepare(sender, SignatureSuite.ED25519, chain, waiting, SOMEONE,
				Amount.ofLeth(2L), Amount.ZERO, "one too many"));

		assertTrue(refused.getMessage().contains("after the transfers already waiting"),
			refused.getMessage());
	}

	@Test
	void theFeeCountsTowardsWhatIsNeeded()
	{
		Amount holding = Replay.verify(chain).finalState().balanceOf(account);

		assertThrows(IllegalArgumentException.class,
			() -> Transfers.prepare(sender, SignatureSuite.ED25519, chain, List.of(), SOMEONE,
				holding, Amount.ofLethe(1L), "exactly the holding, plus a fee"));
	}

	@Test
	void aChainThatDoesNotVerifyPreparesNothing()
	{
		List<BlockBody> tampered = new ArrayList<>(TestChains.chainWith(account,
			Transfers.prepare(sender, SignatureSuite.ED25519, chain, List.of(), SOMEONE,
				Amount.ofLeth(1L), Amount.ZERO, "in a block")));
		BlockBody last = tampered.getLast();
		tampered.set(1, new BlockBody(last.chainIdentifier(), last.height(), Bytes.of(new byte[32]),
			last.beneficiary(), last.transactions(), last.timestamp(), last.difficulty(),
			last.pun()));

		assertThrows(ChainRejected.class, () -> Transfers.prepare(sender, SignatureSuite.ED25519,
			tampered, List.of(), SOMEONE, Amount.ofLeth(1L), Amount.ZERO, "on a broken chain"));
	}
}
