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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Milestone 3: two payments to the same published address have nothing visibly in common, and the
 * recipient finds both.
 * <p>
 * This is the protest's substance rather than its slogan. An address that appears on the chain
 * every time it is paid turns a public ledger into a record about a person; a one-time destination
 * does not. What it does NOT hide is stated with it: the amounts are open, and so is the fact that
 * a transfer happened.
 */
class OneTimeAddressesTest
{

	private final KeyPair view = OneTimeAddresses.newEphemeralKeyPair();

	private final KeyPair spend = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final PublishedAddress address = PublishedAddress.of(view, spend);

	@Test
	@DisplayName("two payments to the same address land on unrelated destinations")
	void twoPayments_areUnlinkable()
	{
		Destination first = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		Destination second = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());

		assertNotEquals(first.key(), second.key(),
			"the destination is what the chain shows, and it must not repeat");
		assertNotEquals(first.ephemeralKey(), second.ephemeralKey());
		assertNotEquals(address.spendKey(), first.key(),
			"and neither of them is the published key itself");
	}

	@Test
	@DisplayName("the recipient recognises both, with the view key alone")
	void theRecipient_findsItsOwn()
	{
		Destination first = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		Destination second = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());

		assertTrue(OneTimeAddresses.belongsTo(first, address, view.getPrivate()));
		assertTrue(OneTimeAddresses.belongsTo(second, address, view.getPrivate()),
			"the spending key was never needed - scanning can happen where it does not live");
	}

	@Test
	@DisplayName("somebody else's payment is not recognised as ours")
	void aForeignPayment_isNotOurs()
	{
		PublishedAddress somebodyElse = PublishedAddress.of(OneTimeAddresses.newEphemeralKeyPair(),
			TransactionSigner.newKeyPair(SignatureSuite.ED25519));

		Destination theirs = OneTimeAddresses.destinationFor(somebodyElse,
			OneTimeAddresses.newEphemeralKeyPair());

		assertFalse(OneTimeAddresses.belongsTo(theirs, address, view.getPrivate()));
	}

	@Test
	@DisplayName("a direct destination is never claimed by the stealth check")
	void aDirectDestination_isNotClaimed()
	{
		assertFalse(OneTimeAddresses.belongsTo(Destination.direct(address.spendKey()), address,
			view.getPrivate()));
	}

	@Test
	@DisplayName("the view tag filters without deciding: it matches for ours, rarely for others")
	void theViewTag_isAFilter()
	{
		Destination ours = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());

		assertTrue(ours.viewTag() >= 0 && ours.viewTag() <= Destination.VIEW_TAG_LIMIT);
		assertTrue(OneTimeAddresses.belongsTo(ours, address, view.getPrivate()));

		int falseTags = 0;
		for (int attempt = 0; attempt < 64; attempt++)
		{
			PublishedAddress other = PublishedAddress.of(OneTimeAddresses.newEphemeralKeyPair(),
				TransactionSigner.newKeyPair(SignatureSuite.ED25519));
			Destination theirs = OneTimeAddresses.destinationFor(other,
				OneTimeAddresses.newEphemeralKeyPair());
			if (theirs.viewTag() == ours.viewTag())
			{
				falseTags++;
			}
			assertFalse(OneTimeAddresses.belongsTo(theirs, address, view.getPrivate()),
				"a matching tag never decides: the destination is derived and compared too");
		}
		assertTrue(falseTags <= 8,
			"one byte of tag rules out about 255 of every 256 transactions; " + falseTags
				+ " of 64 collided, which would be a filter that filters nothing");
	}

	@Test
	@DisplayName("a transfer to a one-time destination replays like any other")
	void aStealthTransfer_replays()
	{
		KeyPair sender = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		Bytes senderKey = TransactionSigner.asBytes(sender.getPublic());
		Destination destination = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		SignedTransaction transfer = TransactionSigner.sign(
			new TransactionBody(Chain.IDENTIFIER, 0L, senderKey, destination, Amount.ofLeth(3L),
				Amount.ZERO, "paid to an address that appears once"),
			SignatureSuite.ED25519, sender.getPrivate());

		BlockBody genesis = Blocks
			.mine(new BlockBody(Chain.IDENTIFIER, 0L, Bytes.of(new byte[32]), senderKey,
				new ArrayList<>(), 1_759_000_000_000L, 8, "in the beginning was the pun"),
				1_000_000L)
			.orElseThrow();
		BlockBody second = Blocks.mine(new BlockBody(Chain.IDENTIFIER, 1L, Blocks.hashOf(genesis),
			Bytes.of(new byte[] { 5 }), List.of(transfer), 1_759_000_060_000L, 8,
			"nobody can tell who was paid"), 1_000_000L).orElseThrow();

		Replay replay = Replay.verify(
			CanonicalEncoding.readChain(CanonicalEncoding.encodeChain(List.of(genesis, second))));

		assertEquals(Amount.ofLeth(3L), replay.finalState().balanceOf(destination.key()),
			"the money sits at the one-time destination, which only the recipient can connect to "
				+ "its published address");
		assertEquals(Emission.TOTAL_SUPPLY, replay.finalState().total());
	}
}
