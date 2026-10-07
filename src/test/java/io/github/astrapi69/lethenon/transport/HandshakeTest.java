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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainWork;

/**
 * The first building block of the test network (#78, ADR 0003): nodes on TCP, a fixed list of
 * peers, and a handshake that carries the protocol version and the chain identifier and refuses
 * anything that is not this test chain
 */
class HandshakeTest
{

	private static final Bytes HOLDER = Bytes.of(new byte[] { 1 });

	private final List<BlockBody> chain = Networks.extended(Networks.testGenesis(HOLDER), HOLDER,
		List.of());

	@Test
	@DisplayName("HELLO carries what the ADR names, and reads back as written")
	void hello_roundTrips() throws IOException
	{
		Hello hello = Hello.of(chain);

		Hello read = Hello.decode(hello.encode());

		assertEquals(hello, read);
		assertEquals(Hello.PROTOCOL_VERSION, read.protocolVersion());
		assertEquals(Chain.TEST_IDENTIFIER, read.chainIdentifier());
		assertEquals(Blocks.hashOf(chain.getFirst()), read.genesisHash());
		assertEquals(1L, read.bestHeight());
		assertEquals(Blocks.hashOf(chain.getLast()), read.bestHash());
		assertEquals(ChainWork.of(chain), read.work());
	}

	@Test
	@DisplayName("two nodes on the same test chain shake hands, and each knows the other's tip")
	void twoNodesOnTheSameChain_shakeHands() throws IOException
	{
		try (Node first = Node.on(chain); Node second = Node.on(chain))
		{
			int port = first.listen(0);
			second.connect("127.0.0.1", port);

			await("both sides to count one peer",
				() -> first.peers().size() == 1 && second.peers().size() == 1);
			assertEquals(1L, second.peers().getFirst().bestHeight());
			assertEquals(Blocks.hashOf(chain.getLast()), first.peers().getFirst().bestHash());
		}
	}

	@Test
	@DisplayName("a node connects to every peer on its fixed list")
	void aNode_connectsToEveryPeerOnItsList() throws IOException
	{
		try (Node hub = Node.on(chain); Node left = Node.on(chain); Node right = Node.on(chain))
		{
			int leftPort = left.listen(0);
			int rightPort = right.listen(0);

			hub.connectAll(List.of(new PeerAddress("127.0.0.1", leftPort),
				new PeerAddress("127.0.0.1", rightPort)));

			await("the hub to count two peers", () -> hub.peers().size() == 2);
			await("each side to count the hub",
				() -> left.peers().size() == 1 && right.peers().size() == 1);
		}
	}

	@Test
	@DisplayName("a node on another genesis block is refused, and the reason names the genesis")
	void anotherGenesis_isRefused() throws IOException
	{
		List<BlockBody> other = Networks.testGenesis(Bytes.of(new byte[] { 2 }));
		try (Node first = Node.on(chain); Node second = Node.on(other))
		{
			int port = first.listen(0);
			second.connect("127.0.0.1", port);

			await("the refusal on both sides",
				() -> first.refusals().size() == 1 && second.refusals().size() == 1);
			assertEquals(List.of(), first.peers());
			assertEquals(List.of(), second.peers());
			assertTrue(first.refusals().getFirst().contains("genesis"), first.refusals().toString());
		}
	}

	static Stream<Arguments> hellosThatAreRefused()
	{
		Hello right = Hello.of(Networks.extended(Networks.testGenesis(HOLDER), HOLDER, List.of()));
		return Stream.of(
			Arguments.of("the main chain",
				new Hello(Hello.PROTOCOL_VERSION, Chain.IDENTIFIER, right.genesisHash(),
					right.bestHeight(), right.bestHash(), right.work()),
				"lethenon-1"),
			Arguments.of("another protocol version",
				new Hello(Hello.PROTOCOL_VERSION + 1, right.chainIdentifier(), right.genesisHash(),
					right.bestHeight(), right.bestHash(), right.work()),
				"protocol version " + (Hello.PROTOCOL_VERSION + 1)));
	}

	@ParameterizedTest(name = "a peer naming {0} is refused")
	@MethodSource("hellosThatAreRefused")
	void aHelloThatDoesNotFit_isRefused(final String description, final Hello theirs,
		final String reasonContains) throws IOException
	{
		try (Node node = Node.on(chain))
		{
			int port = node.listen(0);
			try (Socket socket = new Socket("127.0.0.1", port))
			{
				// a node that kept the connection would leave this read waiting; the timeout turns
				// that into a SocketTimeoutException, which is not the end of stream asserted below
				socket.setSoTimeout(10_000);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				Frames.write(new DataOutputStream(socket.getOutputStream()),
					new Frame(MessageType.HELLO, theirs.encode()));

				assertEquals(MessageType.HELLO, Frames.read(in, Frames.MAXIMUM_FRAME).type(),
					"the node says who it is first");
				assertThrows(EOFException.class, () -> Frames.read(in, Frames.MAXIMUM_FRAME),
					"and then closes the connection");
			}
			await("the refusal", () -> node.refusals().size() == 1);
			assertTrue(node.refusals().getFirst().contains(reasonContains),
				node.refusals().toString());
			assertEquals(List.of(), node.peers());
		}
	}

	@Test
	@DisplayName("a HELLO without the magic is refused")
	void aHelloWithoutTheMagic_isRefused()
	{
		byte[] encoded = Hello.of(chain).encode();
		encoded[0] = 'X';

		ProtocolViolation refused = assertThrows(ProtocolViolation.class,
			() -> Hello.decode(encoded));

		assertTrue(refused.getMessage().contains("LETHENON"), refused.getMessage());
	}

	@Test
	@DisplayName("a HELLO with bytes after its end is refused")
	void aHelloWithTrailingBytes_isRefused()
	{
		byte[] encoded = Hello.of(chain).encode();
		byte[] longer = java.util.Arrays.copyOf(encoded, encoded.length + 1);

		assertThrows(ProtocolViolation.class, () -> Hello.decode(longer));
	}

	@Test
	@DisplayName("a node does not run on the main chain")
	void aNode_refusesTheMainChain()
	{
		List<BlockBody> main = Networks.genesis(Chain.IDENTIFIER, HOLDER);

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Node.on(main));

		assertTrue(refused.getMessage().contains(Chain.TEST_IDENTIFIER), refused.getMessage());
	}

	@Test
	@DisplayName("a work value of any size survives the wire")
	void aLargeWork_survivesTheWire() throws IOException
	{
		Hello large = new Hello(Hello.PROTOCOL_VERSION, Chain.TEST_IDENTIFIER, Bytes.of(new byte[32]),
			7L, Bytes.of(new byte[32]), BigInteger.TWO.pow(256).multiply(BigInteger.valueOf(500_000)));

		assertEquals(large, Hello.decode(large.encode()));
	}

	@Test
	@DisplayName("a peer that closes before saying HELLO is not counted")
	void aSilentPeer_isNotCounted() throws IOException
	{
		try (Node node = Node.on(chain))
		{
			int port = node.listen(0);
			try (Socket socket = new Socket("127.0.0.1", port))
			{
				DataInputStream in = new DataInputStream(socket.getInputStream());
				Frames.read(in, Frames.MAXIMUM_FRAME);
			}
			await("the refusal", () -> node.refusals().size() == 1);
			assertEquals(List.of(), node.peers());
		}
	}
}
