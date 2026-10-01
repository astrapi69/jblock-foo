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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;

/**
 * A key pair made from 32 given bytes rather than from fresh randomness - what lets a wallet's keys
 * come back from its seed.
 * <p>
 * All three key types the chain uses take exactly 32 bytes of randomness to make a pair: the
 * Ed25519 secret of RFC 8032, the X25519 scalar of RFC 7748, and the seed xi from which FIPS 204
 * makes an ML-DSA key pair. So no key is assembled here. The platform's own generator makes the
 * pair, and the only thing replaced is where its 32 bytes come from. Measured on OpenJDK 25: each
 * of the three generators reads exactly one block of 32 bytes; the RFC 8032 known answer in the
 * tests shows that what comes out is the key pair those bytes stand for.
 * <p>
 * The randomness handed over fails closed. It gives its 32 bytes once and refuses every further
 * read, of any length - a future generator that asked for more would otherwise get bytes nobody
 * chose, and keys that look fine and cannot be recovered.
 */
final class DeterministicKeys
{

	/** How many bytes a key pair is made from */
	static final int SEED_LENGTH = 32;

	private DeterministicKeys()
	{
	}

	/**
	 * The key pair a seed stands for
	 *
	 * @param algorithm
	 *            the platform's name of the key type, as crypt-api names it
	 * @param seed
	 *            exactly 32 bytes
	 * @return the key pair
	 */
	static KeyPair from(final String algorithm, final Bytes seed)
	{
		if (seed.length() != SEED_LENGTH)
		{
			throw new IllegalArgumentException("a key pair is made from " + SEED_LENGTH
				+ " bytes, and this seed is " + seed.length() + " bytes");
		}
		try
		{
			KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
			generator.initialize(new NamedParameterSpec(algorithm), new OneSeed(seed));
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException missing)
		{
			throw new IllegalStateException(
				"this Java runtime cannot make a " + algorithm + " key pair from a seed", missing);
		}
	}

	/**
	 * Randomness that is 32 chosen bytes, given out once
	 */
	static final class OneSeed extends SecureRandom
	{

		private static final long serialVersionUID = 1L;

		private final byte[] seed;

		private boolean spent;

		OneSeed(final Bytes seed)
		{
			this.seed = seed.toByteArray();
		}

		@Override
		public synchronized void nextBytes(final byte[] bytes)
		{
			if (spent)
			{
				throw new IllegalStateException("the seed is given out once; a second read would "
					+ "get bytes nobody chose, and a key that cannot be recovered");
			}
			if (bytes.length != seed.length)
			{
				throw new IllegalStateException("the generator asked for " + bytes.length
					+ " bytes, and a key pair is made from exactly " + seed.length);
			}
			System.arraycopy(seed, 0, bytes, 0, seed.length);
			spent = true;
		}
	}
}
