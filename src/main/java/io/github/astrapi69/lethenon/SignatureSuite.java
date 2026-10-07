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

import io.github.astrapi69.crypt.api.algorithm.key.KeyPairGeneratorAlgorithm;

/**
 * Which signature scheme signed a transaction, named inside the bytes that were signed.
 * <p>
 * The algorithm itself is not defined here: {@link KeyPairGeneratorAlgorithm} in crypt-api already
 * carries Ed25519, the three ML-DSA parameter sets and the SLH-DSA family, and a second list of the
 * same names is a list that drifts. What is defined here is the one thing that belongs to the
 * chain: the short identifier that goes into the signed bytes and into every block ever written.
 * <p>
 * That identifier is deliberately not the enum's own name. A persisted format must not move when an
 * upstream enum is renamed or reordered - the wire is forever, the library is a dependency.
 * <p>
 * Crypto-agility counts only if it is in the format from the first block: ML-DSA-65 arrives in
 * milestone 2 into a format that already expects it, and a signature made for one suite cannot be
 * reinterpreted as another, because the identifier is part of the payload rather than a field
 * beside it.
 */
public enum SignatureSuite implements Scheme
{

	/** Ed25519, 64 byte signatures, what every tool reads */
	ED25519("ed25519", KeyPairGeneratorAlgorithm.Ed25519),

	/**
	 * ML-DSA-65, FIPS 204. Measured with OpenSSL 3.5.5: a signature is 3309 bytes against
	 * Ed25519's 64, and a public key 1974 against 44 - the price of an answer to "harvest now,
	 * decrypt later", stated rather than hidden. It is why this chain uses accounts rather than
	 * UTXO: one signature per transaction instead of one per input
	 */
	ML_DSA_65("ml-dsa-65", KeyPairGeneratorAlgorithm.ML_DSA_65);

	private final String identifier;

	private final KeyPairGeneratorAlgorithm algorithm;

	SignatureSuite(final String identifier, final KeyPairGeneratorAlgorithm algorithm)
	{
		this.identifier = identifier;
		this.algorithm = algorithm;
	}

	/**
	 * The name that goes into the signed bytes, stable for the life of the chain
	 *
	 * @return the identifier
	 */
	@Override
	public String identifier()
	{
		return identifier;
	}

	@Override
	public BuildingBlock block()
	{
		return BuildingBlock.AUTHORIZATION;
	}

	/**
	 * The algorithm name this platform knows the scheme by, from crypt-api rather than from a
	 * string in this project
	 *
	 * @return the algorithm name
	 */
	public String algorithm()
	{
		return algorithm.getAlgorithm();
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
