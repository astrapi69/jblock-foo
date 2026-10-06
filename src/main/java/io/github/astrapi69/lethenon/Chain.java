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
 * What this chain is called where it matters: inside the bytes that get signed.
 * <p>
 * The identifier is part of every signing payload, so a transaction signed here cannot be replayed
 * on a test network or on a fork that shares the key material.
 * <p>
 * There are two: the main chain and the test chain (lethenon#50). Which one a chain is, its genesis
 * block decides, and every later block and transfer carries the same identifier. A new scheme runs
 * on the test chain first (ADR 0001).
 */
public final class Chain
{

	/** The identifier of the main chain, which goes into every signature made for it */
	public static final String IDENTIFIER = "lethenon-1";

	/** The identifier of the test chain (lethenon#50) */
	public static final String TEST_IDENTIFIER = "lethenon-test-1";

	private Chain()
	{
	}

	/**
	 * Whether an identifier names one of the two chains, exactly as written
	 *
	 * @param identifier
	 *            the identifier
	 * @return true for {@link #IDENTIFIER} and {@link #TEST_IDENTIFIER}
	 */
	public static boolean isKnown(final String identifier)
	{
		return IDENTIFIER.equals(identifier) || TEST_IDENTIFIER.equals(identifier);
	}
}
