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

import java.util.Map;
import java.util.Optional;

/**
 * What this chain is called where it matters: inside the bytes that get signed.
 * <p>
 * The identifier is part of every signing payload, so a transaction signed here cannot be replayed
 * on a test network or on a fork that shares the key material.
 * <p>
 * There are two: the main chain and the test chain (lethenon#50). Which one a chain is, its genesis
 * block decides, and every later block and transfer carries the same identifier. A new scheme runs
 * on the test chain first (ADR 0001).
 * <p>
 * Under the rules from lethenon 0.4.0 on - no pre-allocation (#111), a declining reward with a tail
 * (#133), an anchored genesis block for the main chain - they are {@code lethenon-2} and
 * {@code lethenon-test-2} (lethenon#137). The identifiers before them, {@code lethenon-1} and
 * {@code lethenon-test-1}, are retired: a version before 0.3.0 read a chain of 0.3.0 without a word
 * and computed other balances, and a version of the new rules would do the same with an old chain. An identifier is
 * the one thing every version checks first, so a new one makes both sides refuse instead.
 */
public final class Chain
{

	/**
	 * The identifier of the main chain under the rules from lethenon 0.4.0 on (#137), which goes into
	 * every signature made for it
	 */
	public static final String IDENTIFIER = "lethenon-2";

	/** The identifier of the test chain under the rules from lethenon 0.4.0 on (#50, #137) */
	public static final String TEST_IDENTIFIER = "lethenon-test-2";

	/** The identifiers of the rules before 0.4.0, each with the one that took its place (#137) */
	private static final Map<String, String> RETIRED = Map.of("lethenon-1", IDENTIFIER,
		"lethenon-test-1", TEST_IDENTIFIER);

	private Chain()
	{
	}

	/**
	 * Why a chain under the given identifier is refused: it was started under the rules before
	 * lethenon 0.4.0, and the reason names the identifier that took its place (#137)
	 *
	 * @param identifier
	 *            the identifier a chain carries
	 * @return the reason, or empty for an identifier that was never retired
	 */
	public static Optional<String> retiredBecause(final String identifier)
	{
		return Optional.ofNullable(RETIRED.get(identifier))
			.map(successor -> "chain '" + identifier
				+ "' was started under the rules before lethenon 0.4.0; under the rules from 0.4.0 on the "
				+ (IDENTIFIER.equals(successor) ? "main" : "test") + " chain is '" + successor
				+ "'");
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
