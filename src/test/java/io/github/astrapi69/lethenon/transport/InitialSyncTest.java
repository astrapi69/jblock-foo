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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;

/**
 * A node that connects to a peer with more work asks for its chain at once, without waiting for
 * the next block (ADR 0003, handshake)
 */
class InitialSyncTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private final List<BlockBody> genesis = testGenesis(Bytes.of(new byte[] { 1 }));

	private final List<BlockBody> ahead = chainOf(2 * BlockRequest.LIMIT + 5);

	private List<BlockBody> chainOf(final int blocks)
	{
		List<BlockBody> chain = genesis;
		while (chain.size() < blocks)
		{
			chain = extended(chain, MINER, List.of());
		}
		return chain;
	}

	@Test
	@DisplayName("a node that connects to a peer ahead of it fetches the whole chain at once")
	void aNodeThatConnectsToAPeerAhead_fetchesTheWholeChain() throws IOException
	{
		try (Node a = Node.on(ahead); Node behind = Node.on(genesis))
		{
			behind.connect("127.0.0.1", a.listen(0));

			await("the whole chain behind", () -> behind.chain().equals(ahead));
		}
	}

	@Test
	@DisplayName("a node that a peer ahead of it connects to fetches the whole chain as well")
	void aNodeThatAPeerAheadConnectsTo_fetchesTheWholeChain() throws IOException
	{
		try (Node a = Node.on(ahead); Node behind = Node.on(genesis))
		{
			a.connect("127.0.0.1", behind.listen(0));

			await("the whole chain behind", () -> behind.chain().equals(ahead));
		}
	}

	@Test
	@DisplayName("the far end of a line of three catches up through the middle")
	void theFarEndOfALine_catchesUpThroughTheMiddle() throws IOException
	{
		try (Node a = Node.on(ahead); Node b = Node.on(genesis); Node c = Node.on(genesis))
		{
			int portOfB = b.listen(0);
			c.connect("127.0.0.1", portOfB);
			await("c and b connected", () -> b.peers().size() == 1);
			b.connect("127.0.0.1", a.listen(0));

			try
			{
				await("the whole chain at the far end", () -> c.chain().equals(ahead));
			}
			catch (AssertionError stuck)
			{
				throw new AssertionError(stuck.getMessage() + "; c holds " + c.chain().size()
					+ " blocks, c refused " + c.refusals() + ", b refused " + b.refusals(), stuck);
			}
		}
	}

	@Test
	@DisplayName("a node ahead of its peer does not ask it for anything")
	void aNodeAheadOfItsPeer_asksNothing() throws IOException
	{
		try (Node node = Node.on(ahead); Socket socket = handshake(node, Hello.of(genesis)))
		{
			socket.setSoTimeout(500);
			DataInputStream in = new DataInputStream(socket.getInputStream());

			assertThrows(SocketTimeoutException.class,
				() -> Frames.read(in, Frames.MAXIMUM_FRAME), "nothing is sent");
		}
	}

	@Test
	@DisplayName("a node with as much work as its peer does not ask it for anything")
	void aNodeWithEqualWork_asksNothing() throws IOException
	{
		try (Node node = Node.on(ahead); Socket socket = handshake(node, Hello.of(ahead)))
		{
			socket.setSoTimeout(500);
			DataInputStream in = new DataInputStream(socket.getInputStream());

			assertThrows(SocketTimeoutException.class,
				() -> Frames.read(in, Frames.MAXIMUM_FRAME), "nothing is sent");
		}
	}

	@Test
	@DisplayName("a peer that claims more work than its chain carries changes nothing")
	void aPeerThatClaimsMoreWork_changesNothing() throws IOException
	{
		Hello ours = Hello.of(genesis);
		Hello boasting = new Hello(ours.protocolVersion(), ours.chainIdentifier(),
			ours.genesisHash(), 1_000L, ours.bestHash(), BigInteger.TWO.pow(200));
		try (Node node = Node.on(genesis); Socket socket = handshake(node, boasting))
		{
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());

			assertEquals(MessageType.GET_CHAIN, Frames.read(in, Frames.MAXIMUM_FRAME).type(),
				"more work is asked for at once");
			Frames.write(out, new Frame(MessageType.CHAIN, new ChainEntry(0L, List.of()).encode()));

			await("the answer handled", () -> node.peers().size() == 1);
			assertEquals(genesis, node.chain());
		}
	}

	/**
	 * Connects a raw socket to the node and shakes hands with the given HELLO
	 */
	private static Socket handshake(final Node node, final Hello hello) throws IOException
	{
		Socket socket = new Socket("127.0.0.1", node.listen(0));
		socket.setSoTimeout(10_000);
		DataInputStream in = new DataInputStream(socket.getInputStream());
		Frames.write(new DataOutputStream(socket.getOutputStream()),
			new Frame(MessageType.HELLO, hello.encode()));
		Frames.read(in, Frames.MAXIMUM_FRAME);
		await("the handshake", () -> node.peers().size() == 1);
		return socket;
	}
}
