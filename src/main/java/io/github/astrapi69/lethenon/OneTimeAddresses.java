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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.SecretKey;

import io.github.astrapi69.mystic.crypt.key.X25519KeyExchange;

/**
 * Payments that cannot be linked to the address they were sent to.
 * <p>
 * The sender takes the recipient's published view key, makes a key pair for this payment alone, and
 * derives a shared secret with X25519. The destination the transaction carries is a hash of that
 * secret together with the recipient's spend key - so two payments to the same published address
 * land on destinations that have nothing visibly in common, while the recipient recognises both
 * with its view private key alone.
 * <p>
 * The view tag is one byte of the same secret, carried in the clear. It is a filter, not a secret:
 * a wallet can rule out 255 of every 256 transactions with one comparison instead of a full
 * derivation, which is what makes scanning a whole chain affordable on a laptop.
 * <p>
 * The X25519 agreement comes from mystic-crypt's {@code X25519KeyExchange}, which runs the raw
 * secret through HKDF rather than using it directly. This class writes no primitive; what it
 * defines is the rule - what is hashed, in which order, with which domain tag.
 */
public final class OneTimeAddresses
{

	/** What the derivation is for, so the same secret cannot mean something else elsewhere */
	public static final String DOMAIN_TAG = "lethenon.stealth.v1";

	private OneTimeAddresses()
	{
	}

	/**
	 * A fresh X25519 pair for one payment, which the sender publishes the public half of inside the
	 * transaction and then forgets
	 *
	 * @return the pair
	 */
	public static KeyPair newEphemeralKeyPair()
	{
		try
		{
			return X25519KeyExchange.newKeyPair();
		}
		catch (GeneralSecurityException missing)
		{
			throw new IllegalStateException("this Java runtime has no X25519", missing);
		}
	}

	/**
	 * The destination a sender writes into a transfer
	 *
	 * @param address
	 *            the recipient's published address
	 * @param ephemeral
	 *            the pair made for this payment alone
	 * @return the destination: scheme, the derived key, the ephemeral public half and the view tag
	 */
	public static Destination destinationFor(final PublishedAddress address,
		final KeyPair ephemeral)
	{
		byte[] shared = sharedSecret(ephemeral.getPrivate(), publicKeyOf(address.viewKey()));
		return new Destination(AddressScheme.STEALTH_V1, derive(shared, address.spendKey()),
			Bytes.of(ephemeral.getPublic().getEncoded()), viewTagOf(shared));
	}

	/**
	 * Whether a destination was meant for this recipient.
	 * <p>
	 * The view tag is compared first, which is the cheap half; only then is the destination
	 * derived and compared.
	 *
	 * @param destination
	 *            the destination a transfer carries
	 * @param address
	 *            the recipient's published address
	 * @param viewPrivateKey
	 *            the private half of the view key - the spending key is not needed to recognise a
	 *            payment, which is the point of having two
	 * @return true when this payment belongs to the recipient
	 */
	public static boolean belongsTo(final Destination destination, final PublishedAddress address,
		final PrivateKey viewPrivateKey)
	{
		if (!AddressScheme.STEALTH_V1.equals(destination.scheme()))
		{
			return false;
		}
		byte[] shared = sharedSecret(viewPrivateKey, publicKeyOf(destination.ephemeralKey()));
		return destination.viewTag() == viewTagOf(shared)
			&& derive(shared, address.spendKey()).equals(destination.key());
	}

	private static byte[] sharedSecret(final PrivateKey ours, final PublicKey theirs)
	{
		try
		{
			SecretKey secret = X25519KeyExchange.deriveSharedSecret(ours, theirs, 32);
			return secret.getEncoded();
		}
		catch (GeneralSecurityException refused)
		{
			throw new IllegalArgumentException("the two keys do not agree on a secret", refused);
		}
	}

	private static Bytes derive(final byte[] shared, final Bytes spendKey)
	{
		ByteArrayOutputStream material = new ByteArrayOutputStream();
		material.writeBytes(DOMAIN_TAG.getBytes(StandardCharsets.UTF_8));
		material.writeBytes(shared);
		material.writeBytes(spendKey.toByteArray());
		return Bytes.of(SigningPayload.digestOf(material.toByteArray()));
	}

	private static int viewTagOf(final byte[] shared)
	{
		ByteArrayOutputStream material = new ByteArrayOutputStream();
		material.writeBytes((DOMAIN_TAG + ".tag").getBytes(StandardCharsets.UTF_8));
		material.writeBytes(shared);
		return SigningPayload.digestOf(material.toByteArray())[0] & 0xFF;
	}

	private static PublicKey publicKeyOf(final Bytes encoded)
	{
		try
		{
			return KeyFactory.getInstance("X25519")
				.generatePublic(new X509EncodedKeySpec(encoded.toByteArray()));
		}
		catch (GeneralSecurityException refused)
		{
			throw new IllegalArgumentException("not an X25519 public key: " + encoded, refused);
		}
	}
}
