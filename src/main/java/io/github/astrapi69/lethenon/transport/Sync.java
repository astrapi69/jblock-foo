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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainFile;
import io.github.astrapi69.lethenon.ChainWork;
import io.github.astrapi69.lethenon.ConsensusRules;

/**
 * Brings a chain file up to one node's tip and stops (#107): what a wallet, a script or a desktop
 * application needs between copying a file by hand and running a node.
 * <p>
 * It runs a node in memory that neither listens nor discovers peers, connects it to the one peer,
 * and waits until its chain holds the tip the peer announced in its HELLO, or carries at least the
 * work the peer announced. Every block is verified by the replay a node uses, so nothing is taken
 * that the chain library does not verify, and the peer is asked for blocks only: no balance, no
 * account, nothing about the wallet (ADR 0003). The chain file is written once, at the end, and
 * only when the chain grew; a sync that fails writes nothing. The pending file is not touched.
 * <p>
 * Test network only, as every node: a main chain file is refused before anything is sent.
 */
public final class Sync
{

	/** How often the waiting looks at the node again */
	private static final long LOOK_AGAIN_MILLIS = 20L;

	private Sync()
	{
	}

	/**
	 * What a sync did
	 *
	 * @param chainIdentifier
	 *            the chain the file holds
	 * @param blocksBefore
	 *            how many blocks the file held before, 0 for an empty one
	 * @param blocksAfter
	 *            how many it holds now
	 */
	public record Synced(String chainIdentifier, long blocksBefore, long blocksAfter)
	{

		/**
		 * How many blocks the sync took from the peer
		 *
		 * @return {@code blocksAfter - blocksBefore}
		 */
		public long taken()
		{
			return blocksAfter - blocksBefore;
		}
	}

	/**
	 * Brings the chain file up to the peer's tip. An empty file starts from the genesis block fixed
	 * in the code for the test network, where there is one, and otherwise takes the peer's
	 * ({@link Bootstrap}, trust on first use).
	 *
	 * @param file
	 *            the chain file, empty or on {@link Chain#TEST_IDENTIFIER}
	 * @param peer
	 *            the node to sync from
	 * @param within
	 *            how long the whole sync may take
	 * @return what it did
	 * @throws IOException
	 *             when the file cannot be read or written, or the peer cannot be reached, refuses
	 *             the handshake, breaks off, or has not handed over its tip within the time; the
	 *             file is then as it was
	 * @throws IllegalArgumentException
	 *             for a chain file that is not on the test chain
	 * @throws io.github.astrapi69.lethenon.ChainRejected
	 *             when the chain file does not verify
	 */
	public static Synced once(final ChainFile file, final PeerAddress peer, final Duration within)
		throws IOException
	{
		return once(file, peer, within, Outbound.DIRECT);
	}

	/**
	 * {@link #once(ChainFile, PeerAddress, Duration)} over the given route: with a proxy, the
	 * genesis block and every block after it come through it
	 *
	 * @param file
	 *            the chain file, empty or on {@link Chain#TEST_IDENTIFIER}
	 * @param peer
	 *            the node to sync from; an onion address needs a proxy
	 * @param within
	 *            how long the whole sync may take
	 * @param outbound
	 *            how the connections leave this machine, for Tor through its SOCKS proxy
	 * @return what it did
	 * @throws IOException
	 *             as {@link #once(ChainFile, PeerAddress, Duration)}
	 */
	public static Synced once(final ChainFile file, final PeerAddress peer, final Duration within,
		final Outbound outbound) throws IOException
	{
		List<BlockBody> before = file.read();
		List<BlockBody> start = before.isEmpty() ? genesisFrom(peer, outbound) : before;
		try (Node node = Node.on(start).discoverPeers(false).dialingThrough(outbound))
		{
			try
			{
				node.connect(peer.host(), peer.port());
			}
			catch (IOException unreachable)
			{
				throw new IOException("the peer at " + peer + " cannot be reached: "
					+ unreachable.getMessage(), unreachable);
			}
			waitForTheTip(node, peer, within);
			List<BlockBody> after = node.chain();
			if (after.size() > before.size())
			{
				file.write(after);
			}
			return new Synced(after.getFirst().chainIdentifier(), before.size(), after.size());
		}
	}

	private static List<BlockBody> genesisFrom(final PeerAddress peer, final Outbound outbound)
		throws IOException
	{
		Optional<BlockBody> anchored = ConsensusRules.LETHENON.anchorFor(Chain.TEST_IDENTIFIER);
		if (anchored.isPresent())
		{
			return List.of(anchored.get());
		}
		try
		{
			return Bootstrap.genesisFrom(peer, outbound);
		}
		catch (IOException unanswered)
		{
			throw new IOException("the peer at " + peer + " gave no genesis block: "
				+ unanswered.getMessage(), unanswered);
		}
	}

	/**
	 * Waits until the node holds the tip its peer announced, or at least the work it announced
	 *
	 * @throws IOException
	 *             when the handshake is refused, the peer breaks off, or the time runs out
	 */
	private static void waitForTheTip(final Node node, final PeerAddress peer,
		final Duration within) throws IOException
	{
		long deadline = System.nanoTime() + within.toNanos();
		Hello announced = null;
		while (System.nanoTime() - deadline < 0)
		{
			List<Hello> peers = node.peers();
			if (!peers.isEmpty())
			{
				announced = peers.getFirst();
				if (holds(node.chain(), announced))
				{
					return;
				}
			}
			else if (announced != null || !node.refusals().isEmpty())
			{
				throw new IOException("the peer at " + peer + (announced == null
					? " refused the handshake" : " broke off at " + reached(node))
					+ ": " + String.join("; ", node.refusals()));
			}
			pause();
		}
		throw new IOException("the peer at " + peer + (announced == null
			? " did not shake hands"
			: " announced height " + announced.bestHeight() + ", and this chain reached "
				+ reached(node))
			+ " within " + within.toMillis() + " ms" + refusalsOf(node));
	}

	private static boolean holds(final List<BlockBody> chain, final Hello announced)
	{
		if (ChainWork.of(chain).compareTo(announced.work()) >= 0)
		{
			return true;
		}
		return chain.stream().anyMatch(block -> Blocks.hashOf(block).equals(announced.bestHash()));
	}

	private static String reached(final Node node)
	{
		return "height " + (node.chain().size() - 1);
	}

	private static String refusalsOf(final Node node)
	{
		List<String> refusals = node.refusals();
		return refusals.isEmpty() ? "" : ": " + String.join("; ", refusals);
	}

	private static void pause() throws IOException
	{
		try
		{
			Thread.sleep(LOOK_AGAIN_MILLIS);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new IOException("the sync was interrupted", interrupted);
		}
	}
}
