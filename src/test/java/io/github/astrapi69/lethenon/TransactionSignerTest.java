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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a signature authorises, and every way it does not.
 * <p>
 * The public key is the transfer's own sender field, so a verifier looks nothing up: the
 * transaction says who claims to have signed it, and either the signature matches that claim over
 * exactly these bytes, or the transaction is refused.
 */
class TransactionSignerTest
{

	private static TransactionBody aTransferFrom(final KeyPair signer, final long nonce)
	{
		return new TransactionBody(Chain.IDENTIFIER, nonce,
			TransactionSigner.asBytes(signer.getPublic()), Bytes.of(new byte[] { 7, 7 }),
			Amount.ofLeth(3L), Amount.ofLethe(10L), "watching is not protecting");
	}

	@Test
	@DisplayName("a transfer signed by its sender verifies")
	void aSignedTransfer_verifies()
	{
		KeyPair signer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

		SignedTransaction signed = TransactionSigner.sign(aTransferFrom(signer, 1L),
			SignatureSuite.ED25519, signer.getPrivate());

		assertTrue(TransactionSigner.verify(signed));
		assertEquals(64, signed.signature().length(),
			"an Ed25519 signature is 64 bytes - the number ML-DSA-65 will be measured against in "
				+ "milestone 2");
	}

	@Test
	@DisplayName("a changed memo makes the signature invalid")
	void aChangedMemo_breaksTheSignature()
	{
		KeyPair signer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		TransactionBody body = aTransferFrom(signer, 1L);
		SignedTransaction signed = TransactionSigner.sign(body, SignatureSuite.ED25519,
			signer.getPrivate());

		SignedTransaction tampered = new SignedTransaction(
			new TransactionBody(body.chainIdentifier(), body.nonce(), body.sender(),
				body.recipient(), body.amount(), body.fee(), body.memo() + " (edited)"),
			signed.suite(), signed.signature());

		assertFalse(TransactionSigner.verify(tampered),
			"the memo is signed with the transfer, which is what makes the protest part of what "
				+ "gets verified");
	}

	@Test
	@DisplayName("a changed amount makes the signature invalid")
	void aChangedAmount_breaksTheSignature()
	{
		KeyPair signer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		TransactionBody body = aTransferFrom(signer, 1L);
		SignedTransaction signed = TransactionSigner.sign(body, SignatureSuite.ED25519,
			signer.getPrivate());

		SignedTransaction tampered = new SignedTransaction(
			new TransactionBody(body.chainIdentifier(), body.nonce(), body.sender(),
				body.recipient(), Amount.ofLeth(4L), body.fee(), body.memo()),
			signed.suite(), signed.signature());

		assertFalse(TransactionSigner.verify(tampered));
	}

	@Test
	@DisplayName("a signature from another key does not authorise this sender")
	void anotherKeysSignature_isRefused()
	{
		KeyPair sender = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		KeyPair somebodyElse = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		TransactionBody body = aTransferFrom(sender, 1L);

		SignedTransaction signed = TransactionSigner.sign(body, SignatureSuite.ED25519,
			somebodyElse.getPrivate());

		assertFalse(TransactionSigner.verify(signed),
			"the key that signs has to be the key the transfer names as its sender");
	}

	@Test
	@DisplayName("a signature does not carry from one nonce to the next")
	void aSignature_doesNotCarryToAnotherNonce()
	{
		KeyPair signer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		SignedTransaction signed = TransactionSigner.sign(aTransferFrom(signer, 1L),
			SignatureSuite.ED25519, signer.getPrivate());

		SignedTransaction replayed = new SignedTransaction(aTransferFrom(signer, 2L),
			signed.suite(), signed.signature());

		assertFalse(TransactionSigner.verify(replayed),
			"otherwise one signature would pay again under the next nonce");
	}

	@Test
	@DisplayName("Ed25519 signs deterministically, so the same transfer gives the same signature")
	void theSignature_isDeterministic()
	{
		KeyPair signer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		TransactionBody body = aTransferFrom(signer, 5L);

		assertArrayEquals(
			TransactionSigner.sign(body, SignatureSuite.ED25519, signer.getPrivate()).signature()
				.toByteArray(),
			TransactionSigner.sign(body, SignatureSuite.ED25519, signer.getPrivate()).signature()
				.toByteArray(),
			"a scheme that needs randomness per signature needs that randomness to be good; "
				+ "Ed25519 needs none, which is one reason it is the default here");
	}

	@Test
	@DisplayName("a suite this build does not know is refused, never ignored")
	void anUnknownSuite_isRefused()
	{
		assertThrows(IllegalArgumentException.class,
			() -> SignatureSuite.withIdentifier("dilithium-from-the-future"));
		assertEquals(SignatureSuite.ED25519, SignatureSuite.withIdentifier("ed25519"));
	}

	@Test
	@DisplayName("nonsense in place of a signature is refused rather than thrown out of")
	void nonsense_isRefusedQuietly()
	{
		KeyPair signer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

		SignedTransaction nonsense = new SignedTransaction(aTransferFrom(signer, 1L),
			SignatureSuite.ED25519, Bytes.of(new byte[] { 1, 2, 3 }));

		assertFalse(TransactionSigner.verify(nonsense),
			"everything that arrives from a network is hostile input, and a verifier answers no "
				+ "rather than crashing");
	}
}
