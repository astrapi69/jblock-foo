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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.mystic.crypt.key.Ed25519ExpandedPrivateKey;
import io.github.astrapi69.mystic.crypt.key.Ed25519KeyBlinding;

/**
 * The acceptance of #21: money paid to a one-time destination can be moved again by the recipient
 * and by nobody else.
 * <p>
 * The destination is the one-time public key {@code P = S + H(s)*B}, a real Ed25519 public key, so
 * a transfer out of it is verified by the same rule as any other transfer - there is no second
 * spending path in the chain. The recipient signs with the blinded scalar {@code b + H(s) mod l},
 * which only it can compute: the payer knows the shared secret and therefore the tweak, but not
 * the spend private key.
 */
class StealthFundsCanBeSpentTest
{

	private final KeyPair view = OneTimeAddresses.newEphemeralKeyPair();

	private final KeyPair spend = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final PublishedAddress address = PublishedAddress.of(view, spend);

	private final KeyPair payer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final Bytes payerKey = TransactionSigner.asBytes(payer.getPublic());

	@Test
	@DisplayName("the destination is a real public key, and it is the one the blinded key signs with")
	void theDestination_isThePublicKeyOfTheOneTimeKey()
	{
		KeyPair ephemeral = OneTimeAddresses.newEphemeralKeyPair();
		Destination destination = OneTimeAddresses.destinationFor(address, ephemeral);

		Ed25519ExpandedPrivateKey oneTimeKey = OneTimeAddresses.oneTimeKey(destination, address,
			view.getPrivate(), spend.getPrivate());

		assertEquals(AddressScheme.STEALTH_V2, destination.scheme());
		assertEquals(destination.key(),
			TransactionSigner.asBytes(oneTimeKey.publicKey()), "a destination nobody holds the "
				+ "private half of is money nobody can move (#21)");
		assertNotEquals(address.spendKey(), destination.key(),
			"and it is still not the published key itself");
	}

	@Test
	@DisplayName("the recipient spends what was paid to its one-time destination, and the replay accepts it")
	void theRecipient_spendsFromItsOneTimeDestination()
	{
		Destination destination = OneTimeAddresses.destinationFor(address,
			OneTimeAddresses.newEphemeralKeyPair());
		Bytes elsewhere = TransactionSigner
			.asBytes(TransactionSigner.newKeyPair(SignatureSuite.ED25519).getPublic());
		BlockBody genesis = genesis();
		BlockBody paid = block(1L, genesis, transferFromThePayer(destination, Amount.ofLeth(5L)));

		Ed25519ExpandedPrivateKey oneTimeKey = OneTimeAddresses.oneTimeKey(destination, address,
			view.getPrivate(), spend.getPrivate());
		SignedTransaction spending = TransactionSigner.sign(
			new TransactionBody(Chain.IDENTIFIER, 0L, destination.key(),
				Destination.direct(elsewhere), Amount.ofLeth(2L), Amount.ZERO,
				"spent from an address that appeared once"),
			oneTimeKey);
		BlockBody spent = block(2L, paid, spending);

		Replay replay = Replay.verify(CanonicalEncoding
			.readChain(CanonicalEncoding.encodeChain(List.of(genesis, paid, spent))));

		assertEquals(Amount.ofLeth(2L), replay.finalState().balanceOf(elsewhere),
			"the money left the one-time destination, which is what #21 says it could not");
		assertEquals(Amount.ofLeth(3L), replay.finalState().balanceOf(destination.key()));
		assertEquals(Emission.GENESIS_SUPPLY, replay.finalState().total());
	}

	@Test
	@DisplayName("the payer holds the shared secret and still cannot spend what it paid")
	void thePayer_cannotSpendWhatItPaid()
	{
		KeyPair ephemeral = OneTimeAddresses.newEphemeralKeyPair();
		Destination destination = OneTimeAddresses.destinationFor(address, ephemeral);
		byte[] tweak = OneTimeAddresses.tweakFor(address, ephemeral.getPrivate());

		// the payer knows the tweak - it derived the destination with it - and blinds the only
		// Ed25519 key it has, its own, which lands somewhere else entirely
		Ed25519ExpandedPrivateKey payersAttempt = Ed25519KeyBlinding
			.blind((java.security.interfaces.EdECPrivateKey)payer.getPrivate(), tweak);

		assertNotEquals(destination.key(),
			TransactionSigner.asBytes(payersAttempt.publicKey()),
			"blinding the wrong spend key cannot land on the recipient's destination");

		BlockBody genesis = genesis();
		BlockBody paid = block(1L, genesis, transferFromThePayer(destination, Amount.ofLeth(5L)));
		SignedTransaction theft = TransactionSigner.sign(
			new TransactionBody(Chain.IDENTIFIER, 0L, destination.key(),
				Destination.direct(payerKey), Amount.ofLeth(5L), Amount.ZERO, "mine now"),
			payersAttempt);
		BlockBody stolen = block(2L, paid, theft);

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(
			CanonicalEncoding.readChain(
				CanonicalEncoding.encodeChain(List.of(genesis, paid, stolen)))));

