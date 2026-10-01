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

import static io.github.astrapi69.lethenon.TestChains.chainWith;
import static io.github.astrapi69.lethenon.TestChains.minersFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Milestone 3: the wallet answers "how much do I have" from the chain it has, and asks nobody.
 * <p>
 * A balance query is the one message a wallet sends that says exactly who it is: this address, from
 * this address, now. It is also the message nearly every wallet sends, because scanning a chain is
 * more work than asking a server. Here the work is done instead, and the test that this is possible
 * at all is the acceptance of milestone 3 (lethenon#2).
 */
class WalletScanTest
{

	private final KeyPair view = OneTimeAddresses.newEphemeralKeyPair();

	private final KeyPair spend = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final PublishedAddress address = PublishedAddress.of(view, spend);

	private final KeyPair sender = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final Bytes senderKey = TransactionSigner.asBytes(sender.getPublic());

	private final PrivateKey viewPrivateKey = view.getPrivate();

	@Test
	@DisplayName("the wallet finds both payments and adds them up, from the chain alone")
	void scan_findsEveryPayment_andTheirSum()
	{
		Destination first = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		Destination second = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = chainWith(
			transfer(0L, first, Amount.ofLeth(3L), "the first of two"),
			transfer(1L, second, Amount.ofLeth(5L), "and the second"));

		WalletScan scan = WalletScan.over(chain, minersFor(chain), senderKey, address,
			viewPrivateKey);

		assertEquals(2, scan.received().size(), scan.describe());
		assertEquals(Amount.ofLeth(8L), scan.balance(),
			"the balance is the sum of what the replayed state holds at each destination");
		assertEquals(List.of("the first of two", "and the second"),
			scan.received().stream().map(WalletScan.Received::memo).toList());
		assertEquals(List.of(first.key(), second.key()),
			scan.received().stream().map(WalletScan.Received::destination).toList());
	}

	@Test
	@DisplayName("somebody else's payment is not in our balance")
	void scan_leavesForeignPayments_whereTheyAre()
	{
		PublishedAddress somebodyElse = PublishedAddress.of(OneTimeAddresses.newEphemeralKeyPair(),
			TransactionSigner.newKeyPair(SignatureSuite.ED25519));
		Destination ours = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		Destination theirs = OneTimeAddresses.destinationFor(somebodyElse,
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = chainWith(transfer(0L, theirs, Amount.ofLeth(11L), "not for us"),
			transfer(1L, ours, Amount.ofLeth(4L), "this one is"));

		WalletScan scan = WalletScan.over(chain, minersFor(chain), senderKey, address,
			viewPrivateKey);

		assertEquals(1, scan.received().size(), scan.describe());
		assertEquals(Amount.ofLeth(4L), scan.balance());
	}

	@Test
	@DisplayName("a direct transfer to the published key is not a one-time payment")
	void scan_doesNotClaim_aDirectTransfer()
	{
		List<BlockBody> chain = chainWith(
			transfer(0L, Destination.direct(address.spendKey()), Amount.ofLeth(7L), "in the open"));

		WalletScan scan = WalletScan.over(chain, minersFor(chain), senderKey, address,
			viewPrivateKey);

		assertEquals(List.of(), scan.received());
		assertEquals(Amount.ZERO, scan.balance());
	}

	@Test
	@DisplayName("a balance is never reported from a chain that does not verify")
	void scan_refuses_aChainThatDoesNotReplay()
	{
		Destination ours = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		List<BlockBody> chain = chainWith(transfer(0L, ours, Amount.ofLeth(6L), "paid"));
		List<BlockBody> tampered = new ArrayList<>(chain);
		BlockBody last = tampered.getLast();
		tampered.set(tampered.size() - 1, new BlockBody(last.chainIdentifier(), last.height(),
			Bytes.of(new byte[32]), last.transactions(), last.timestamp(), last.difficulty(),
			last.pun()));

		ChainRejected refused = assertThrows(ChainRejected.class, () -> WalletScan.over(tampered,
			minersFor(tampered), senderKey, address, viewPrivateKey));

		assertTrue(refused.getMessage().contains("previous hash"), refused.getMessage());
	}

	@Test
	@DisplayName("the scan reports what it read, so an empty answer cannot pass for a zero balance")
	void scan_reportsWhatItRead()
	{
		List<BlockBody> chain = chainWith(transfer(0L,
			OneTimeAddresses.destinationFor(address, OneTimeAddresses.newEphemeralKeyPair()),
			Amount.ofLeth(2L), "one transfer"));

		WalletScan scan = WalletScan.over(chain, minersFor(chain), senderKey, address,
			viewPrivateKey);

		assertEquals(chain.size(), scan.blocksRead());
		assertEquals(1L, scan.transactionsRead());
		assertTrue(scan.describe().contains(String.valueOf(scan.transactionsRead())),
			scan.describe());
	}

	private SignedTransaction transfer(final long nonce, final Destination recipient,
		final Amount amount, final String memo)
	{
		return TransactionSigner.sign(new TransactionBody(Chain.IDENTIFIER, nonce, senderKey,
			recipient, amount, Amount.ZERO, memo), SignatureSuite.ED25519, sender.getPrivate());
	}
}
