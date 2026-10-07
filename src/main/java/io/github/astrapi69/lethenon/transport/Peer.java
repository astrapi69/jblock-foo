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

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * One connection to another node after a successful handshake: the socket, the peer's HELLO, and
 * a lock so that frames from several threads are not interleaved
 */
final class Peer implements Closeable
{

	private final Socket socket;

	private final DataInputStream in;

	private final DataOutputStream out;

	private final Hello hello;

	Peer(final Socket socket, final DataInputStream in, final DataOutputStream out,
		final Hello hello)
	{
		this.socket = socket;
		this.in = in;
		this.out = out;
		this.hello = hello;
	}

	Hello hello()
	{
		return hello;
	}

	DataInputStream in()
	{
		return in;
	}

	String address()
	{
		return String.valueOf(socket.getRemoteSocketAddress());
	}

	void send(final Frame frame) throws IOException
	{
		synchronized (out)
		{
			Frames.write(out, frame);
		}
	}

	@Override
	public void close() throws IOException
	{
		socket.close();
	}
}
