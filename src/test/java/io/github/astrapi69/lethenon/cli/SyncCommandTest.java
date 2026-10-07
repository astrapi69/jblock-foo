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
package io.github.astrapi69.lethenon.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainFile;
import io.github.astrapi69.lethenon.transport.Node;

/**
 * lethenon sync: a chain file brought up to one node's tip, and the command stops (#107)
 */
class SyncCommandTest extends AbstractCliTest
{

	@TempDir
	Path directory;

	@Test
	@DisplayName("sync takes a node's chain into an empty file and says how many blocks it took")
	void sync_takesTheNodesChain() throws Exception
	{
		List<BlockBody> served = aTestChainOfTwoBlocks();
		Path target = directory.resolve("synced.lethenon");
		try (Node node = Node.on(served))
		{
			int port = node.listen(0);

			assertEquals(0, run("", "sync", "--chain", target.toString(), "--peer",
				"127.0.0.1:" + port), err);
		}

		assertEquals(served, new ChainFile(target).require());
		assertTrue(out.contains("chain " + Chain.TEST_IDENTIFIER), out);
		assertTrue(out.contains("took 2 block(s)"), out);
		assertTrue(out.contains("height 1"), out);
	}

	@Test
	@DisplayName("sync with a peer that cannot be reached exits 1, names the peer and writes nothing")
	void sync_withAnUnreachablePeer_failsAndWritesNothing() throws Exception
	{
		Path target = directory.resolve("never.lethenon");
		int closed;
		try (ServerSocket socket = new ServerSocket(0))
		{
			closed = socket.getLocalPort();
		}

		assertEquals(1, run("", "sync", "--chain", target.toString(), "--peer",
			"127.0.0.1:" + closed));

		assertTrue(err.contains("127.0.0.1:" + closed), err);
		assertFalse(Files.exists(target));
	}

	private List<BlockBody> aTestChainOfTwoBlocks() throws Exception
	{
		String password = aPassword();
		String wallet = directory.resolve("miner.wallet").toString();
		String chain = directory.resolve("served.lethenon").toString();
		assertEquals(0, run(password, "wallet", "create", "--wallet", wallet), err);
		assertEquals(0, run(password, "mine", "--testnet", "--chain", chain, "--wallet", wallet),
			err);
		assertEquals(0, run(password, "mine", "--chain", chain, "--wallet", wallet), err);
		return new ChainFile(Path.of(chain)).require();
	}
}