		assertTrue(refused.getMessage().contains("signature does not match its sender"),
			refused.getMessage());
	}

	@Test
	@DisplayName("a payment to somebody else's address yields no key for us")
	void aForeignPayment_yieldsNoKey()
	{
		PublishedAddress somebodyElse = PublishedAddress.of(OneTimeAddresses.newEphemeralKeyPair(),
			TransactionSigner.newKeyPair(SignatureSuite.ED25519));
		Destination theirs = OneTimeAddresses.destinationFor(somebodyElse,
			OneTimeAddresses.newEphemeralKeyPair());

		assertThrows(IllegalArgumentException.class, () -> OneTimeAddresses.oneTimeKey(theirs,
			address, view.getPrivate(), spend.getPrivate()));
	}

	@Test
	@DisplayName("a chain carrying a stealth-v1 destination is refused rather than quietly kept")
	void aStealthV1Payment_isRefusedByTheReplay()
	{
		Destination unspendable = new Destination(AddressScheme.STEALTH_V1,
			Bytes.of(SigningPayload.digestOf("a hash, not a key".getBytes())),
			Bytes.of(OneTimeAddresses.newEphemeralKeyPair().getPublic().getEncoded()), 7);
		BlockBody genesis = genesis();
		BlockBody paid = block(1L, genesis, transferFromThePayer(unspendable, Amount.ofLeth(5L)));

		ChainRejected refused = assertThrows(ChainRejected.class, () -> Replay.verify(
			CanonicalEncoding.readChain(CanonicalEncoding.encodeChain(List.of(genesis, paid)))));

		assertTrue(refused.getMessage().contains("private half of nothing"), refused.getMessage());
	}

	@Test
	@DisplayName("a wallet spends what was paid to its own one-time destination")
	void aWallet_spendsWhatWasPaidToIt()
	{
		Wallet payee = Wallet.create();
		Destination destination = OneTimeAddresses.destinationFor(payee.address(),
			OneTimeAddresses.newEphemeralKeyPair());
		BlockBody genesis = genesis();
		BlockBody paid = block(1L, genesis, transferFromThePayer(destination, Amount.ofLeth(4L)));

		SignedTransaction spending = TransactionSigner.sign(
			new TransactionBody(Chain.IDENTIFIER, 0L, destination.key(),
				Destination.direct(payee.spendKey(SignatureSuite.ED25519)), Amount.ofLeth(4L),
				Amount.ZERO, "swept into the account itself"),
			payee.oneTimeKey(destination));
		BlockBody spent = block(2L, paid, spending);

		Replay replay = Replay.verify(CanonicalEncoding
			.readChain(CanonicalEncoding.encodeChain(List.of(genesis, paid, spent))));

		assertEquals(Amount.ofLeth(4L),
			replay.finalState().balanceOf(payee.spendKey(SignatureSuite.ED25519)),
			"the wallet alone, with the chain, moved what was paid to it");
		assertEquals(Amount.ZERO, replay.finalState().balanceOf(destination.key()));
	}

	private SignedTransaction transferFromThePayer(final Destination destination,
		final Amount amount)
	{
		return TransactionSigner.sign(
			new TransactionBody(Chain.IDENTIFIER, 0L, payerKey, destination, amount, Amount.ZERO,
				"paid to an address that appears once"),
			SignatureSuite.ED25519, payer.getPrivate());
	}

	private BlockBody genesis()
	{
		return Blocks
			.mine(new BlockBody(Chain.IDENTIFIER, 0L, Bytes.of(new byte[32]), payerKey,
				new ArrayList<>(), 1_759_000_000_000L, 8, "in the beginning was the pun"),
				1_000_000L)
			.orElseThrow();
	}

	private BlockBody block(final long height, final BlockBody previous,
		final SignedTransaction transfer)
	{
		return Blocks.mine(new BlockBody(Chain.IDENTIFIER, height, Blocks.hashOf(previous),
			payerKey, List.of(transfer), 1_759_000_000_000L + height * 120_000L, 8,
			"block " + height), 1_000_000L).orElseThrow();
	}
}
