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
package io.github.astrapi69.lethenon.transport;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.github.astrapi69.lethenon.Bytes;

/**
 * The payload of {@code GET_CHAIN}: block hashes, newest first, by which a peer finds the newest
 * block both sides share
 * <p>
 * Built as Monero's (ADR 0003): the ten newest blocks one by one, then steps that double, and the
 * genesis block always last, so a locator of a chain of a million blocks has about thirty hashes.
 *
 * @param hashes
 *            the hashes, newest first, the genesis block last
 */
record Locator(List<Bytes> hashes)
{

	/** The most hashes a locator may carry: enough for a chain of 2^50 blocks */
	static final int LIMIT = 64;

	private static final int ONE_BY_ONE = 10;

	Locator
	{
		hashes = List.copyOf(hashes);
	}

	/**
	 * The locator of a chain
	 *
	 * @param chainHashes
	 *            the hashes of the chain's blocks, genesis first
	 * @return its locator
	 */
	static Locator of(final List<Bytes> chainHashes)
	{
		List<Bytes> picked = new ArrayList<>();
		long step = 1;
		long height = chainHashes.size() - 1L;
		while (height > 0)
		{
			picked.add(chainHashes.get((int)height));
			if (picked.size() >= ONE_BY_ONE)
			{
				step *= 2;
			}
			height -= step;
		}
		picked.add(chainHashes.getFirst());
		return new Locator(picked);
	}

	byte[] encode()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Wire.writeInt(out, hashes.size());
		for (Bytes hash : hashes)
		{
			Wire.writeBytes(out, hash.toByteArray());
		}
		return out.toByteArray();
	}

	static Locator decode(final byte[] payload) throws ProtocolViolation
	{
		ByteBuffer in = ByteBuffer.wrap(payload);
		int count = Wire.readInt(in);
		if (count < 1 || count > LIMIT)
		{
			throw new ProtocolViolation(
				"a locator of " + count + " hashes, outside 1 to " + LIMIT);
		}
		List<Bytes> hashes = new ArrayList<>();
		for (int index = 0; index < count; index++)
		{
			hashes.add(Wire.readHash(in, "a locator hash"));
		}
		Wire.requireEnd(in, "GET_CHAIN");
		return new Locator(hashes);
	}
}
