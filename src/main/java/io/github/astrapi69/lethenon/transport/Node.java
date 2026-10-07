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
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainFile;
import io.github.astrapi69.lethenon.ChainRejected;
import io.github.astrapi69.lethenon.Replay;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionPool.Admission;

/**
 * A node of the test network (ADR 0003): it listens, connects to a fixed list of peers, and shakes
 * hands with every connection before anything else is exchanged.
 * <p>
 * A node runs only on the test chain {@link Chain#TEST_IDENTIFIER}: it refuses a main chain at
 * start and a peer that names any other chain or another genesis block. Each connection runs on a
 * virtual thread of its own.
 * <p>
 * A node relays what it accepts: a block that extends its chain and verifies, and a transfer its
 * pool admits, to every peer except the one it came from. A block whose parent it does not know
 * makes it ask that peer for its chain and fetch the missing blocks. Every chain it adopts is
 * checked whole with {@link Replay#verify}. A peer that sends bytes that do not decode, a message
 * out of place, or a block that does not verify is disconnected.
 */
public final class Node implements AutoCloseable
{

	/** How long a peer has to say HELLO: Monero's handshake timeout of 5000 ms (ADR 0003) */
	public static final int HANDSHAKE_MILLIS = 5_000;

	/** How long a request may wait for its answer: Monero's two-minute invoke timeout (ADR 0003) */
	public static final int ANSWER_MILLIS = 120_000;

	/** The most connections a node opens: Monero's default of 12 (ADR 0003) */
	public static final int MAXIMUM_OUTGOING = 12;

	/** The most connections a node accepts (ADR 0003) */
	public static final int MAXIMUM_INCOMING = 16;

	private final AtomicInteger outgoing = new AtomicInteger();

	private final AtomicInteger incoming = new AtomicInteger();

	private volatile int answerMillis = ANSWER_MILLIS;

	private final List<Peer> peers = new CopyOnWriteArrayList<>();

	private final List<String> refusals = new CopyOnWriteArrayList<>();

	private final List<Socket> open = new CopyOnWriteArrayList<>();

	private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

	private final LocalChain local;

	private final Gossip gossip;

	private ServerSocket server;

	private final long nodeId = drawNodeId();

	private final Discovery discovery;

	private volatile int listenPort;

	private Node(final List<BlockBody> chain, final Replay replay, final ChainFile file)
	{
		this.local = new LocalChain(chain, replay);
		this.discovery = new Discovery(peers, refusals, new Discovery.Dialer()
		{
			@Override
			public int outgoing()
			{
				return outgoing.get();
			}

			@Override
			public void dial(final PeerAddress address) throws IOException
			{
				connect(address.host(), address.port());
			}
		}, threads);
		this.gossip = new Gossip(local, file, peers, refusals, discovery);
	}

	/**
	 * A random, non-zero identity for this run of the node, so that a connection to itself is
	 * recognised (Monero's {@code peer_id})
	 */
	private static long drawNodeId()
	{
		long drawn = 0L;
		while (drawn == 0L)
		{
			drawn = new SecureRandom().nextLong();
		}
		return drawn;
	}

	/**
	 * A node on the given chain
	 *
	 * @param chain
	 *            the chain, genesis first, on {@link Chain#TEST_IDENTIFIER}
	 * @return the node, not yet listening or connected
	 * @throws IllegalArgumentException
	 *             for an empty chain or one that is not the test chain
	 * @throws ChainRejected
	 *             when the chain does not verify
	 */
	public static Node on(final List<BlockBody> chain)
	{
		requireTestChain(chain);
		return new Node(chain, Replay.verify(chain), null);
	}

