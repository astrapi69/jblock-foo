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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.astrapi69.crypt.api.algorithm.MacAlgorithm;

/**
 * Hierarchical key derivation after SLIP-0010, hardened only, for ed25519 and curve25519.
 * <p>
 * One seed, many keys: every key a wallet holds is a path below the same master node, so the seed
 * is the only thing that has to be backed up, and splitting it into shares backs up all of them.
 * <p>
 * SLIP-0010 rather than an own construction, because a derivation is a recovery format: a wallet
 * that is restored in ten years has to arrive at the same keys, and a published standard with test
 * vectors is what makes that checkable. The only primitive is HMAC-SHA512, from the platform; this
 * family has no hierarchical derivation, and HMAC itself is not something to write.
 * <p>
 * That missing piece is filed as <a href="https://github.com/astrapi69/mystic-crypt/issues/163">
 * mystic-crypt#163</a>: SLIP-0010 derivation and the HMAC helper belong in the library, where one
 * seed-to-key-pair mapping serves every consumer. The steps below are the workaround while it is
 * open, and this class calls the library instead once it lands - a seed-to-key-pair mapping is the
 * last thing that may differ between two implementations.
 * <p>
 * Only hardened children exist for these two curves - SLIP-0010 defines no other kind for them -
 * so every index is taken as hardened and a path is given as plain numbers.
 *
 * @see <a href="https://github.com/satoshilabs/slips/blob/master/slip-0010.md">SLIP-0010</a>
 */
final class HierarchicalDerivation
{

	/** The shortest seed BIP-32 allows, 128 bits */
	static final int SEED_MINIMUM = 16;

	/** The longest seed BIP-32 allows, 512 bits */
	static final int SEED_MAXIMUM = 64;

	private static final int HARDENED = 0x8000_0000;

	private static final int HALF = 32;

	/**
	 * Which tree a key comes from. The name is the HMAC key of the master node, so the same seed
	 * gives unrelated keys on the two curves
	 */
	enum Curve
	{
		/** signing keys */
		ED25519("ed25519 seed"),

		/** key agreement keys, the view key of a published address */
		CURVE25519("curve25519 seed");

		private final byte[] masterKey;

		Curve(final String masterKey)
		{
			this.masterKey = masterKey.getBytes(StandardCharsets.US_ASCII);
		}
	}

	/**
	 * A point in the tree
	 *
	 * @param privateKey
	 *            the 32 bytes of the private key at this point
	 * @param chainCode
	 *            the 32 bytes that, with the key, derive the children
	 */
	record Node(Bytes privateKey, Bytes chainCode)
	{
	}

	private HierarchicalDerivation()
	{
	}

	/**
	 * The node at a path below the master node of a seed
	 *
	 * @param curve
	 *            the tree
	 * @param seed
	 *            16 to 64 bytes
	 * @param path
	 *            the child indices, each one hardened; none means the master node
	 * @return the node
	 * @throws IllegalArgumentException
	 *             for a seed of the wrong length or a negative index
	 */
	static Node derive(final Curve curve, final byte[] seed, final int... path)
	{
		if (seed.length < SEED_MINIMUM || seed.length > SEED_MAXIMUM)
		{
			throw new IllegalArgumentException("a seed is " + SEED_MINIMUM + " to " + SEED_MAXIMUM
				+ " bytes, and this one is " + seed.length + " bytes");
		}
		Node node = split(hmac(curve.masterKey, seed));
		for (int index : path)
		{
			node = child(node, index);
		}
		return node;
	}

	private static Node child(final Node parent, final int index)
	{
		if (index < 0)
		{
			throw new IllegalArgumentException("a child index counts from 0 to " + Integer.MAX_VALUE
				+ " and is hardened by the derivation itself, unlike " + index);
		}
		byte[] material = ByteBuffer.allocate(1 + HALF + Integer.BYTES).put((byte)0)
			.put(parent.privateKey().toByteArray()).putInt(index | HARDENED).array();
		return split(hmac(parent.chainCode().toByteArray(), material));
	}

	private static Node split(final byte[] digest)
	{
		return new Node(Bytes.of(Arrays.copyOfRange(digest, 0, HALF)),
			Bytes.of(Arrays.copyOfRange(digest, HALF, 2 * HALF)));
	}

	private static byte[] hmac(final byte[] key, final byte[] message)
	{
		String algorithm = MacAlgorithm.HmacSHA512.getAlgorithm();
		try
		{
			Mac mac = Mac.getInstance(algorithm);
			mac.init(new SecretKeySpec(key, algorithm));
			return mac.doFinal(message);
		}
		catch (GeneralSecurityException missing)
		{
			throw new IllegalStateException("this Java runtime has no " + algorithm, missing);
		}
	}
}
