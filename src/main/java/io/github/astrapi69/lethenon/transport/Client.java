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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

/**
 * A short conversation with a node, for a caller that is not a node itself: connect, read the
 * node's HELLO, say something, and close in order
 * <p>
 * The node sends its HELLO first, so the caller learns which chain the node is on before it says
 * anything. Closing in order means closing this direction and reading until the node closes the
 * other: a node handles frames one after the other, so it has then handled everything that was
 * sent.
 */
final class Client implements Closeable
{

	private final Socket socket;

	private final DataInputStream in;

	private final DataOutputStream out;

	private final Hello theirs;

	private Client(final Socket socket, final DataInputStream in, final DataOutputStream out,
		final Hello theirs)
	{
		this.socket = socket;
		this.in = in;
		this.out = out;
		this.theirs = theirs;
	}

	/**
	 * Connects to a node over the given route and reads its HELLO, both within
	 * {@link Node#HANDSHAKE_MILLIS}
	 */
	static Client open(final PeerAddress address, final Outbound outbound) throws IOException
	{
		Socket socket = outbound.open(address, Node.HANDSHAKE_MILLIS);
		try
		{
			socket.setSoTimeout(Node.HANDSHAKE_MILLIS);
			DataInputStream in = new DataInputStream(
				new BufferedInputStream(socket.getInputStream()));
			DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(socket.getOutputStream()));
			Frame first = Frames.read(in, Frames.MAXIMUM_FRAME);
			if (first.type() != MessageType.HELLO)
			{
				throw new ProtocolViolation("it sent " + first.type() + " before HELLO");
			}
			return new Client(socket, in, out, Hello.decode(first.payload()));
		}
		catch (IOException failed)
		{
			socket.close();
			throw failed;
		}
	}

	Hello theirs()
	{
		return theirs;
	}

	void send(final Frame frame) throws IOException
	{
		Frames.write(out, frame);
	}

	Frame read() throws IOException
	{
		return Frames.read(in, Frames.MAXIMUM_FRAME);
	}

	/**
	 * Closes this direction and reads whatever the node still sends, a relayed block for
	 * instance, until it closes the other
	 */
	void closeInOrder() throws IOException
	{
		socket.shutdownOutput();
		try
		{
			while (true)
			{
				read();
			}
		}
		catch (EOFException closed)
		{
			// the node handled what was sent and closed its side
		}
	}

	@Override
	public void close() throws IOException
	{
		socket.close();
	}
}
