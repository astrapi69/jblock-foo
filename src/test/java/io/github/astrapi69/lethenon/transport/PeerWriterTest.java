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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.Bytes;

/**
 * A connection that ends because a frame could not be written says why (#101)
 */
class PeerWriterTest
{

	@Test
	@DisplayName("a frame above the limit ends the connection, and the reason names its size")
	void aFrameAboveTheLimit_endsTheConnection_withTheReason() throws IOException
	{
		List<String> reasons = new CopyOnWriteArrayList<>();
		ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
		try (ServerSocket server = new ServerSocket(0);
			Socket ours = new Socket("127.0.0.1", server.getLocalPort());
			Socket theirs = server.accept())
		{
			Peer peer = new Peer(ours, new DataInputStream(ours.getInputStream()),
				new DataOutputStream(ours.getOutputStream()),
				Hello.of(testGenesis(Bytes.of(new byte[] { 1 }))), Node.ANSWER_MILLIS,
				reasons::add);
			peer.startWriting(threads);

			peer.send(new Frame(MessageType.BLOCKS, new byte[Frames.MAXIMUM_FRAME]));

			await("the reason", () -> !reasons.isEmpty());
			assertTrue(reasons.getFirst().contains(String.valueOf(Frames.MAXIMUM_FRAME + 1L)),
				reasons.toString());
			assertTrue(reasons.getFirst().contains("could not be written"), reasons.toString());
			await("the connection closed", ours::isClosed);
		}
		finally
		{
			threads.shutdownNow();
		}
	}

	@Test
	@DisplayName("a peer that lets its queue fill up is disconnected, and the reason says so")
	void aFullQueue_endsTheConnection_withTheReason() throws IOException
	{
		List<String> reasons = new CopyOnWriteArrayList<>();
		try (ServerSocket server = new ServerSocket(0);
			Socket ours = new Socket("127.0.0.1", server.getLocalPort());
			Socket theirs = server.accept())
		{
			Peer peer = new Peer(ours, new DataInputStream(ours.getInputStream()),
				new DataOutputStream(ours.getOutputStream()),
				Hello.of(testGenesis(Bytes.of(new byte[] { 1 }))), Node.ANSWER_MILLIS,
				reasons::add);

			for (int index = 0; index <= Peer.QUEUE_LIMIT; index++)
			{
				peer.send(new Frame(MessageType.GET_CHAIN, new byte[0]));
			}

			assertTrue(ours.isClosed());
			assertTrue(reasons.getFirst().contains(Peer.QUEUE_LIMIT + " frames waited"),
				reasons.toString());
		}
	}
}
