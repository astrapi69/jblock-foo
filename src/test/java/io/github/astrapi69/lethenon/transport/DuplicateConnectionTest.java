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

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;

/**
 * Two nodes keep one connection between them, however many each opens (#117): a second connection
 * to a node identity that is already a peer is closed, and both ends close the same one
 */
class DuplicateConnectionTest
{

	/** How long a test waits to be sure that a second connection did not stay */
	private static final long QUIET_MILLIS = 700L;

	private final List<BlockBody> genesis = testGenesis(Bytes.of(new byte[] { 1 }));

	@Test
	@DisplayName("a node that dials a peer which already dialled it ends up with one connection")
	void dialingBack_leavesOneConnection() throws Exception
	{
		try (Node a = Node.on(genesis).discoverPeers(false);
			Node b = Node.on(genesis).discoverPeers(false))
		{
			int portOfA = a.listen(0);
			int portOfB = b.listen(0);
			a.connect("127.0.0.1", portOfB);
			await("a and b connected", () -> a.peers().size() == 1 && b.peers().size() == 1);

			b.connect("127.0.0.1", portOfA);

			Thread.sleep(QUIET_MILLIS);
			assertEquals(1, a.peers().size(), "a: " + a.refusals());
			assertEquals(1, b.peers().size(), "b: " + b.refusals());
		}
	}

	@Test
	@DisplayName("two nodes that dial each other at the same time keep exactly one connection")
	void dialingEachOtherAtOnce_leavesOneConnection() throws Exception
	{
		for (int round = 0; round < 10; round++)
		{
			try (Node a = Node.on(genesis).discoverPeers(false);
				Node b = Node.on(genesis).discoverPeers(false))
			{
				int portOfA = a.listen(0);
				int portOfB = b.listen(0);

				Thread dialing = Thread.ofVirtual().start(() -> connect(a, portOfB));
				connect(b, portOfA);
				dialing.join();

				await("one connection in round " + round,
					() -> a.peers().size() == 1 && b.peers().size() == 1);
				Thread.sleep(QUIET_MILLIS / 7);
				assertEquals(1, a.peers().size(), "round " + round + ", a: " + a.refusals());
				assertEquals(1, b.peers().size(), "round " + round + ", b: " + b.refusals());
			}
		}
	}

	@Test
	@DisplayName("two commands handing over at once are not taken for one peer")
	void commandsWithoutAnIdentity_areNotDuplicates() throws Exception
	{
		try (Node node = Node.on(genesis);
			Client first = Client.open(new PeerAddress("127.0.0.1", node.listen(0)),
				Outbound.DIRECT);
			Client second = Client.open(new PeerAddress("127.0.0.1", node.listen(0)),
				Outbound.DIRECT))
		{
			first.send(new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
			second.send(new Frame(MessageType.HELLO, Hello.of(genesis).encode()));

			await("both commands connected", () -> node.peers().size() == 2);
		}
	}

	private static void connect(final Node node, final int port)
	{
		try
		{
			node.connect("127.0.0.1", port);
		}
		catch (IOException failed)
		{
			throw new IllegalStateException(failed);
		}
	}
}
