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
package io.github.astrapi69.lethenon.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainFile;
import io.github.astrapi69.lethenon.ChainRejected;
import io.github.astrapi69.lethenon.ConsensusRules;
import io.github.astrapi69.lethenon.Genesis;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.transport.Bootstrap;
import io.github.astrapi69.lethenon.transport.Miner;
import io.github.astrapi69.lethenon.transport.Node;
import io.github.astrapi69.lethenon.transport.Outbound;
import io.github.astrapi69.lethenon.transport.PeerAddress;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Runs a node of the test network on a chain file (ADR 0003): it listens, connects to a fixed list
 * of peers, relays and synchronises, and with --mine mines on its pool
 */
@Command(name = "node", description = "Run a node of the test network, lethenon-test-1, on a chain "
	+ "file it keeps while it runs. With --mine it mines on its pool and pays the wallet, whose "
	+ "password is the first line of standard input.")
class NodeCommand implements Callable<Integer>
{

	/** How many peers the anonymity zone takes unless --tx-proxy says otherwise: Monero's example */
	static final int DEFAULT_ANONYMITY_PEERS = 10;

	@Option(names = "--chain", required = true,
		description = "the chain file; empty, it is started with --mine or taken from a peer")
	Path chain;

	@Option(names = "--listen", required = true, description = "the TCP port, 0 for any free one")
	int listen;

	@Option(names = "--peer", description = "host:port of a peer; repeat for more, at most 12")
	List<String> peers = new ArrayList<>();

	@Option(names = "--proxy", description = "host:port of a SOCKS5 proxy, for Tor usually "
		+ "127.0.0.1:9050; every outgoing connection goes through it, and the node listens on "
		+ "127.0.0.1 unless --bind says otherwise (ADR 0004)")
	String proxy;

	@Option(names = "--tx-proxy", description = "tor,host:port[,max]: an anonymity zone through "
		+ "Tor's SOCKS proxy; onion peers given with --peer join it, at most max of them "
		+ "(default 10), and a transfer that originates on this node goes only there, or waits "
		+ "for one, never in the clear (ADR 0004)")
	String txProxy;

	@Option(names = "--bind", description = "the local address to listen on; default: every "
		+ "interface, or 127.0.0.1 with --proxy")
	String bind;

	@Option(names = "--no-discovery", description = "stay with the peers given by --peer and do "
		+ "not connect to addresses learnt from them; the node still passes on what it knows")
	boolean noDiscovery;

	@Option(names = "--mine", description = "mine on the node's pool and pay the wallet")
	boolean mine;

	@Option(names = "--wallet", description = "the wallet the mined blocks pay; needs --mine")
	Path wallet;

	@Option(names = "--pun", defaultValue = "watching is not protecting",
		description = "the words mining varies; default: ${DEFAULT-VALUE}")
	String pun;

	@Option(names = "--for", defaultValue = "0",
		description = "stop after this many seconds; default: run until interrupted")
	long seconds;

	/**
	 * Creates the command; picocli instantiates it reflectively
	 */
	NodeCommand()
	{
	}

	@Override
	public Integer call()
	{
		try
		{
			return run(System.out);
		}
		catch (ChainRejected | IllegalArgumentException | IllegalStateException
			| SecurityException | IOException refused)
		{
			System.err.println(refused.getMessage());
			return 1;
		}
	}

