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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;

/**
 * The limits of ADR 0003 that are about connections and requests: how many peers, and how long a
 * request may wait for its answer
 */
class LimitsTest
{

	private final List<BlockBody> genesis = testGenesis(Bytes.of(new byte[] { 1 }));

	@Test
	@DisplayName("a node keeps at most twelve outgoing connections and refuses the thirteenth")
	void atMostTwelveOutgoingConnections() throws IOException
	{
		List<Node> others = new ArrayList<>();
		try (Node hub = Node.on(genesis))
		{
			List<PeerAddress> addresses = new ArrayList<>();
			for (int index = 0; index <= Node.MAXIMUM_OUTGOING; index++)
			{
				Node other = Node.on(genesis);
				others.add(other);
				addresses.add(new PeerAddress("127.0.0.1", other.listen(0)));
			}

			hub.connectAll(addresses);

			await("twelve peers", () -> hub.peers().size() == Node.MAXIMUM_OUTGOING);
			assertTrue(hub.refusals().stream()
				.anyMatch(reason -> reason.contains(Node.MAXIMUM_OUTGOING + " outgoing")),
				hub.refusals().toString());
			PeerAddress last = addresses.getLast();
			IOException refused = assertThrows(IOException.class,
				() -> hub.connect(last.host(), last.port()));
			assertTrue(refused.getMessage().contains("12"), refused.getMessage());
		}
		finally
		{
			others.forEach(Node::close);
		}
	}

	@Test
	@DisplayName("a node takes at most sixteen incoming connections and closes the seventeenth")
	void atMostSixteenIncomingConnections() throws IOException
	{
		List<Socket> sockets = new ArrayList<>();
		try (Node node = Node.on(genesis))
		{
			int port = node.listen(0);
			for (int index = 0; index < Node.MAXIMUM_INCOMING; index++)
			{
				sockets.add(handshake(port, Hello.of(genesis)));
			}
			await("sixteen peers", () -> node.peers().size() == Node.MAXIMUM_INCOMING);

			try (Socket seventeenth = new Socket("127.0.0.1", port))
			{
				seventeenth.setSoTimeout(10_000);
				DataInputStream in = new DataInputStream(seventeenth.getInputStream());

				assertThrows(EOFException.class, () -> Frames.read(in, Frames.MAXIMUM_FRAME),
					"closed without a HELLO");
			}
			assertTrue(node.refusals().stream()
				.anyMatch(reason -> reason.contains(Node.MAXIMUM_INCOMING + " incoming")),
				node.refusals().toString());
			assertEquals(Node.MAXIMUM_INCOMING, node.peers().size());
		}
		finally
		{
			for (Socket socket : sockets)
			{
				socket.close();
			}
		}
	}

	@Test
	@DisplayName("a peer that does not answer a request in time is disconnected")
	void aPeerThatDoesNotAnswer_isDisconnected() throws IOException
	{
		Hello ours = Hello.of(genesis);
		Hello ahead = new Hello(ours.protocolVersion(), ours.chainIdentifier(),
			ours.genesisHash(), 10L, ours.bestHash(), BigInteger.TWO.pow(100));
		try (Node node = Node.on(genesis))
		{
			node.answerWithin(300);
			int port = node.listen(0);
			try (Socket socket = handshake(port, ahead))
			{
				DataInputStream in = new DataInputStream(socket.getInputStream());
				assertEquals(MessageType.GET_CHAIN, Frames.read(in, Frames.MAXIMUM_FRAME).type());

				assertThrows(EOFException.class, () -> Frames.read(in, Frames.MAXIMUM_FRAME),
					"no answer, and the node closes");
			}
			await("the reason", () -> node.refusals().stream()
				.anyMatch(reason -> reason.contains("did not answer")));
		}
	}

	@Test
	@DisplayName("a quiet peer with nothing asked of it stays connected")
	void aQuietPeer_staysConnected() throws IOException, InterruptedException
	{
		List<BlockBody> longer = extended(genesis, Bytes.of(new byte[] { 9 }), List.of());
		try (Node node = Node.on(genesis))
		{
			node.answerWithin(300);
			int port = node.listen(0);
			try (Socket socket = handshake(port, Hello.of(genesis)))
			{
				Thread.sleep(1_000);

				assertEquals(1, node.peers().size(), "still connected after three timeouts");
				node.submitBlock(longer.getLast());
				DataInputStream in = new DataInputStream(socket.getInputStream());
				assertEquals(MessageType.BLOCK, Frames.read(in, Frames.MAXIMUM_FRAME).type());
			}
		}
	}

	@Test
	@DisplayName("a peer that answered may stay quiet afterwards")
	void aPeerThatAnswered_mayStayQuietAfterwards() throws IOException, InterruptedException
	{
		Hello ours = Hello.of(genesis);
		Hello ahead = new Hello(ours.protocolVersion(), ours.chainIdentifier(),
			ours.genesisHash(), 10L, ours.bestHash(), BigInteger.TWO.pow(100));
		try (Node node = Node.on(genesis))
		{
			node.answerWithin(300);
			int port = node.listen(0);
			try (Socket socket = handshake(port, ahead))
			{
				DataInputStream in = new DataInputStream(socket.getInputStream());
				assertEquals(MessageType.GET_CHAIN, Frames.read(in, Frames.MAXIMUM_FRAME).type());
				Frames.write(new DataOutputStream(socket.getOutputStream()),
					new Frame(MessageType.CHAIN, new ChainEntry(0L, List.of()).encode()));

				Thread.sleep(1_000);

				assertEquals(1, node.peers().size(), node.refusals().toString());
			}
		}
	}

	private static Socket handshake(final int port, final Hello hello) throws IOException
	{
		Socket socket = new Socket("127.0.0.1", port);
		socket.setSoTimeout(10_000);
		DataInputStream in = new DataInputStream(socket.getInputStream());
		Frames.write(new DataOutputStream(socket.getOutputStream()),
			new Frame(MessageType.HELLO, hello.encode()));
		Frames.read(in, Frames.MAXIMUM_FRAME);
		return socket;
	}
}
