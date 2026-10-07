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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Writes and reads frames: a 4-byte length, a type byte, the payload (ADR 0003). The length counts
 * the type and the payload, and it is checked before anything is allocated.
 */
public final class Frames
{

	/** The largest frame a node writes or reads: 4 MiB (ADR 0003, limits) */
	public static final int MAXIMUM_FRAME = 4 * 1024 * 1024;

	private Frames()
	{
	}

	/**
	 * Writes one frame and flushes it
	 *
	 * @param out
	 *            the stream
	 * @param frame
	 *            the frame
	 * @throws ProtocolViolation
	 *             when the frame would exceed {@link #MAXIMUM_FRAME}; nothing is written
	 * @throws IOException
	 *             when the stream fails
	 */
	public static void write(final DataOutputStream out, final Frame frame) throws IOException
	{
		long length = 1L + frame.payload().length;
		if (length > MAXIMUM_FRAME)
		{
			throw new ProtocolViolation("a " + frame.type() + " frame of " + length
				+ " bytes exceeds the limit of " + MAXIMUM_FRAME);
		}
		out.writeInt((int)length);
		out.writeByte(frame.type().code());
		out.write(frame.payload());
		out.flush();
	}

	/**
	 * Reads one frame
	 *
	 * @param in
	 *            the stream
	 * @param maximum
	 *            the largest length accepted
	 * @return the frame
	 * @throws ProtocolViolation
	 *             for a length outside 1 to maximum, or an unknown type, before the payload is read
	 * @throws java.io.EOFException
	 *             when the stream ends inside the frame
	 * @throws IOException
	 *             when the stream fails
	 */
	public static Frame read(final DataInputStream in, final int maximum) throws IOException
	{
		int length = in.readInt();
		if (length < 1 || length > maximum)
		{
			throw new ProtocolViolation(
				"a frame announced " + length + " bytes, outside 1 to " + maximum);
		}
		MessageType type = MessageType.ofCode(in.readByte());
		byte[] payload = new byte[length - 1];
		in.readFully(payload);
		return new Frame(type, payload);
	}
}
