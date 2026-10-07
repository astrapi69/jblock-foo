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
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * One connection to another node after a successful handshake: the socket, the peer's HELLO, the
 * frames waiting to be written, and what the node is fetching from this peer
 * <p>
 * Frames are written by a thread of the peer's own, from a bounded queue. A node handles a frame
 * on the thread that read it, and that thread may have to send to other peers: if it wrote to their
 * sockets itself, two nodes relaying to each other while both send buffers are full would each
 * wait for the other to read. A peer whose queue is full does not read and is disconnected.
 */
final class Peer implements Closeable
{

	/** How many frames may wait for a peer before it counts as not reading */
	static final int QUEUE_LIMIT = 1_024;

	private final Socket socket;

	private final DataInputStream in;

	private final DataOutputStream out;

	private final Hello hello;

	private final BlockingQueue<Frame> outgoing = new LinkedBlockingQueue<>(QUEUE_LIMIT);

	private volatile Future<?> writer;

	private volatile Fetch fetch;

	private boolean synchronising;

	private boolean askAgain;

	private final int answerMillis;

	Peer(final Socket socket, final DataInputStream in, final DataOutputStream out,
		final Hello hello, final int answerMillis)
	{
		this.socket = socket;
		this.in = in;
		this.out = out;
		this.hello = hello;
		this.answerMillis = answerMillis;
	}

	/**
	 * Starts the thread that writes this peer's frames
	 */
	void startWriting(final ExecutorService threads)
	{
		writer = threads.submit(this::write);
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

	Fetch fetch()
	{
		return fetch;
	}

	void fetch(final Fetch next)
	{
		fetch = next;
	}

	/**
	 * Starts a synchronisation with this peer unless one is in flight; one that is in flight is
	 * asked to be followed by another when it ends (#85)
	 *
	 * @return whether the caller is to send GET_CHAIN now
	 */
	synchronized boolean startSynchronising()
	{
		if (synchronising)
		{
			askAgain = true;
			return false;
		}
		synchronising = true;
		awaitAnswersFor(answerMillis);
		return true;
	}

	synchronized boolean synchronising()
	{
		return synchronising;
	}

	/**
	 * Ends the synchronisation in flight
	 *
	 * @return whether another was asked for meanwhile, so that the caller is to send GET_CHAIN
	 *         again now
	 */
	synchronized boolean endSynchronising()
	{
		fetch = null;
		if (askAgain)
		{
			askAgain = false;
			return true;
		}
		synchronising = false;
		awaitAnswersFor(0);
		return false;
	}

	/**
	 * While a request is in flight the peer's next frame has to come within the answer time,
	 * otherwise the read fails and the node disconnects; with nothing asked, a peer may stay quiet
	 * (ADR 0003, limits)
	 */
	private void awaitAnswersFor(final int millis)
	{
		try
		{
			socket.setSoTimeout(millis);
		}
		catch (SocketException broken)
		{
			closeQuietly();
		}
	}

	/**
	 * Queues a frame for this peer; a peer whose queue is full is disconnected
	 */
	void send(final Frame frame)
	{
		if (!outgoing.offer(frame))
		{
			closeQuietly();
		}
	}

	private void write()
	{
		try
		{
			while (!socket.isClosed())
			{
				Frames.write(out, outgoing.take());
			}
		}
		catch (InterruptedException stopped)
		{
			Thread.currentThread().interrupt();
		}
		catch (IOException broken)
		{
			closeQuietly();
		}
	}

	private void closeQuietly()
	{
		try
		{
			close();
		}
		catch (IOException alreadyGone)
		{
			// a socket that cannot be closed is gone already
		}
	}

	@Override
	public void close() throws IOException
	{
		Future<?> running = writer;
		if (running != null)
		{
			running.cancel(true);
		}
		socket.close();
	}
}
