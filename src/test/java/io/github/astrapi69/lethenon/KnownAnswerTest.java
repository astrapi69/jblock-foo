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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.mystic.crypt.key.Signatures;

/**
 * Known answers, so that a change in a dependency cannot silently alter what verifies.
 * <p>
 * The chain's own tests all sign and verify with keys they generated a moment earlier: they would
 * stay green if both halves changed together. These two do not - one checks a signature published
 * by a standard, the other checks that what the platform signs the library accepts and the other
 * way round.
 */
class KnownAnswerTest
{

	/** The DER prefix that wraps 32 raw Ed25519 public key bytes as X.509, RFC 8410 */
	private static final String ED25519_X509_PREFIX = "302a300506032b6570032100";

	@Test
	@DisplayName("RFC 8032 test vector 1 verifies through the library")
	void rfc8032_vectorOne_verifies() throws Exception
	{
		// RFC 8032, section 7.1, TEST 1: the empty message, its public key and its signature
		byte[] publicKey = HexFormat.of()
			.parseHex("d75a980182b10ab7d54bfed3c964073a"
				+ "0ee172f3daa62325af021a68f707511a");
		byte[] signature = HexFormat.of()
			.parseHex("e5564300c360ac729086e2cc806e828a"
				+ "84877f1eb8e5d974d873e06522490155"
				+ "5fb8821590a33bacc61e39701cf9b46b"
				+ "d25bf5f0595bbe24655141438e7a100b");
		PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
			HexFormat.of().parseHex(ED25519_X509_PREFIX + HexFormat.of().formatHex(publicKey))));

		assertTrue(Signatures.verify(SignatureSuite.ED25519.algorithm(), key, new byte[0],
			signature), "the signature RFC 8032 publishes for the empty message");

		byte[] tampered = signature.clone();
		tampered[0] ^= 0x01;
		assertFalse(
			Signatures.verify(SignatureSuite.ED25519.algorithm(), key, new byte[0], tampered),
			"and one bit away from it is not a signature");
	}

	@Test
	@DisplayName("what the platform signs, the library verifies - for both suites")
	void platformAndLibrary_agree() throws Exception
	{
		byte[] message = "the chain is checkable by anybody".getBytes("UTF-8");
		for (SignatureSuite suite : SignatureSuite.values())
		{
			KeyPair pair = TransactionSigner.newKeyPair(suite);

			Signature platform = Signature.getInstance(suite.algorithm());
			platform.initSign(pair.getPrivate());
			platform.update(message);
			byte[] signedByThePlatform = platform.sign();

			assertTrue(
				Signatures.verify(suite.algorithm(), pair.getPublic(), message,
					signedByThePlatform),
				suite + ": the library has to accept what the platform signed");

			byte[] signedByTheLibrary = Signatures.sign(suite.algorithm(), pair.getPrivate(),
				message);
			Signature check = Signature.getInstance(suite.algorithm());
			check.initVerify(pair.getPublic());
			check.update(message);
			assertTrue(check.verify(signedByTheLibrary),
				suite + ": and the platform has to accept what the library signed");
		}
	}

	@Test
	@DisplayName("ML-DSA-65 signatures and keys have the sizes FIPS 204 fixes")
	void mlDsa_hasTheSizesTheStandardFixes() throws Exception
	{
		KeyPair pair = TransactionSigner.newKeyPair(SignatureSuite.ML_DSA_65);
		byte[] signature = Signatures.sign(SignatureSuite.ML_DSA_65.algorithm(), pair.getPrivate(),
			new byte[] { 1, 2, 3 });

		// FIPS 204: ML-DSA-65 signatures are 3309 bytes and raw public keys 1952; the key here
		// carries its X.509 wrapper, which is why it reads 1974
		assertArrayEquals(new int[] { 3309, 1974 },
			new int[] { signature.length, pair.getPublic().getEncoded().length });
	}
}
