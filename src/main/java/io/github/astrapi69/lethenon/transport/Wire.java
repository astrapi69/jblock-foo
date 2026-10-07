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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.github.astrapi69.lethenon.Bytes;

/**
 * The field encoding of the network's own payloads: big endian, every variable-length field
 * prefixed with its length, and every length bounded on reading. Blocks and transfers inside a
 * payload keep the chain's own encoding (CanonicalEncoding).
 */
final class Wire
{

	private Wire()
	{
	}

	static void writeInt(final ByteArrayOutputStream out, final int value)
	{
		out.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
	}

	static void writeLong(final ByteArrayOutputStream out, final long value)
	{
		out.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
	}

	static void writeBytes(final ByteArrayOutputStream out, final byte[] value)
	{
		writeInt(out, value.length);
		out.writeBytes(value);
	}

	static void writeText(final ByteArrayOutputStream out, final String value)
	{
		writeBytes(out, value.getBytes(StandardCharsets.UTF_8));
	}

	static int readInt(final ByteBuffer in) throws ProtocolViolation
	{
		try
		{
			return in.getInt();
		}
		catch (BufferUnderflowException ended)
		{
			throw new ProtocolViolation("a payload ended where a number was expected");
		}
	}

	static long readLong(final ByteBuffer in) throws ProtocolViolation
	{
		try
		{
			return in.getLong();
		}
		catch (BufferUnderflowException ended)
		{
			throw new ProtocolViolation("a payload ended where a number was expected");
		}
	}

	static byte[] readBytes(final ByteBuffer in, final int maximum, final String what)
		throws ProtocolViolation
	{
		int length = readInt(in);
		if (length < 0 || length > maximum || length > in.remaining())
		{
			throw new ProtocolViolation(what + " announced " + length + " bytes, with " + maximum
				+ " allowed and " + in.remaining() + " left");
		}
		byte[] value = new byte[length];
		in.get(value);
		return value;
	}

	static Bytes readHash(final ByteBuffer in, final String what) throws ProtocolViolation
	{
		return Bytes.of(readBytes(in, 64, what));
	}

	static String readText(final ByteBuffer in, final int maximum, final String what)
		throws ProtocolViolation
	{
		return new String(readBytes(in, maximum, what), StandardCharsets.UTF_8);
	}

	static void requireEnd(final ByteBuffer in, final String what) throws ProtocolViolation
	{
		if (in.hasRemaining())
		{
			throw new ProtocolViolation(
				what + " carries " + in.remaining() + " bytes after its end");
		}
	}
}