	/**
	 * A node that serves a chain file: it starts from the chain and the pool in it, and writes
	 * every chain it adopts and every change of its pool back, in the formats every command reads.
	 * Each waiting transfer is offered to the pool again at start, so a double spend or a transfer
	 * that no longer fits is dropped, with the reason in {@link #refusals()}, and the file
	 * rewritten.
	 *
	 * @param file
	 *            the chain file, on {@link Chain#TEST_IDENTIFIER}; it belongs to the node while the
	 *            node runs
	 * @return the node, not yet listening or connected
	 * @throws IOException
	 *             when the file cannot be read or written
	 * @throws IllegalArgumentException
	 *             for an empty chain or one that is not the test chain
	 * @throws ChainRejected
	 *             when the chain does not verify
	 */
	public static Node serving(final ChainFile file) throws IOException
	{
		List<BlockBody> chain = file.require();
		requireTestChain(chain);
		Node node = new Node(chain, Replay.verify(chain), file);
		for (SignedTransaction waiting : file.readPending())
		{
			node.gossip.admit(waiting, null);
		}
		node.gossip.writePool();
		return node;
	}

	private static void requireTestChain(final List<BlockBody> chain)
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
		listenPort = socket.getLocalPort();
		threads.submit(this::accept);
		return listenPort;
	}

	/**
	 * Connects to one peer and shakes hands with it on a thread of its own
	 *
	 * @param host
	 *            the peer's host
	 * @param port
	 *            the peer's port
	 * @throws IOException
	 *             when the peer cannot be reached within the handshake time, or this node has
	 *             {@link #MAXIMUM_OUTGOING} outgoing connections already
	 */
	public void connect(final String host, final int port) throws IOException
	{
		if (outgoing.incrementAndGet() > MAXIMUM_OUTGOING)
		{
			outgoing.decrementAndGet();
			throw new IOException("this node has " + MAXIMUM_OUTGOING
				+ " outgoing connections, its limit (ADR 0003)");
		}
		Socket socket = new Socket();
		try
		{
			socket.connect(new InetSocketAddress(host, port), HANDSHAKE_MILLIS);
		}
		catch (IOException unreachable)
		{
			outgoing.decrementAndGet();
			closeQuietly(socket);
			throw unreachable;
		}
		PeerAddress dialed = new PeerAddress(host, port);
		threads.submit(() -> serve(socket, outgoing, Optional.of(dialed)));
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
			catch (IOException notConnected)
			{
				refusals.add(address + " was not connected: " + notConnected.getMessage());
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
		return local.blocks();
	}

	private void accept()
	{
		ServerSocket socket = server;
		while (!socket.isClosed())
		{
			try
			{
				Socket accepted = socket.accept();
				if (incoming.incrementAndGet() > MAXIMUM_INCOMING)
				{
					incoming.decrementAndGet();
					refusals.add(accepted.getRemoteSocketAddress() + " was refused: this node has "
						+ MAXIMUM_INCOMING + " incoming connections, its limit (ADR 0003)");
					closeQuietly(accepted);
					continue;
				}
				threads.submit(() -> serve(accepted, incoming, Optional.empty()));
			}
			catch (IOException closed)
			{
				return;
			}
		}
	}

	/**
	 * Runs one connection to its end; the counter it was admitted under is released then
	 *
	 * @param dialed
	 *            the address this node dialled, empty for a connection it accepted
	 */
	private void serve(final Socket socket, final AtomicInteger counted,
		final Optional<PeerAddress> dialed)
	{
		open.add(socket);
		Peer peer = null;
		try
		{
			peer = shakeHands(socket, dialed);
			if (peer == null)
			{
				return;
			}
			peer.startWriting(threads);
			peers.add(peer);
			discovery.connected(peer);
			gossip.askIfAhead(peer);
			while (!socket.isClosed())
			{
				gossip.handle(peer, Frames.read(peer.in(), Frames.MAXIMUM_FRAME));
			}
		}
		catch (IOException ended)
		{
			if (peer == null)
			{
				refusals.add(socket.getRemoteSocketAddress() + " ended the handshake: "
					+ ended.getClass().getSimpleName() + " " + ended.getMessage());
			}
			else if (ended instanceof ProtocolViolation violation)
			{
				refusals.add(peer.address() + " was disconnected: " + violation.getMessage());
			}
			else if (ended instanceof SocketTimeoutException)
			{
				refusals.add(peer.address() + " did not answer within " + answerMillis
					+ " ms and was disconnected");
			}
		}
		finally
		{
			if (peer != null)
			{
				peers.remove(peer);
				closeQuietly(peer);
			}
			open.remove(socket);
			closeQuietly(socket);
			counted.decrementAndGet();
		}
	}

	private Peer shakeHands(final Socket socket, final Optional<PeerAddress> dialed)
		throws IOException
	{
		socket.setSoTimeout(HANDSHAKE_MILLIS);
		DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		DataOutputStream out = new DataOutputStream(
			new BufferedOutputStream(socket.getOutputStream()));
		Frames.write(out,
			new Frame(MessageType.HELLO, Hello.of(chain(), listenPort, nodeId).encode()));
		Frame first = Frames.read(in, Frames.MAXIMUM_FRAME);
		if (first.type() != MessageType.HELLO)
		{
			refusals.add(socket.getRemoteSocketAddress() + " sent " + first.type()
				+ " before HELLO");
			return null;
		}
		Hello theirs = Hello.decode(first.payload());
		if (theirs.nodeId() == nodeId)
		{
			refusals.add(socket.getRemoteSocketAddress() + " is this node itself");
			dialed.ifPresent(discovery::isItself);
			return null;
		}
		Optional<String> refusal = refusalOf(theirs);
		if (refusal.isPresent())
		{
			refusals.add(socket.getRemoteSocketAddress() + ": " + refusal.get());
			return null;
		}
		socket.setSoTimeout(0);
		return new Peer(socket, in, out, theirs, answerMillis, refusals::add,
			listeningAddress(socket, theirs, dialed));
	}

	/**
	 * Where a peer can be reached: the address this node dialled, or for a connection it
	 * accepted, the peer's host with the port the peer announced; empty for a peer that does
	 * not listen, which is then never passed on
	 */
	private static Optional<PeerAddress> listeningAddress(final Socket socket, final Hello theirs,
		final Optional<PeerAddress> dialed)
	{
		if (dialed.isPresent())
		{
			return dialed;
		}
		if (theirs.listenPort() == 0)
		{
			return Optional.empty();
		}
		return Optional.of(
			new PeerAddress(socket.getInetAddress().getHostAddress(), theirs.listenPort()));
	}

	/**
	 * Whether this node connects to addresses it learns from its peers; on by default. Off, it
	 * stays with the peers it was given and still passes on the addresses it knows.
	 *
	 * @param enabled
	 *            whether to dial learnt addresses
	 * @return this node
	 */
	public Node discoverPeers(final boolean enabled)
	{
		discovery.dialling(enabled);
		return this;
	}

	/**
	 * Addresses this node has heard of from its peers and not yet been connected to
	 *
	 * @return a snapshot
	 */
	public List<PeerAddress> heardOf()
	{
		return discovery.heardOf();
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
	 * Offers a block this node mined or was handed: it is adopted if it extends the chain and the
	 * chain still verifies, and then relayed to every peer
	 *
	 * @param block
	 *            the block
	 * @return whether the chain now ends with it; the reason for a refusal is in
	 *         {@link #refusals()}
	 */
	public boolean submitBlock(final BlockBody block)
	{
		return gossip.submitBlock(block);
	}

	/**
	 * Offers a transfer to the node's pool and relays it to every peer when it is admitted
	 *
	 * @param transfer
	 *            the signed transfer
	 * @return the pool's answer
	 */
	public Admission submitTransfer(final SignedTransaction transfer)
	{
		return gossip.admit(transfer, null);
	}

	/**
	 * The transfers waiting in the node's pool, in the order a block can carry them
	 *
	 * @return a snapshot
	 */
	public List<SignedTransaction> pending()
	{
		return local.pool().waiting();
	}

	/**
	 * Sets how long a request may wait for its answer, for connections made after this call;
	 * tests use it so as not to wait two minutes
	 */
	void answerWithin(final int millis)
	{
		answerMillis = millis;
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
