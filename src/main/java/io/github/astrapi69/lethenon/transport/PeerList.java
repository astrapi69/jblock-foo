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

/**
 * The payload of {@code PEERS}: addresses of nodes that listen, which the answering node has
 * itself been connected to
 *
 * @param addresses
 *            at most {@link #LIMIT} addresses
 */
record PeerList(List<PeerAddress> addresses)
{

	/**
	 * The most addresses one answer carries: Monero's {@code P2P_DEFAULT_PEERS_IN_HANDSHAKE 250}
	 * ({@code src/cryptonote_config.h:144})
	 */
	static final int LIMIT = 250;

	/** The longest host name a DNS name can be */
	private static final int HOST_LIMIT = 255;

	PeerList
	{
		addresses = List.copyOf(addresses);
	}

	byte[] encode()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Wire.writeInt(out, addresses.size());
		for (PeerAddress address : addresses)
		{
			Wire.writeText(out, address.host());
			Wire.writeInt(out, address.port());
		}
		return out.toByteArray();
	}

	static PeerList decode(final byte[] payload) throws ProtocolViolation
	{
		ByteBuffer in = ByteBuffer.wrap(payload);
		int count = Wire.readInt(in);
		if (count < 0 || count > LIMIT)
		{
			throw new ProtocolViolation("PEERS with " + count + " addresses, outside 0 to " + LIMIT);
		}
		List<PeerAddress> addresses = new ArrayList<>();
		for (int index = 0; index < count; index++)
		{
			String host = Wire.readText(in, HOST_LIMIT, "a peer's host");
			int port = Wire.readInt(in);
			try
			{
				addresses.add(new PeerAddress(host, port));
			}
			catch (IllegalArgumentException invalid)
			{
				throw new ProtocolViolation("PEERS carries an address that is none: "
					+ invalid.getMessage());
			}
		}
		Wire.requireEnd(in, "PEERS");
		return new PeerList(addresses);
	}
}
