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
 * Where a transfer goes: the recipient field of the signed transaction (lethenon#2).
 * <p>
 * One-time addresses are in the format from the first block, because a format is a hard fork to
 * change later. A destination therefore always carries the scheme it was formed under, the
 * sender's ephemeral key and the one-byte view tag - empty and zero for a {@link
 * AddressScheme#DIRECT direct} destination, required for a {@link AddressScheme#STEALTH_V1
 * stealth} one. The view tag lets a wallet discard about 255 of 256 foreign outputs before the
 * expensive derivation, as Monero and ERC-5564 do.
 *
 * @param scheme
 *            how the destination was formed
 * @param key
 *            the key the funds are locked to
 * @param ephemeralKey
 *            the sender's one-time public key, empty for a direct destination
 * @param viewTag
 *            one byte of the shared secret, 0 to 255, zero for a direct destination
 */
public record Destination(AddressScheme scheme, Bytes key, Bytes ephemeralKey, int viewTag)
{

	/** The largest view tag: it is one byte on the wire */
	public static final int VIEW_TAG_LIMIT = 255;

	/**
	 * Checks the shape the scheme allows
	 */
	public Destination
	{
		if (key.length() == 0)
		{
			throw new IllegalArgumentException("a destination needs the key the funds go to");
		}
		if (viewTag < 0 || viewTag > VIEW_TAG_LIMIT)
		{
			throw new IllegalArgumentException(
				"a view tag is one byte, 0 to " + VIEW_TAG_LIMIT + ", unlike " + viewTag);
		}
		switch (scheme)
		{
			case DIRECT -> {
				if (ephemeralKey.length() != 0 || viewTag != 0)
				{
					throw new IllegalArgumentException("a direct destination carries no ephemeral key"
						+ " and view tag 0, not " + ephemeralKey.length() + " bytes and " + viewTag);
				}
			}
			case STEALTH_V1 -> {
				if (ephemeralKey.length() == 0)
				{
					throw new IllegalArgumentException(
						"a stealth destination carries the sender's ephemeral key");
				}
			}
		}
	}

	/**
	 * A destination that is the key as given
	 *
	 * @param key
	 *            the key the funds are locked to
	 * @return the direct destination
	 */
	public static Destination direct(final Bytes key)
	{
		return new Destination(AddressScheme.DIRECT, key, Bytes.of(new byte[0]), 0);
	}
}
