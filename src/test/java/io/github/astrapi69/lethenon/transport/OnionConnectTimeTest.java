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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionBody;
import io.github.astrapi69.lethenon.Wallet;

/**
 * A connection through Tor is given the time Tor needs (#127).
 * <p>
 * In the first run against a real Tor, a node gave up on an onion peer after five seconds -
 * {@code Connect timed out} - because every connection opened with the time a direct one gets. For
 * an onion service Tor answers the SOCKS CONNECT only after it has fetched the service's descriptor
 * and built a rendezvous circuit, which on a first connection takes longer. Monero separates the
 * two: {@code P2P_DEFAULT_CONNECTION_TIMEOUT} 5 seconds, {@code P2P_DEFAULT_SOCKS_CONNECT_TIMEOUT}
 * 45 seconds. The SOCKS5 server here answers every CONNECT after seven seconds.
 */
class OnionConnectTimeTest
{

	private static final String ONION = ProxyTest.ONION;

	/** Longer than a direct connection may take, well within what a connection through Tor may */
	private static final Duration FIRST_CIRCUIT = Duration.ofSeconds(7);

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private final Wallet holder = Wallet.create();

	private final Bytes account = holder.spendKey(SignatureSuite.ED25519);

	private final List<BlockBody> genesis = testGenesis(account);

	@Test
	@DisplayName("a zone node reaches an onion peer whose first circuit takes longer than a direct connection may")
	void aZoneNode_waitsForTheFirstCircuit() throws IOException
	{
		try (Socks5Server socks = Socks5Server.start(); Node onion = Node.on(genesis);
			Node node = Node.on(genesis).anonymityZone(Outbound.through(socks.address()), 10))
		{
			socks.route(ONION, 18480, onion.listen(0)).answeringConnectsAfter(FIRST_CIRCUIT);

			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));

			await("the onion peer in the zone", () -> node.anonymousPeers().size() == 1);
			assertEquals(List.of(), node.refusals(), "nothing was refused");
		}
	}

	@Test
	@DisplayName("a node with --proxy takes the chain from an onion peer whose first circuit is slow")
	void aProxiedNode_waitsForTheFirstCircuit() throws IOException
	{
		List<BlockBody> ahead = extended(extended(genesis, MINER, List.of()), MINER, List.of());
		try (Socks5Server socks = Socks5Server.start(); Node peer = Node.on(ahead);
			Node node = Node.on(genesis).dialingThrough(Outbound.through(socks.address())))
		{
			socks.route(ONION, 18480, peer.listen(0)).answeringConnectsAfter(FIRST_CIRCUIT);

			node.connectAll(List.of(PeerAddress.parse(ONION + ":18480")));

			await("the chain from the onion peer", () -> node.chain().equals(ahead));
		}
	}

	@Test
	@DisplayName("send --node hands a transfer over through a slow first circuit")
	void aHandover_waitsForTheFirstCircuit() throws IOException
	{
		SignedTransaction transfer = holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, 0L,
			account, Destination.direct(Bytes.of(new byte[] { 7 })), Amount.ofLeth(3L), Amount.ZERO,
			"through a slow circuit"), SignatureSuite.ED25519);
		try (Socks5Server socks = Socks5Server.start(); Node node = Node.on(genesis))
		{
			socks.route(ONION, 18480, node.listen(0)).answeringConnectsAfter(FIRST_CIRCUIT);

			Handover.send(PeerAddress.parse(ONION + ":18480"), genesis, transfer,
				Outbound.through(socks.address()));

			await("the transfer in the pool", () -> node.pending().equals(List.of(transfer)));
		}
	}

	@Test
	@DisplayName("a direct connection keeps five seconds, one through a proxy gets Monero's 45")
	void theConnectTime_belongsToTheRoute()
	{
		assertEquals(5_000, Outbound.DIRECT.connectMillis());
		assertEquals(45_000,
			Outbound.through(new PeerAddress("127.0.0.1", 9050)).connectMillis());
		assertTrue(Outbound.through(new PeerAddress("127.0.0.1", 9050))
			.connectMillis() > FIRST_CIRCUIT.toMillis(), "the tests above are inside the limit");
	}
}
