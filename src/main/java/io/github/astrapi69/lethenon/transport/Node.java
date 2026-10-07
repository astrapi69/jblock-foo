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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Chain;

/**
 * A node of the test network (ADR 0003): it listens, connects to a fixed list of peers, and shakes
 * hands with every connection before anything else is exchanged.
 * <p>
 * A node runs only on the test chain {@link Chain#TEST_IDENTIFIER}: it refuses a main chain at
 * start and a peer that names any other chain or another genesis block. Each connection runs on a
 * virtual thread of its own.
 */
public final class Node implements AutoCloseable
{

	/** How long a peer has to say HELLO: Monero's handshake timeout of 5000 ms (ADR 0003) */
	public static final int HANDSHAKE_MILLIS = 5_000;

	private final Object lock = new Object();

	private final List<Peer> peers = new CopyOnWriteArrayList<>();

	private final List<String> refusals = new CopyOnWriteArrayList<>();

	private final List<Socket> open = new CopyOnWriteArrayList<>();

	private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

	private List<BlockBody> chain;

	private ServerSocket server;

	private Node(final List<BlockBody> chain)
	{
		this.chain = List.copyOf(chain);
	}

	/**
	 * A node on the given chain
	 *
	 * @param chain
	 *            the chain, genesis first, on {@link Chain#TEST_IDENTIFIER}
	 * @return the node, not yet listening or connected
	 * @throws IllegalArgumentException
	 *             for an empty chain or one that is not the test chain
	 */
	public static Node on(final List<BlockBody> chain)
	{
		if (chain.isEmpty())
		{
			throw new IllegalArgumentException("a node needs a chain with a genesis block");
		}
		String identifier = chain.getFirst().chainIdentifier();
		if (!Chain.TEST_IDENTIFIER.equals(identifier))
		{
			throw new IllegalArgumentException("a node runs only on '" + Chain.TEST_IDENTIFIER
				+ "', and this chain is '" + identifier + "' (ADR 0003)");
		}
		return new Node(chain);
	}

	/**
	 * Starts accepting connections
	 *
	 * @param port
	 *            the TCP port, 0 for any free one
	 * @return the port it listens on
	 * @throws IOException
	 *             when the port cannot be bound
	 */
	public int listen(final int port) throws IOException
	{
		ServerSocket socket = new ServerSocket();
		socket.bind(new InetSocketAddress(port));
		server = socket;
		threads.submit(this::accept);
		return socket.getLocalPort();
	}

	/**
	 * Connects to one peer and shakes hands with it on a thread of its own
	 *
	 * @param host
	 *            the peer's host
	 * @param port
	 *            the peer's port
	 * @throws IOException
	 *             when the peer cannot be reached within the handshake time
	 */
	public void connect(final String host, final int port) throws IOException
	{
		Socket socket = new Socket();
		socket.connect(new InetSocketAddress(host, port), HANDSHAKE_MILLIS);
		threads.submit(() -> serve(socket));
	}

	/**
	 * Connects to every peer on a fixed list; a peer that cannot be reached is recorded as a
	 * refusal and does not stop the others
	 *
	 * @param addresses
	 *            the peers
	 */
	public void connectAll(final List<PeerAddress> addresses)
	{
		for (PeerAddress address : addresses)
		{
			try
			{
				connect(address.host(), address.port());
			}
			catch (IOException unreachable)
			{
				refusals.add(address + " could not be reached: " + unreachable.getMessage());
			}
		}
	}

	/**
	 * The HELLOs of the peers this node has shaken hands with and is still connected to
	 *
	 * @return a snapshot
	 */
	public List<Hello> peers()
	{
		List<Hello> hellos = new ArrayList<>();
		for (Peer peer : peers)
		{
			hellos.add(peer.hello());
		}
		return List.copyOf(hellos);
	}

	/**
	 * Why connections were refused, oldest first
	 *
	 * @return a snapshot
	 */
	public List<String> refusals()
	{
		return List.copyOf(refusals);
	}

