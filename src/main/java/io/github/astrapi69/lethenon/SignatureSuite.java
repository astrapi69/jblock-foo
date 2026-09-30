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

/**
 * Which signature scheme signed a transaction, named inside the bytes that were signed.
 * <p>
 * Crypto-agility only counts if it is in the format from the first block: adding a suite field to a
 * signed structure later is a hard fork. ML-DSA-65 arrives in milestone 2, and it arrives into a
 * format that already expects it - a signature made for one suite must not verify as another, which
 * is why the identifier is part of the signing payload rather than a field beside it.
 */
public enum SignatureSuite
{

	/** Ed25519, 64 byte signatures, what every tool reads */
	ED25519("ed25519", "Ed25519");

	private final String identifier;

	private final String algorithm;

	SignatureSuite(final String identifier, final String algorithm)
	{
		this.identifier = identifier;
		this.algorithm = algorithm;
	}

	/**
	 * The name that goes into the signed bytes
	 *
	 * @return the identifier
	 */
	public String identifier()
	{
		return identifier;
	}

	/**
	 * The algorithm name this platform knows the scheme by
	 *
	 * @return the algorithm
	 */
	public String algorithm()
	{
		return algorithm;
	}

	/**
	 * The suite with the given identifier
	 *
	 * @param identifier
	 *            the name out of the signed bytes
	 * @return the suite
	 * @throws IllegalArgumentException
	 *             if no suite has that name - an unknown suite is refused, never ignored
	 */
	public static SignatureSuite withIdentifier(final String identifier)
	{
		for (SignatureSuite suite : values())
		{
			if (suite.identifier.equals(identifier))
			{
				return suite;
			}
		}
		throw new IllegalArgumentException("no signature suite is called '" + identifier
			+ "'; a build that does not know a suite cannot check what it signed");
	}
}
