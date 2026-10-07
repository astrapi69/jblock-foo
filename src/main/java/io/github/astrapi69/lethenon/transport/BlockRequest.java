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

/**
 * The payload of {@code GET_BLOCKS}: the height of the first block wanted and how many
 *
 * @param first
 *            the first height
 * @param count
 *            how many blocks, 1 to {@link #LIMIT}
 */
record BlockRequest(long first, int count)
{

	/** The most blocks one request may ask for: Monero's default of 20 (ADR 0003, limits) */
	static final int LIMIT = 20;

	byte[] encode()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Wire.writeLong(out, first);
		Wire.writeInt(out, count);
		return out.toByteArray();
	}

	static BlockRequest decode(final byte[] payload) throws ProtocolViolation
	{
		ByteBuffer in = ByteBuffer.wrap(payload);
		long first = Wire.readLong(in);
		int count = Wire.readInt(in);
		Wire.requireEnd(in, "GET_BLOCKS");
		if (first < 0 || count < 1 || count > LIMIT)
		{
			throw new ProtocolViolation("GET_BLOCKS asked for " + count + " blocks from height "
				+ first + ", and a request asks for 1 to " + LIMIT + " from height 0 or above");
		}
		return new BlockRequest(first, count);
	}
}
