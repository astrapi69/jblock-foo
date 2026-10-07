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

import static io.github.astrapi69.lethenon.transport.Networks.extended;
import static io.github.astrapi69.lethenon.transport.Networks.testGenesis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Chain;

/**
 * A node with an empty chain file takes the genesis block from a peer, and checks it against what
 * the peer announced (ADR 0003, the node command)
 */
class BootstrapTest
{

	private final List<BlockBody> genesis = testGenesis(Bytes.of(new byte[] { 1 }));

	@Test
	@DisplayName("the genesis block is taken from a peer, whatever the peer's height")
	void theGenesisBlock_isTakenFromAPeer() throws IOException
	{
		List<BlockBody> longer = extended(extended(genesis, Bytes.of(new byte[] { 9 }), List.of()),
			Bytes.of(new byte[] { 9 }), List.of());
		try (Node node = Node.on(longer))
		{
			int port = node.listen(0);

			assertEquals(genesis, Bootstrap.genesisFrom(new PeerAddress("127.0.0.1", port)));
		}
	}

	@Test
	@DisplayName("a peer on the main chain is refused")
	void aPeerOnTheMainChain_isRefused() throws Exception
	{
		Hello main = new Hello(Hello.PROTOCOL_VERSION, Chain.IDENTIFIER,
			Blocks.hashOf(genesis.getFirst()), 0L, Blocks.hashOf(genesis.getFirst()),
			BigInteger.ONE);

		IOException refused = fakePeer(main, genesis);

		assertTrue(refused.getMessage().contains("'" + Chain.IDENTIFIER + "'"),
			refused.getMessage());
	}

	@Test
	@DisplayName("a peer whose block 0 does not hash to the genesis it announced is refused")
	void aPeerWhoseBlockDoesNotMatchItsHello_isRefused() throws Exception
	{
		List<BlockBody> other = Networks.genesis(Chain.TEST_IDENTIFIER, Bytes.of(new byte[] { 2 }));

		IOException refused = fakePeer(Hello.of(genesis), other);

		assertTrue(refused.getMessage().contains("does not hash"), refused.getMessage());
	}

	/**
	 * A peer that says the given HELLO and answers any request with the given blocks; returns what
	 * the bootstrap threw
	 */
	private static IOException fakePeer(final Hello hello, final List<BlockBody> blocks)
		throws Exception
	{
		ExecutorService thread = Executors.newVirtualThreadPerTaskExecutor();
		try (ServerSocket server = new ServerSocket(0))
		{
			thread.submit(() -> {
				try (Socket socket = server.accept())
				{
					DataInputStream in = new DataInputStream(socket.getInputStream());
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					Frames.write(out, new Frame(MessageType.HELLO, hello.encode()));
					Frames.read(in, Frames.MAXIMUM_FRAME);
					Frames.read(in, Frames.MAXIMUM_FRAME);
					Frames.write(out,
						new Frame(MessageType.BLOCKS, CanonicalEncoding.encodeChain(blocks)));
					Frames.read(in, Frames.MAXIMUM_FRAME);
				}
				return null;
			});
			return assertThrows(IOException.class,
				() -> Bootstrap.genesisFrom(new PeerAddress("127.0.0.1", server.getLocalPort())));
		}
		finally
		{
			thread.shutdownNow();
		}
	}
}
