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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sweeping: the wallet moves what was paid to its one-time destinations into its own account.
 * <p>
 * One transfer per destination, because each destination IS an account - its own balance, its own
 * nonce, its own one-time key. A single transfer cannot spend two of them, and pretending otherwise
 * is how a sweep silently drops money (#37).
 */
class SweepsTest
{

	private final Wallet payee = Wallet.create();

	private final KeyPair payer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final Bytes payerKey = TransactionSigner.asBytes(payer.getPublic());

	@Test
	@DisplayName("two payments to the same address are swept with one transfer each")
	void twoPayments_areSweptOneByOne()
	{
		Destination first = OneTimeAddresses.destinationFor(payee.address(),
			OneTimeAddresses.newEphemeralKeyPair());
		Destination second = OneTimeAddresses.destinationFor(payee.address(),
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = chainPaying(List.of(payment(first, 0L, Amount.ofLeth(3L)),
			payment(second, 1L, Amount.ofLeth(5L))));

		List<SignedTransaction> sweep = Sweeps.prepare(payee, chain, new ArrayList<>(),
			Amount.ZERO, "swept");

		assertEquals(2, sweep.size(), "one transfer per destination, not one for both");
		assertEquals(List.of(first.key(), second.key()),
			sweep.stream().map(transfer -> transfer.body().sender()).toList());
		assertTrue(sweep.stream().allMatch(TransactionSigner::verify),
			"each is signed with the one-time key of the destination it empties");

		Replay replay = Replay.verify(withABlockOf(chain, sweep));

		assertEquals(Amount.ofLeth(8L),
			replay.finalState().balanceOf(payee.spendKey(SignatureSuite.ED25519)));
		assertEquals(Amount.ZERO, replay.finalState().balanceOf(first.key()));
		assertEquals(Amount.ZERO, replay.finalState().balanceOf(second.key()));
	}

	@Test
	@DisplayName("a fee is taken out of what is swept, not out of what is not there")
	void aFee_comesOutOfTheAmount()
	{
		Destination paid = OneTimeAddresses.destinationFor(payee.address(),
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = chainPaying(List.of(payment(paid, 0L, Amount.ofLeth(4L))));

		List<SignedTransaction> sweep = Sweeps.prepare(payee, chain, new ArrayList<>(),
			Amount.ofLeth(1L), "swept with a fee");

		assertEquals(1, sweep.size());
		assertEquals(Amount.ofLeth(3L), sweep.getFirst().body().amount());
		assertEquals(Amount.ofLeth(1L), sweep.getFirst().body().fee());

		Replay replay = Replay.verify(withABlockOf(chain, sweep));

		assertEquals(Amount.ofLeth(3L),
			replay.finalState().balanceOf(payee.spendKey(SignatureSuite.ED25519)));
		assertEquals(Amount.ZERO, replay.finalState().balanceOf(paid.key()),
			"the destination is emptied: amount plus fee is what it held");
	}

	@Test
	@DisplayName("a destination holding no more than the fee is left alone")
	void aDestinationWorthLessThanTheFee_isLeftAlone()
	{
		Destination dust = OneTimeAddresses.destinationFor(payee.address(),
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = chainPaying(List.of(payment(dust, 0L, Amount.ofLeth(1L))));

		assertEquals(List.of(), Sweeps.prepare(payee, chain, new ArrayList<>(), Amount.ofLeth(1L),
			"nothing to gain"), "moving a destination for exactly its fee gains nothing and "
				+ "publishes the link for free");
	}

	@Test
	@DisplayName("a chain with nothing for us yields nothing to sweep")
	void aChainWithNothingForUs_yieldsNothing()
	{
		PublishedAddress somebodyElse = PublishedAddress.of(OneTimeAddresses.newEphemeralKeyPair(),
			TransactionSigner.newKeyPair(SignatureSuite.ED25519));
		List<BlockBody> chain = chainPaying(List.of(payment(
			OneTimeAddresses.destinationFor(somebodyElse, OneTimeAddresses.newEphemeralKeyPair()),
			0L, Amount.ofLeth(9L))));

		assertEquals(List.of(),
			Sweeps.prepare(payee, chain, new ArrayList<>(), Amount.ZERO, "not ours"));
	}

	@Test
	@DisplayName("a destination a waiting transfer already empties is not swept twice")
	void aDestinationAlreadyWaiting_isNotSweptTwice()
	{
		Destination paid = OneTimeAddresses.destinationFor(payee.address(),
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = chainPaying(List.of(payment(paid, 0L, Amount.ofLeth(6L))));
		List<SignedTransaction> waiting = new ArrayList<>(
			Sweeps.prepare(payee, chain, new ArrayList<>(), Amount.ZERO, "first time"));

		assertEquals(1, waiting.size());
		assertEquals(List.of(), Sweeps.prepare(payee, chain, waiting, Amount.ZERO, "second time"),
			"the money is already on its way; a second transfer from the same destination would "
				+ "reuse its nonce and be refused");
	}

	@Test
	@DisplayName("what a direct payment put on the account is not what a sweep moves")
	void aDirectPayment_isNotSwept()
	{
		List<BlockBody> chain = chainPaying(
			List.of(payment(Destination.direct(payee.spendKey(SignatureSuite.ED25519)), 0L,
				Amount.ofLeth(7L))));

		assertEquals(List.of(),
			Sweeps.prepare(payee, chain, new ArrayList<>(), Amount.ZERO, "already home"));
		assertNotEquals(Amount.ZERO,
			Replay.verify(chain).finalState()
				.balanceOf(payee.spendKey(SignatureSuite.ED25519)),
			"it is already on the account, which is why there is nothing to sweep");
	}

	private SignedTransaction payment(final Destination destination, final long nonce,
		final Amount amount)
	{
		return TransactionSigner.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce, payerKey,
			destination, amount, Amount.ZERO, "paid"), SignatureSuite.ED25519,
			payer.getPrivate());
	}

	private List<BlockBody> chainPaying(final List<SignedTransaction> payments)
	{
		List<BlockBody> chain = new ArrayList<>(List.of(Blocks
			.mine(new BlockBody(Chain.TEST_IDENTIFIER, 0L, Bytes.of(new byte[32]), payerKey,
				new ArrayList<>(), 1_759_000_000_000L, 8, "in the beginning was the pun"),
				1_000_000L)
			.orElseThrow()));
		for (SignedTransaction payment : payments)
		{
			chain.add(block(chain, List.of(payment)));
		}
		return chain;
	}

	private List<BlockBody> withABlockOf(final List<BlockBody> chain,
		final List<SignedTransaction> transfers)
	{
		List<BlockBody> extended = new ArrayList<>(chain);
		extended.add(block(extended, transfers));
		return extended;
	}

	private BlockBody block(final List<BlockBody> chain, final List<SignedTransaction> transfers)
	{
		BlockBody previous = chain.getLast();
		return Blocks.mine(new BlockBody(Chain.TEST_IDENTIFIER, previous.height() + 1L,
			Blocks.hashOf(previous), payerKey, transfers,
			previous.timestamp() + 120_000L, 8, "block " + (previous.height() + 1L)), 1_000_000L)
			.orElseThrow();
	}
}
