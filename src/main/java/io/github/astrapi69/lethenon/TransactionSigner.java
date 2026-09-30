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

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * Signs a transfer, and checks a signed one.
 * <p>
 * Everything cryptographic here comes from the platform: JDK 25 ships Ed25519 in SunEC, so this
 * class writes no primitive, only the rules around one - what gets signed, which suite is named in
 * it, and whose key has to match.
 */
public final class TransactionSigner
{

	private TransactionSigner()
	{
	}

	/**
	 * A fresh key pair of the given suite
	 *
	 * @param suite
	 *            the signature scheme
	 * @return the key pair
	 */
	public static KeyPair newKeyPair(final SignatureSuite suite)
	{
		try
		{
			return KeyPairGenerator.getInstance(suite.algorithm()).generateKeyPair();
		}
		catch (GeneralSecurityException missing)
		{
			throw new IllegalStateException(
				"this Java runtime has no " + suite.algorithm() + " key pair generator", missing);
		}
	}

	/**
	 * The address form of a public key: the encoded key, which is what a transfer names as its
	 * sender
	 *
	 * @param publicKey
	 *            the key
	 * @return its bytes
	 */
	public static Bytes asBytes(final PublicKey publicKey)
	{
		return Bytes.of(publicKey.getEncoded());
	}

	/**
	 * Signs a transfer
	 *
	 * @param body
	 *            the transfer, whose sender field must be the public key belonging to this private
	 *            key
	 * @param suite
	 *            the signature scheme
	 * @param privateKey
	 *            the key to sign with
	 * @return the signed transaction
	 */
	public static SignedTransaction sign(final TransactionBody body, final SignatureSuite suite,
		final PrivateKey privateKey)
	{
		try
		{
			Signature signature = Signature.getInstance(suite.algorithm());
			signature.initSign(privateKey);
			signature.update(SigningPayload.of(body, suite));
			return new SignedTransaction(body, suite, Bytes.of(signature.sign()));
		}
		catch (GeneralSecurityException refused)
		{
			throw new IllegalStateException("the transfer could not be signed", refused);
		}
	}

	/**
	 * Whether a signed transaction was signed by the key it names, over exactly the transfer it
	 * carries
	 *
	 * @param transaction
	 *            the signed transaction
	 * @return true when the signature matches, false for every way it does not
	 */
	public static boolean verify(final SignedTransaction transaction)
	{
		try
		{
			PublicKey publicKey = KeyFactory.getInstance(transaction.suite().algorithm())
				.generatePublic(new X509EncodedKeySpec(transaction.body().sender().toByteArray()));
			Signature signature = Signature.getInstance(transaction.suite().algorithm());
			signature.initVerify(publicKey);
			signature.update(SigningPayload.of(transaction.body(), transaction.suite()));
			return signature.verify(transaction.signature().toByteArray());
		}
		catch (GeneralSecurityException refused)
		{
			// a key that cannot be read and a signature that cannot be checked are the same answer
			// to the only question asked here: no, this is not authorised
			return false;
		}
	}
}