	private int run(final PrintStream out) throws IOException
	{
		if (mine && wallet == null)
		{
			throw new IllegalArgumentException("--mine pays a wallet: name it with --wallet");
		}
		List<PeerAddress> addresses = peers.stream().map(PeerAddress::parse).toList();
		Outbound outbound = proxy == null ? Outbound.DIRECT
			: Outbound.through(PeerAddress.parse(proxy));
		Bytes beneficiary = mine ? ChainCommand
			.openWallet(wallet, ChainCommand.firstLineOfStandardInput())
			.spendKey(SignatureSuite.ED25519) : null;
		ChainFile file = new ChainFile(chain);
		if (file.read().isEmpty())
		{
			file.write(start(addresses, beneficiary, outbound, out));
		}
		try (Node node = Node.serving(file).discoverPeers(!noDiscovery).dialingThrough(outbound))
		{
			String zone = txProxy == null ? "" : startTheZone(node, txProxy);
			int port = bind == null ? node.listen(listen)
				: node.listen(InetAddress.getByName(bind), listen);
			node.connectAll(addresses);
			out.println("node on " + Chain.TEST_IDENTIFIER + ", listening on "
				+ node.listeningOn().getHostAddress() + " port " + port + ", "
				+ addresses.size() + " peer(s) configured, connecting " + outbound + zone
				+ (mine ? ", mining for " + ChainCommand.hex(beneficiary) : ""));
			long minedBlocks = runUntilStopped(node, beneficiary);
			out.println("stopped at height " + (node.chain().size() - 1) + ", mined " + minedBlocks
				+ " block(s), " + node.pending().size() + " transfer(s) waiting, "
				+ node.peers().size() + " peer(s) connected"
				+ (txProxy == null ? "" : ", " + node.anonymousPeers().size()
					+ " anonymity peer(s)"));
			node.refusals().forEach(System.err::println);
		}
		return 0;
	}

	/**
	 * Reads tor,host:port[,max] and turns the anonymity zone on
	 *
	 * @return what the start line says about it
	 */
	private static String startTheZone(final Node node, final String value)
	{
		String[] parts = value.split(",", -1);
		if (parts.length < 2 || parts.length > 3)
		{
			throw new IllegalArgumentException(
				"--tx-proxy is tor,host:port[,max], not '" + value + "'");
		}
		if (!"tor".equals(parts[0]))
		{
			throw new IllegalArgumentException("--tx-proxy knows one network, tor, not '"
				+ parts[0] + "' (ADR 0004)");
		}
		int maximum;
		try
		{
			maximum = parts.length == 3 ? Integer.parseInt(parts[2]) : DEFAULT_ANONYMITY_PEERS;
		}
		catch (NumberFormatException notANumber)
		{
			throw new IllegalArgumentException(
				"--tx-proxy is tor,host:port[,max], not '" + value + "'");
		}
		Outbound route = Outbound.through(PeerAddress.parse(parts[1]));
		node.anonymityZone(route, maximum);
		return ", anonymity zone " + route + ", at most " + maximum + " peer(s)";
	}

	/**
	 * The genesis block of an empty chain file: mined with --mine, otherwise taken from the first
	 * peer that answers
	 */
	private List<BlockBody> start(final List<PeerAddress> addresses, final Bytes beneficiary,
		final Outbound outbound, final PrintStream out) throws IOException
	{
		Optional<BlockBody> anchored = ConsensusRules.LETHENON.anchorFor(Chain.TEST_IDENTIFIER);
		if (anchored.isPresent())
		{
			out.println("started from the genesis block fixed in the code for "
				+ Chain.TEST_IDENTIFIER);
			return List.of(anchored.get());
		}
		if (beneficiary != null)
		{
			BlockBody genesis = Genesis.start(Chain.TEST_IDENTIFIER, beneficiary, pun,
				System.currentTimeMillis());
			out.println("mined the genesis block of " + Chain.TEST_IDENTIFIER);
			return List.of(genesis);
		}
		if (addresses.isEmpty())
		{
			throw new IllegalArgumentException(chain + " is empty: start a test chain with --mine, "
				+ "or take one from a node with --peer");
		}
		List<String> reasons = new ArrayList<>();
		for (PeerAddress address : addresses)
		{
			try
			{
				List<BlockBody> genesis = Bootstrap.genesisFrom(address, outbound);
				out.println("took the genesis block from the peer at " + address);
				return genesis;
			}
			catch (IOException unanswered)
			{
				reasons.add(unanswered.getMessage());
			}
		}
		throw new IOException("no peer gave a genesis block: " + String.join("; ", reasons));
	}

	private long runUntilStopped(final Node node, final Bytes beneficiary)
	{
		Miner miner = beneficiary == null ? null : Miner.start(node, beneficiary, pun);
		try
		{
			if (seconds > 0)
			{
				Thread.sleep(seconds * 1_000L);
			}
			else
			{
				new CountDownLatch(1).await();
			}
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
		}
		finally
		{
			if (miner != null)
			{
				miner.close();
			}
		}
		return miner == null ? 0L : miner.minedBlocks();
	}
}