	/**
	 * The node's chain
	 *
	 * @return a snapshot, genesis first
	 */
	public List<BlockBody> chain()
	{
		synchronized (lock)
		{
			return chain;
		}
	}

	private void accept()
	{
		ServerSocket socket = server;
		while (!socket.isClosed())
		{
			try
			{
				Socket accepted = socket.accept();
				threads.submit(() -> serve(accepted));
			}
			catch (IOException closed)
			{
				return;
			}
		}
	}

	private void serve(final Socket socket)
	{
		open.add(socket);
		Peer peer = null;
		try
		{
			peer = shakeHands(socket);
			if (peer == null)
			{
				return;
			}
			peers.add(peer);
			while (!socket.isClosed())
			{
				handle(peer, Frames.read(peer.in(), Frames.MAXIMUM_FRAME));
			}
		}
		catch (IOException ended)
		{
			if (peer == null)
			{
				refusals.add(socket.getRemoteSocketAddress() + " ended the handshake: "
					+ ended.getClass().getSimpleName() + " " + ended.getMessage());
			}
		}
		finally
		{
			if (peer != null)
			{
				peers.remove(peer);
			}
			open.remove(socket);
			closeQuietly(socket);
		}
	}

	private Peer shakeHands(final Socket socket) throws IOException
	{
		socket.setSoTimeout(HANDSHAKE_MILLIS);
		DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		DataOutputStream out = new DataOutputStream(
			new BufferedOutputStream(socket.getOutputStream()));
		Frames.write(out, new Frame(MessageType.HELLO, Hello.of(chain()).encode()));
		Frame first = Frames.read(in, Frames.MAXIMUM_FRAME);
		if (first.type() != MessageType.HELLO)
		{
			refusals.add(socket.getRemoteSocketAddress() + " sent " + first.type()
				+ " before HELLO");
			return null;
		}
		Hello theirs = Hello.decode(first.payload());
		Optional<String> refusal = refusalOf(theirs);
		if (refusal.isPresent())
		{
			refusals.add(socket.getRemoteSocketAddress() + ": " + refusal.get());
			return null;
		}
		socket.setSoTimeout(0);
		return new Peer(socket, in, out, theirs);
	}

	private Optional<String> refusalOf(final Hello theirs)
	{
		Hello ours = Hello.of(chain());
		if (theirs.protocolVersion() != Hello.PROTOCOL_VERSION)
		{
			return Optional.of("the peer speaks protocol version " + theirs.protocolVersion()
				+ " and this node version " + Hello.PROTOCOL_VERSION);
		}
		if (!Chain.TEST_IDENTIFIER.equals(theirs.chainIdentifier()))
		{
			return Optional.of("the peer is on chain '" + theirs.chainIdentifier()
				+ "', and this node runs only on '" + Chain.TEST_IDENTIFIER + "'");
		}
		if (!ours.genesisHash().equals(theirs.genesisHash()))
		{
			return Optional.of("the peer's genesis block is " + theirs.genesisHash()
				+ " and this node's is " + ours.genesisHash());
		}
		return Optional.empty();
	}

	/**
	 * What a node does with a frame after the handshake; the messages after HELLO arrive with the
	 * next building blocks of ADR 0003
	 */
	private void handle(final Peer peer, final Frame frame)
	{
		// nothing yet
	}

	/**
	 * Stops listening, closes every connection and its thread
	 */
	@Override
	public void close()
	{
		if (server != null)
		{
			closeQuietly(server);
		}
		for (Socket socket : open)
		{
			closeQuietly(socket);
		}
		threads.shutdownNow();
	}

	private static void closeQuietly(final java.io.Closeable closeable)
	{
		try
		{
			closeable.close();
		}
		catch (IOException alreadyGone)
		{
			// closing a socket that is already closed or broken has nothing left to do
		}
	}
}
