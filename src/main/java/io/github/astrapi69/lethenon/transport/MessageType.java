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

/**
 * The messages of the test network (ADR 0003), each with the byte that names it in a frame. The
 * byte is part of the wire and does not change when an entry is renamed or reordered.
 */
public enum MessageType
{

	/** Who a node is: protocol version, chain, genesis, tip, work */
	HELLO((byte)1),

	/** One block, encoded as a chain of one */
	BLOCK((byte)2),

	/** One signed transfer */
	TRANSFER((byte)3),

	/** A locator of block hashes, asking which of them the peer shares */
	GET_CHAIN((byte)4),

	/** The height of the first shared hash, and the hashes after it */
	CHAIN((byte)5),

	/** A first height and a count of blocks */
	GET_BLOCKS((byte)6),

	/** Blocks, encoded as a chain */
	BLOCKS((byte)7);

	private final byte code;

	MessageType(final byte code)
	{
		this.code = code;
	}

	/**
	 * The byte that names this message in a frame
	 *
	 * @return the code
	 */
	public byte code()
	{
		return code;
	}

	/**
	 * The message a code names
	 *
	 * @param code
	 *            the byte read from a frame
	 * @return the message type
	 * @throws ProtocolViolation
	 *             for a code no message has
	 */
	public static MessageType ofCode(final byte code) throws ProtocolViolation
	{
		for (MessageType type : values())
		{
			if (type.code == code)
			{
				return type;
			}
		}
		throw new ProtocolViolation("no message type has the code " + code);
	}
}
