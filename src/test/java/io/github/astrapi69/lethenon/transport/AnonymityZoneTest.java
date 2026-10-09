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

import static io.github.astrapi69.lethenon.transport.Networks.await;
import static io.github.astrapi69.lethenon.transport.Networks.extended;
import static io.github.astrapi69.lethenon.transport.Networks.testGenesis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionBody;
import io.github.astrapi69.lethenon.Wallet;

/**
 * An anonymity zone (ADR 0004, step 2, #116): a transfer that originates on the node goes only to
 * peers reached through the Tor proxy, never in the clear; the zone carries no chain, has its own
 * identity, and says nothing about the node's chain beyond the genesis block
 */
class AnonymityZoneTest
{

	private static final String ONION = ProxyTest.ONION;

	private static final String OTHER_ONION =
		"zonezonezonezonezonezonezonezonezonezonezonezonezonezone.onion";

	/** How long a test waits to be sure that something did not arrive */
	private static final long QUIET_MILLIS = 700L;

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private final Wallet holder = Wallet.create();

	private final Bytes account = holder.spendKey(SignatureSuite.ED25519);

	private final List<BlockBody> genesis = testGenesis(account);

	private SignedTransaction transfer(final long nonce)
	{
		return holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce, account,
			Destination.direct(Bytes.of(new byte[] { 7 })), Amount.ofLeth(1L), Amount.ZERO,
			"transfer " + nonce), SignatureSuite.ED25519);
	}

	private static void quietly() throws InterruptedException
	{
		Thread.sleep(QUIET_MILLIS);
	}

	@Test
	@DisplayName("an own transfer goes to the anonymity peer and not to the clearnet peer")
	void anOwnTransfer_goesOnlyToTheZone() throws Exception
	{
		try (Socks5Server socks = Socks5Server.start(); Node clear = Node.on(genesis);
			Node onion = Node.on(genesis);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			socks.route(ONION, 18480, onion.listen(0));
			node.connect("127.0.0.1", clear.listen(0));
			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));
			await("both connected", () -> node.peers().size() == 1
				&& node.anonymousPeers().size() == 1);

			SignedTransaction own = transfer(0L);
			node.submitTransfer(own);

			await("the transfer at the onion peer", () -> onion.pending().equals(List.of(own)));
			quietly();
			assertEquals(List.of(), clear.pending(), "nothing went out in the clear");
		}
	}

	@Test
	@DisplayName("without an anonymity peer an own transfer waits, and goes when one connects")
	void anOwnTransfer_waitsForAnAnonymityPeer() throws Exception
	{
		try (Socks5Server socks = Socks5Server.start(); Node clear = Node.on(genesis);
			Node onion = Node.on(genesis);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			node.connect("127.0.0.1", clear.listen(0));
			await("the clearnet peer", () -> node.peers().size() == 1);
			SignedTransaction own = transfer(0L);

			node.submitTransfer(own);

			quietly();
			assertEquals(List.of(), clear.pending(), "it was not sent in the clear");
			assertEquals(List.of(own), node.pending(), "it waits in the node's own pool");
			assertTrue(node.refusals().stream().anyMatch(reason -> reason.contains("waits")),
				node.refusals().toString());

			socks.route(ONION, 18480, onion.listen(0));
			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));

			await("the transfer at the onion peer", () -> onion.pending().equals(List.of(own)));
			quietly();
			assertEquals(List.of(), clear.pending());
		}
	}

	@Test
	@DisplayName("a transfer a command hands over counts as the node's own")
	void aHandedOverTransfer_countsAsOwn() throws Exception
	{
		try (Socks5Server socks = Socks5Server.start(); Node clear = Node.on(genesis);
			Node onion = Node.on(genesis);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			socks.route(ONION, 18480, onion.listen(0));
			int port = node.listen(0);
			node.connect("127.0.0.1", clear.listen(0));
			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));
			await("both connected", () -> node.peers().size() == 1
				&& node.anonymousPeers().size() == 1);
			SignedTransaction handed = transfer(0L);

			Handover.send(new PeerAddress("127.0.0.1", port), genesis, handed);

			await("the transfer at the onion peer",
				() -> onion.pending().equals(List.of(handed)));
			quietly();
			assertEquals(List.of(), clear.pending());
		}
	}

	@Test
	@DisplayName("a transfer from a clearnet node is relayed in the clear, as before, and not into the zone")
	void aTransferFromAClearnetNode_isRelayedInTheClear() throws Exception
	{
		try (Socks5Server socks = Socks5Server.start(); Node from = Node.on(genesis);
			Node to = Node.on(genesis); Node onion = Node.on(genesis);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			socks.route(ONION, 18480, onion.listen(0));
			int port = node.listen(0);
			from.connect("127.0.0.1", port);
			node.connect("127.0.0.1", to.listen(0));
			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));
			await("all connected", () -> node.peers().size() == 2
				&& node.anonymousPeers().size() == 1);
			SignedTransaction relayed = transfer(0L);

			from.submitTransfer(relayed);

			await("the transfer at the next clearnet node",
				() -> to.pending().equals(List.of(relayed)));
			quietly();
			assertEquals(List.of(), onion.pending());
		}
	}

	@Test
	@DisplayName("a transfer that arrives over the zone is admitted and relayed in the clear")
	void aTransferFromTheZone_isRelayedInTheClear() throws Exception
	{
		try (Socks5Server socks = Socks5Server.start(); Node clear = Node.on(genesis);
			Node onion = Node.on(genesis);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			socks.route(ONION, 18480, onion.listen(0));
			node.connect("127.0.0.1", clear.listen(0));
			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));
			await("both connected", () -> node.peers().size() == 1
				&& node.anonymousPeers().size() == 1 && onion.peers().size() == 1);
			SignedTransaction arriving = transfer(0L);

			onion.submitTransfer(arriving);

			await("the transfer at the clearnet peer",
				() -> clear.pending().equals(List.of(arriving)));
		}
	}

	@Test
	@DisplayName("the zone's HELLO has an identity of its own, no listening port and the genesis block as its tip")
	void theZonesHello_linksNothing() throws Exception
	{
		List<BlockBody> longer = extended(extended(genesis, MINER, List.of()), MINER, List.of());
		try (Socks5Server socks = Socks5Server.start(); ServerSocket fake = new ServerSocket(0);
			Node node = Node.on(longer).anonymityZone(Outbound.through(socks.address()), 10))
		{
			Hello clearnet = clearnetHelloOf(node);
			socks.route(ONION, 18480, fake.getLocalPort());
			CompletableFuture<Hello> zone = CompletableFuture.supplyAsync(() -> helloAt(fake));

			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));

			Hello theirs = zone.get(10, TimeUnit.SECONDS);
			assertNotEquals(clearnet.nodeId(), theirs.nodeId());
			assertEquals(0, theirs.listenPort());
			assertEquals(0L, theirs.bestHeight());
			assertEquals(clearnet.genesisHash(), theirs.bestHash());
			assertEquals(longer.size() - 1L, clearnet.bestHeight());
		}
	}

	@Test
	@DisplayName("a zone peer that asks for the chain is disconnected, with the reason")
	void aZonePeerThatAsksForTheChain_isDisconnected() throws Exception
	{
		try (Socks5Server socks = Socks5Server.start(); ServerSocket fake = new ServerSocket(0);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			socks.route(ONION, 18480, fake.getLocalPort());
			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));

			try (Socket socket = acceptAndShakeHands(fake))
			{
				Frames.write(new DataOutputStream(socket.getOutputStream()),
					new Frame(MessageType.GET_CHAIN, Locator.of(List.of(Hello.of(genesis)
						.genesisHash())).encode()));

				DataInputStream in = new DataInputStream(socket.getInputStream());
				assertThrows(EOFException.class, () -> Frames.read(in, Frames.MAXIMUM_FRAME));
			}
			await("the reason", () -> node.refusals().stream()
				.anyMatch(reason -> reason.contains("anonymity zone")));
		}
	}

	@Test
	@DisplayName("a block is never sent into the zone, and one that comes from it is not adopted")
	void blocks_doNotCrossTheZone() throws Exception
	{
		List<BlockBody> next = extended(genesis, MINER, List.of());
		try (Socks5Server socks = Socks5Server.start(); ServerSocket fake = new ServerSocket(0);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			socks.route(ONION, 18480, fake.getLocalPort());
			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));
			try (Socket socket = acceptAndShakeHands(fake))
			{
				await("the zone peer", () -> node.anonymousPeers().size() == 1);
				node.submitBlock(next.getLast());
				socket.setSoTimeout((int)QUIET_MILLIS);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				assertThrows(SocketTimeoutException.class,
					() -> Frames.read(in, Frames.MAXIMUM_FRAME), "no block reached the zone");

				Frames.write(new DataOutputStream(socket.getOutputStream()),
					new Frame(MessageType.BLOCK, CanonicalEncoding.encodeChain(
						List.of(extended(next, Bytes.of(new byte[] { 8 }), List.of()).getLast()))));
				quietly();
				assertEquals(next, node.chain(), "a block from the zone is not adopted");
				assertEquals(1, node.anonymousPeers().size(), "and the peer stays");
			}
		}
	}

	@Test
	@DisplayName("the zone takes at most as many peers as it was given")
	void theZone_takesAtMostItsMaximum() throws Exception
	{
		try (Socks5Server socks = Socks5Server.start(); Node first = Node.on(genesis);
			Node second = Node.on(genesis);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 1))
		{
			socks.route(ONION, 18480, first.listen(0));
			socks.route(OTHER_ONION, 18480, second.listen(0));

			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480"),
				PeerAddress.parse(OTHER_ONION + ":18480")));

			await("one zone peer", () -> node.anonymousPeers().size() == 1);
			assertTrue(node.refusals().stream().anyMatch(reason -> reason.contains(OTHER_ONION)
				&& reason.contains("1 anonymity")), node.refusals().toString());
		}
	}

	private Hello clearnetHelloOf(final Node node) throws IOException
	{
		try (Socket socket = new Socket("127.0.0.1", node.listen(0)))
		{
			socket.setSoTimeout(10_000);
			return Hello.decode(
				Frames.read(new DataInputStream(socket.getInputStream()), Frames.MAXIMUM_FRAME)
					.payload());
		}
	}

	/**
	 * Accepts the node's zone connection, reads its HELLO and answers with one of the same chain
	 */
	private Hello helloAt(final ServerSocket fake)
	{
		try (Socket socket = fake.accept())
		{
			socket.setSoTimeout(10_000);
			Frame first = Frames.read(new DataInputStream(socket.getInputStream()),
				Frames.MAXIMUM_FRAME);
			Frames.write(new DataOutputStream(socket.getOutputStream()),
				new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
			return Hello.decode(first.payload());
		}
		catch (IOException failed)
		{
			throw new IllegalStateException(failed);
		}
	}

	private Socket acceptAndShakeHands(final ServerSocket fake) throws IOException
	{
		fake.setSoTimeout(10_000);
		Socket socket = fake.accept();
		socket.setSoTimeout(10_000);
		Frames.read(new DataInputStream(socket.getInputStream()), Frames.MAXIMUM_FRAME);
		Frames.write(new DataOutputStream(socket.getOutputStream()),
			new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
		return socket;
	}
}
