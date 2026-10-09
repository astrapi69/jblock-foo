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

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Milestone 2: the same chain carries both signature suites.
 * <p>
 * "Harvest now, decrypt later" - record today's traffic and break it when the machine exists - is a
 * surveillance practice rather than a theory, and ML-DSA-65 (FIPS 204) is the answer to it. The
 * price is in the numbers below, printed by the test rather than claimed in prose: a post-quantum
 * signature is fifty times the size of an Ed25519 one.
 */
class PostQuantumSuiteTest
{

	private static SignedTransaction aTransferSignedWith(final SignatureSuite suite,
		final long nonce)
	{
		KeyPair signer = TransactionSigner.newKeyPair(suite);
		TransactionBody body = new TransactionBody(Chain.TEST_IDENTIFIER, nonce,
			TransactionSigner.asBytes(signer.getPublic()),
			new Destination(AddressScheme.DIRECT, Bytes.of(new byte[] { 9 }), Bytes.of(new byte[0]),
				0),
			Amount.ofLeth(1L), Amount.ZERO, "harvest now, decrypt later is a practice");
		return TransactionSigner.sign(body, suite, signer.getPrivate());
	}

	@Test
	@DisplayName("a transfer signed with ML-DSA-65 verifies, and a tampered one does not")
	void aPostQuantumTransfer_verifies()
	{
		SignedTransaction signed = aTransferSignedWith(SignatureSuite.ML_DSA_65, 0L);

		assertTrue(TransactionSigner.verify(signed));

		SignedTransaction tampered = new SignedTransaction(
			new TransactionBody(signed.body().chainIdentifier(), signed.body().nonce(),
				signed.body().sender(), signed.body().recipient(), Amount.ofLeth(2L),
				signed.body().fee(), signed.body().memo()),
			signed.suite(), signed.signature());
		assertFalse(TransactionSigner.verify(tampered));
	}

	@Test
	@DisplayName("the two suites cost what they cost, measured rather than claimed")
	void theSizes_areMeasured()
	{
		SignedTransaction classical = aTransferSignedWith(SignatureSuite.ED25519, 0L);
		SignedTransaction postQuantum = aTransferSignedWith(SignatureSuite.ML_DSA_65, 0L);

		int classicalSignature = classical.signature().length();
		int postQuantumSignature = postQuantum.signature().length();
		System.out.println("signature: ed25519 " + classicalSignature + " bytes, ml-dsa-65 "
			+ postQuantumSignature + " bytes; sender key: ed25519 "
			+ classical.body().sender().length() + " bytes, ml-dsa-65 "
			+ postQuantum.body().sender().length() + " bytes");

		assertEquals(64, classicalSignature);
		assertEquals(3309, postQuantumSignature,
			"FIPS 204 fixes this at 3309 bytes; OpenSSL 3.5.5 measures the same");
		assertTrue(postQuantum.body().sender().length() > classical.body().sender().length() * 40,
			"the key grows with it, which is why this chain signs once per transaction rather "
				+ "than once per input");
	}

	@Test
	@DisplayName("a signature made for one suite does not verify as the other")
	void aSuite_cannotBeReinterpreted()
	{
		SignedTransaction signed = aTransferSignedWith(SignatureSuite.ED25519, 0L);

		SignedTransaction relabelled = new SignedTransaction(signed.body(),
			SignatureSuite.ML_DSA_65, signed.signature());

		assertFalse(TransactionSigner.verify(relabelled),
			"the suite is inside the signed bytes, so relabelling changes what was signed");
	}

	@Test
	@DisplayName("an unknown suite identifier is refused, never read as an invalid signature")
	void anUnknownSuite_isRefused()
	{
		assertThrows(IllegalArgumentException.class,
			() -> SignatureSuite.withIdentifier("ml-dsa-from-the-future"));
	}

	@Test
	@DisplayName("one chain carries both kinds of transfer, and the replay accepts it")
	void oneChain_carriesBothSuites()
	{
		KeyPair holder = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		KeyPair quantumHolder = TransactionSigner.newKeyPair(SignatureSuite.ML_DSA_65);
		Bytes holderKey = TransactionSigner.asBytes(holder.getPublic());
		Bytes quantumKey = TransactionSigner.asBytes(quantumHolder.getPublic());
		Bytes miner = Bytes.of(new byte[] { 7 });

		// the holder, whom block 1 paid, pays the post-quantum account first, so it can pay in turn
		SignedTransaction classical = TransactionSigner.sign(
			new TransactionBody(Chain.TEST_IDENTIFIER, 0L, holderKey,
				new Destination(AddressScheme.DIRECT, quantumKey, Bytes.of(new byte[0]), 0),
				Amount.ofLeth(10L), Amount.ZERO, "signed the way every tool reads"),
			SignatureSuite.ED25519, holder.getPrivate());
		SignedTransaction postQuantum = TransactionSigner.sign(
			new TransactionBody(Chain.TEST_IDENTIFIER, 0L, quantumKey,
				new Destination(AddressScheme.DIRECT, holderKey, Bytes.of(new byte[0]), 0),
				Amount.ofLeth(4L), Amount.ZERO, "signed against a machine that does not exist yet"),
			SignatureSuite.ML_DSA_65, quantumHolder.getPrivate());

		List<BlockBody> funded = TestChains.funding(Chain.TEST_IDENTIFIER, holderKey,
			1_759_000_000_000L);
		BlockBody second = Blocks.mine(new BlockBody(Chain.TEST_IDENTIFIER, 2L,
			Blocks.hashOf(funded.get(1)), miner, List.of(classical, postQuantum),
			1_759_000_120_000L, 8, "two suites, one chain"), 1_000_000L).orElseThrow();

		byte[] file = CanonicalEncoding.encodeChain(List.of(funded.get(0), funded.get(1), second));
		System.out.println("a chain with one transfer of each suite is " + file.length + " bytes");

		Replay replay = Replay.verify(CanonicalEncoding.readChain(file));

		assertEquals(2L, replay.transactions());
		assertEquals(2L, replay.signatures());
		assertEquals(Emission.GENESIS_SUPPLY, replay.finalState().total());
		assertEquals(Amount.ofLeth(6L), replay.finalState().balanceOf(quantumKey),
			"ten in, four out again");
	}
}
