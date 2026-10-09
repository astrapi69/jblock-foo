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
import static io.github.astrapi69.lethenon.transport.Networks.testGenesis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;

/**
 * A configured peer that could not be reached is dialled again, after a pause that grows with every
 * failed attempt, as Monero keeps redialling its priority peers (#128)
 */
class RedialTest
{

	private static final Duration FIRST = Duration.ofMillis(100);

	private static final Duration LONGEST = Duration.ofMillis(800);

	private final List<BlockBody> genesis = testGenesis(Bytes.of(new byte[] { 1 }));

	/** A port nothing listens on: bound once to find a free one, then closed */
	private static int aFreePort() throws IOException
	{
		try (ServerSocket probe = new ServerSocket(0))
		{
			return probe.getLocalPort();
		}
	}

	@Test
	@DisplayName("a configured peer that is reachable only after the first attempt is connected once it is")
	void aPeerReachableOnlyAfterTheFirstAttempt_isConnectedOnceItIs()
		throws IOException, InterruptedException
	{
		int port = aFreePort();
		PeerAddress later = new PeerAddress("127.0.0.1", port);
		try (Node node = Node.on(genesis).redialingAfter(FIRST, LONGEST))
		{
			node.connectAll(List.of(later));
			assertTrue(node.peers().isEmpty());
			// the attempts after 100 and 300 ms fail as well, before the peer starts listening
			Thread.sleep(FIRST.multipliedBy(5).toMillis());
			assertTrue(node.peers().isEmpty());
			assertEquals(1, refusalsOf(node, later), node.refusals().toString());

			try (Node peer = Node.on(genesis))
			{
				assertEquals(port, peer.listen(port));

				await("the configured peer, dialled again", () -> node.peers().size() == 1);
				assertEquals(1, refusalsOf(node, later),
					"the refusal is recorded once, not on every attempt: " + node.refusals());
			}
		}
	}

	@Test
	@DisplayName("a configured peer that disconnects is dialled again")
	void aConfiguredPeerThatDisconnects_isDialledAgain() throws IOException
	{
		try (Node node = Node.on(genesis).redialingAfter(FIRST, LONGEST))
		{
			int port;
			try (Node first = Node.on(genesis))
			{
				port = first.listen(0);
				node.connectAll(List.of(new PeerAddress("127.0.0.1", port)));
				await("the first connection", () -> node.peers().size() == 1);
			}
			await("the disconnect", () -> node.peers().isEmpty());

			try (Node again = Node.on(genesis))
			{
				assertEquals(port, again.listen(port));
				await("the configured peer, dialled again", () -> node.peers().size() == 1);
			}
		}
	}

	@Test
	@DisplayName("without a configured peer nothing is dialled")
	void withoutConfiguredPeers_nothingIsDialled() throws IOException
	{
		try (Node node = Node.on(genesis).redialingAfter(FIRST, LONGEST))
		{
			node.connectAll(List.of());
			assertTrue(node.refusals().isEmpty());
			assertTrue(node.peers().isEmpty());
		}
	}

	@ParameterizedTest(name = "after {0} failed attempt(s) the pause is {1} ms")
	@CsvSource({ "1, 100", "2, 200", "3, 400", "4, 800", "5, 800", "40, 800" })
	void thePause_doublesUpToTheLongest(final int failures, final long millis)
	{
		assertEquals(Duration.ofMillis(millis), new Redials(FIRST, LONGEST).pauseAfter(failures));
	}

	@Test
	@DisplayName("the default pauses: five seconds first, five minutes at most")
	void theDefaultPauses()
	{
		assertEquals(Duration.ofSeconds(5), Redials.FIRST);
		assertEquals(Duration.ofMinutes(5), Redials.LONGEST);
		assertEquals(Duration.ofSeconds(80), Redials.standard().pauseAfter(5));
		assertEquals(Duration.ofMinutes(5), Redials.standard().pauseAfter(7));
	}

	private static long refusalsOf(final Node node, final PeerAddress address)
	{
		return node.refusals().stream().filter(refusal -> refusal.startsWith(address.toString()))
			.count();
	}
}
