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
 * The payload of {@code CHAIN}: the height of the newest locator hash the answering node shares,
 * then the hashes of its blocks after it, oldest first
 *
 * @param sharedHeight
 *            the height of the shared block
 * @param hashes
 *            at most {@link #LIMIT} hashes of the blocks after it
 */
record ChainEntry(long sharedHeight, List<Bytes> hashes)
{

	/** The most hashes one answer carries (ADR 0003, limits) */
	static final int LIMIT = 500;

	ChainEntry
	{
		hashes = List.copyOf(hashes);
	}

	byte[] encode()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Wire.writeLong(out, sharedHeight);
		Wire.writeInt(out, hashes.size());
		for (Bytes hash : hashes)
		{
			Wire.writeBytes(out, hash.toByteArray());
		}
		return out.toByteArray();
	}

	static ChainEntry decode(final byte[] payload) throws ProtocolViolation
	{
		ByteBuffer in = ByteBuffer.wrap(payload);
		long sharedHeight = Wire.readLong(in);
		int count = Wire.readInt(in);
		if (sharedHeight < 0 || count < 0 || count > LIMIT)
		{
			throw new ProtocolViolation("a CHAIN answer sharing height " + sharedHeight
				+ " with " + count + " hashes, outside 0 to " + LIMIT);
		}
		List<Bytes> hashes = new ArrayList<>();
		for (int index = 0; index < count; index++)
		{
			hashes.add(Wire.readHash(in, "a chain hash"));
		}
		Wire.requireEnd(in, "CHAIN");
		return new ChainEntry(sharedHeight, hashes);
	}
}
