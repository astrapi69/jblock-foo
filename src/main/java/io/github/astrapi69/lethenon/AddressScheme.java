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
 * How a destination was formed, named on the wire (lethenon#2, the practice of ERC-5564's scheme
 * identifier).
 * <p>
 * The identifier is this chain's own, like {@link SignatureSuite#identifier()}: it goes into signed
 * bytes, so it must not change when anything upstream is renamed. A scheme this build does not
 * know is refused, never read as some other one - funds sent under it could not be attributed to
 * anybody.
 */
public enum AddressScheme
{
	/**
	 * The key as the recipient gave it: no ephemeral key, view tag zero. For recipients whose
	 * addresses have no one-time form - every ML-DSA destination, until a post-quantum stealth
	 * construction exists
	 */
	DIRECT("direct"),

	/**
	 * A one-time destination derived from the recipient's published view and spend keys and the
	 * sender's ephemeral key, which the transaction carries. The derivation comes to mystic-crypt
	 * before milestone 3; milestone 1 fixes the shape
	 */
	/**
	 * The first stealth destination, whose key was a HASH and therefore the private half of
	 * nothing: money paid to one of these could never be moved again (#21). It stays in this enum
	 * so a file that carries one can still be READ and named, and nothing produces a new one.
	 */
	STEALTH_V1("stealth-v1"),

	/**
	 * The one-time destination: the key is the Ed25519 public key {@code P = S + H(s)*B}, so a
	 * transfer out of it is verified by the same rule as any other, and the recipient signs with
	 * the blinded scalar only it can compute (#21)
	 */
	STEALTH_V2("stealth-v2");

	private final String identifier;

	AddressScheme(final String identifier)
	{
		this.identifier = identifier;
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
	 * The scheme with the given identifier
	 *
	 * @param identifier
	 *            the name out of the signed bytes
	 * @return the scheme
	 * @throws IllegalArgumentException
	 *             if no scheme has that name
	 */
	public static AddressScheme withIdentifier(final String identifier)
	{
		for (AddressScheme scheme : values())
		{
			if (scheme.identifier.equals(identifier))
			{
				return scheme;
			}
		}
		throw new IllegalArgumentException("no address scheme is called '" + identifier
			+ "'; a build that does not know a scheme cannot tell whose the funds are");
	}
}
