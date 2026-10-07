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
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainRejected;
import io.github.astrapi69.lethenon.ChainWork;
import io.github.astrapi69.lethenon.Replay;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionPool.Admission;
import io.github.astrapi69.lethenon.TransactionPool.Outcome;

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

	private final List<Peer> peers = new CopyOnWriteArrayList<>();

	private final List<String> refusals = new CopyOnWriteArrayList<>();

	private final List<Socket> open = new CopyOnWriteArrayList<>();

	private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

	private final LocalChain local;

	private ServerSocket server;

	private Node(final List<BlockBody> chain, final Replay replay)
	{
		this.local = new LocalChain(chain, replay);
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
		return new Node(chain, Replay.verify(chain));
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
			peer.startWriting(threads);
			peers.add(peer);
			askIfAhead(peer);
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
			else if (ended instanceof ProtocolViolation violation)
			{
				refusals.add(peer.address() + " was disconnected: " + violation.getMessage());
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
		}
	}

	/**
	 * Asks a peer that announced more cumulative work for its chain at once, instead of waiting
	 * for its next block (ADR 0003, handshake); what it then sends is verified like any fetch
	 */
	private void askIfAhead(final Peer peer)
	{
		if (peer.hello().work().compareTo(ChainWork.of(local.blocks())) > 0)
		{
			synchroniseWith(peer);
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
		try
		{
			return acceptBlock(block, null);
		}
		catch (ProtocolViolation refused)
		{
			refusals.add("a submitted block was refused: " + refused.getMessage());
			return false;
		}
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
		return admit(transfer, null);
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
	 * What a node does with a frame after the handshake
	 */
	private void handle(final Peer peer, final Frame frame) throws ProtocolViolation
	{
		switch (frame.type())
		{
			case HELLO -> throw new ProtocolViolation("HELLO after the handshake");
			case BLOCK -> onBlock(peer, frame);
			case TRANSFER -> admit(decodeTransfer(frame.payload()), peer);
			case GET_CHAIN -> peer.send(new Frame(MessageType.CHAIN,
				local.answer(Locator.decode(frame.payload())).encode()));
			case CHAIN -> onChain(peer, ChainEntry.decode(frame.payload()));
			case GET_BLOCKS -> peer.send(new Frame(MessageType.BLOCKS,
				CanonicalEncoding.encodeChain(local.blocksFor(BlockRequest.decode(frame.payload())))));
			case BLOCKS -> onBlocks(peer, decodeBlocks(frame.payload()));
		}
	}

	private void onBlock(final Peer peer, final Frame frame) throws ProtocolViolation
	{
		List<BlockBody> blocks = decodeBlocks(frame.payload());
		if (blocks.size() != 1)
		{
			throw new ProtocolViolation("a BLOCK message carries " + blocks.size() + " blocks");
		}
		if (!acceptBlock(blocks.getFirst(), peer) && !local.knows(blocks.getFirst()))
		{
			synchroniseWith(peer);
		}
	}

	/**
	 * Adopts a block that extends the chain and relays it
	 *
	 * @return whether it was adopted; false for a known block and for one whose parent is not the
	 *         tip
	 * @throws ProtocolViolation
	 *             when it extends the chain and the chain then does not verify
	 */
	private boolean acceptBlock(final BlockBody block, final Peer source) throws ProtocolViolation
	{
		if (!local.extendWith(block))
		{
			return false;
		}
		relay(new Frame(MessageType.BLOCK, CanonicalEncoding.encodeChain(List.of(block))), source);
		return true;
	}

	private Admission admit(final SignedTransaction transfer, final Peer source)
	{
		Admission admission = local.pool().offer(transfer);
		if (admission.outcome() == Outcome.ADMITTED)
		{
			relay(new Frame(MessageType.TRANSFER, CanonicalEncoding.encode(transfer)), source);
		}
		else if (admission.outcome() == Outcome.REFUSED)
		{
			refusals.add((source == null ? "a submitted" : source.address() + " sent a")
				+ " transfer that was refused: " + admission.reason());
		}
		return admission;
	}

	private void onChain(final Peer peer, final ChainEntry entry) throws ProtocolViolation
	{
		if (!peer.synchronising())
		{
			throw new ProtocolViolation("a CHAIN answer nobody asked for");
		}
		switch (local.plan(entry))
		{
			case LocalChain.Plan.Nothing nothing -> endSynchronising(peer);
			case LocalChain.Plan.Fetching fetching -> {
				peer.fetch(fetching.fetch());
				peer.send(new Frame(MessageType.GET_BLOCKS,
					fetching.fetch().nextRequest().encode()));
			}
		}
	}

	private void onBlocks(final Peer peer, final List<BlockBody> blocks) throws ProtocolViolation
	{
		Fetch fetch = peer.fetch();
		if (fetch == null)
		{
			throw new ProtocolViolation("BLOCKS that were not asked for");
		}
		if (blocks.isEmpty())
		{
			// the peer no longer has what it announced
			endSynchronising(peer);
			return;
		}
		BlockRequest asked = fetch.nextRequest();
		if (blocks.size() > asked.count())
		{
			throw new ProtocolViolation("BLOCKS with " + blocks.size() + " blocks for a request of "
				+ asked.count());
		}
		for (int index = 0; index < blocks.size(); index++)
		{
			if (!Blocks.hashOf(blocks.get(index)).equals(fetch.expected().get(index)))
			{
				throw new ProtocolViolation("BLOCKS carries at height " + (asked.first() + index)
					+ " a block whose hash the peer did not announce");
			}
		}
		switch (local.consider(fetch.with(blocks)))
		{
			// the chain moved while the blocks were on their way, and the fork point went with it
			case LocalChain.Outcome.Stale stale -> endSynchronising(peer);
			case LocalChain.Outcome.Waiting waiting -> continueFetching(peer, waiting.fetch());
			case LocalChain.Outcome.Switched switched -> {
				relay(new Frame(MessageType.BLOCK,
					CanonicalEncoding.encodeChain(List.of(local.blocks().getLast()))), peer);
				continueFetching(peer, switched.rest());
			}
		}
	}

	private void continueFetching(final Peer peer, final Fetch rest)
	{
		if (!rest.expected().isEmpty())
		{
			peer.fetch(rest);
			peer.send(new Frame(MessageType.GET_BLOCKS, rest.nextRequest().encode()));
		}
		else if (!rest.fetched().isEmpty())
		{
			// the candidate never carried more work: on equal work the first chain seen stays,
			// and a fork that has not overtaken within one CHAIN answer is not followed, so that a
			// peer cannot make this node hold more than that many blocks (ADR 0003)
			refusals.add(peer.address() + "'s fork from height " + (rest.base() + 1) + " was not "
				+ "followed: " + rest.fetched().size() + " blocks carry no more work"
				+ (rest.more() ? " within one CHAIN answer" : ""));
			endSynchronising(peer);
		}
		else if (rest.more())
		{
			peer.fetch(null);
			peer.send(new Frame(MessageType.GET_CHAIN, local.locator().encode()));
		}
		else
		{
			endSynchronising(peer);
		}
	}

	/**
	 * Asks a peer for its chain, unless a synchronisation with it is in flight; then one more
	 * follows when that one ends (#85)
	 */
	private void synchroniseWith(final Peer peer)
	{
		if (peer.startSynchronising())
		{
			peer.send(new Frame(MessageType.GET_CHAIN, local.locator().encode()));
		}
	}

	private void endSynchronising(final Peer peer)
	{
		if (peer.endSynchronising())
		{
			peer.send(new Frame(MessageType.GET_CHAIN, local.locator().encode()));
		}
	}

	private void relay(final Frame frame, final Peer source)
	{
		for (Peer peer : peers)
		{
			if (peer != source)
			{
				peer.send(frame);
			}
		}
	}

	private static List<BlockBody> decodeBlocks(final byte[] payload) throws ProtocolViolation
	{
		try
		{
			return CanonicalEncoding.readChain(payload);
		}
		catch (IllegalArgumentException undecodable)
		{
			throw new ProtocolViolation("bytes that do not decode as blocks: "
				+ undecodable.getMessage());
		}
	}

	private static SignedTransaction decodeTransfer(final byte[] payload) throws ProtocolViolation
	{
		try
		{
			return CanonicalEncoding.readSignedTransaction(payload);
		}
		catch (IllegalArgumentException undecodable)
		{
			throw new ProtocolViolation("bytes that do not decode as a transfer: "
				+ undecodable.getMessage());
		}
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
