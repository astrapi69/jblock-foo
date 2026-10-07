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

import static io.github.astrapi69.lethenon.transport.Networks.testGenesis;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.Bytes;

/**
 * Discovery does not dial an address whose connection is still in its handshake: between the
 * open socket and the added peer, the address is neither dialling nor a peer, and dialling it
 * again opened a second connection to the same node (#117)
 */
class DiscoveryTest
{

	private static final PeerAddress IN_HANDSHAKE = new PeerAddress("127.0.0.1", 18481);

	private static final PeerAddress NEW = new PeerAddress("127.0.0.1", 18482);

	@Test
	@DisplayName("an address in its handshake is not dialled again, a new one is")
	void anAddressInItsHandshake_isNotDialledAgain() throws Exception
	{
		List<PeerAddress> dialled = new CopyOnWriteArrayList<>();
		Discovery.Dialer dialer = new Discovery.Dialer()
		{
			@Override
			public int outgoing()
			{
				return 0;
			}

			@Override
			public void dial(final PeerAddress address)
			{
				dialled.add(address);
			}

			/**
			 * As Node does: a dialled address is in its handshake from the moment its socket is
			 * open, which is when dial returns
			 */
			@Override
			public Set<PeerAddress> handshaking()
			{
				Set<PeerAddress> busy = new HashSet<>(dialled);
				busy.add(IN_HANDSHAKE);
				return busy;
			}
		};
		ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
		try (ServerSocket server = new ServerSocket(0);
			Socket ours = new Socket("127.0.0.1", server.getLocalPort());
			Socket theirs = server.accept())
		{
			Discovery discovery = new Discovery(List.of(), new CopyOnWriteArrayList<>(), dialer,
				threads);
			Peer peer = peerOver(ours);
			peer.expectPeers();

			discovery.learn(peer, new PeerList(List.of(IN_HANDSHAKE, NEW)));

			threads.shutdown();
			threads.awaitTermination(5, TimeUnit.SECONDS);
			assertEquals(List.of(NEW), dialled);
		}
		finally
		{
			threads.shutdownNow();
		}
	}

	private static Peer peerOver(final Socket socket) throws IOException
	{
		return new Peer(socket, new DataInputStream(socket.getInputStream()),
			new DataOutputStream(socket.getOutputStream()),
			Hello.of(testGenesis(Bytes.of(new byte[] { 1 }))), Node.ANSWER_MILLIS,
			reason -> {
			});
	}
}
