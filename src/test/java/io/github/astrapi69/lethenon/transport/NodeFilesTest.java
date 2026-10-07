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
import static io.github.astrapi69.lethenon.transport.Networks.genesis;
import static io.github.astrapi69.lethenon.transport.Networks.testGenesis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainFile;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionBody;
import io.github.astrapi69.lethenon.Wallet;

/**
 * A node that serves a chain file keeps its chain and the shared pool there, in the formats every
 * command reads, and takes transfers handed to it over the network (ADR 0003, the pool)
 */
class NodeFilesTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	@TempDir
	Path directory;

	private final Wallet holder = Wallet.create();

	private final Bytes account = holder.spendKey(SignatureSuite.ED25519);

	private final List<BlockBody> genesis = testGenesis(account);

	private SignedTransaction transfer(final long nonce, final long leth)
	{
		return holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce, account, SOMEONE,
			Amount.ofLeth(leth), Amount.ZERO, leth + " LETH"), SignatureSuite.ED25519);
	}

	private ChainFile fileWith(final String name, final List<BlockBody> chain,
		final List<SignedTransaction> pending) throws IOException
	{
		ChainFile file = new ChainFile(directory.resolve(name));
		file.write(chain);
		file.writePending(pending);
		return file;
	}

	@Test
	@DisplayName("a serving node writes every transfer it admits and every block it adopts")
	void aServingNode_writesWhatItAdmitsAndAdopts() throws IOException
	{
		ChainFile file = fileWith("chain.lethenon", genesis, List.of());
		SignedTransaction first = transfer(0L, 3L);
		List<BlockBody> carrying = extended(genesis, MINER, List.of(first));
		try (Node node = Node.serving(file))
		{
			node.submitTransfer(first);

			assertEquals(List.of(first), file.readPending());

			node.submitBlock(carrying.getLast());

			assertEquals(carrying, file.read());
			assertEquals(List.of(), file.readPending());
		}
	}

	@Test
	@DisplayName("a node started on its files takes up the pool where it was")
	void aRestartedNode_takesUpThePool() throws IOException
	{
		SignedTransaction first = transfer(0L, 3L);
		SignedTransaction second = transfer(1L, 4L);
		ChainFile file = fileWith("chain.lethenon", genesis, List.of(first, second));

		try (Node node = Node.serving(file))
		{
			assertEquals(List.of(first, second), node.pending());
		}
	}

	@Test
	@DisplayName("a double spend in the pool file is dropped at start, and the file rewritten")
	void aDoubleSpendInThePoolFile_isDroppedAtStart() throws IOException
	{
		SignedTransaction first = transfer(0L, 3L);
		SignedTransaction again = transfer(0L, 5L);
		SignedTransaction next = transfer(1L, 1L);
		ChainFile file = fileWith("chain.lethenon", genesis, List.of(first, again, next));

		try (Node node = Node.serving(file))
		{
			assertEquals(List.of(first, next), node.pending());
			assertEquals(List.of(first, next), file.readPending());
			assertTrue(node.refusals().stream().anyMatch(reason -> reason.contains("double spend")),
				node.refusals().toString());
		}
	}

	@Test
	@DisplayName("a pool file in which nothing fits any more is emptied at start")
	void aPoolFileInWhichNothingFits_isEmptiedAtStart() throws IOException
	{
		ChainFile file = fileWith("chain.lethenon", genesis, List.of(transfer(5L, 3L)));

		try (Node node = Node.serving(file))
		{
			assertEquals(List.of(), node.pending());
			assertEquals(List.of(), file.readPending());
		}
	}

	@Test
	@DisplayName("a transfer relayed to another node lands in that node's file")
	void aRelayedTransfer_landsInTheOtherNodesFile() throws IOException
	{
		ChainFile fileOfA = fileWith("a.lethenon", genesis, List.of());
		ChainFile fileOfB = fileWith("b.lethenon", genesis, List.of());
		SignedTransaction first = transfer(0L, 3L);
		try (Node a = Node.serving(fileOfA); Node b = Node.serving(fileOfB))
		{
			b.connect("127.0.0.1", a.listen(0));
			await("the handshake", () -> a.peers().size() == 1);

			a.submitTransfer(first);

			await("the transfer in b's file", () -> pendingOf(fileOfB).equals(List.of(first)));
		}
	}

	private static List<SignedTransaction> pendingOf(final ChainFile file)
	{
		try
		{
			return file.readPending();
		}
		catch (IOException unreadable)
		{
			throw new AssertionError(unreadable);
		}
	}

	@Test
	@DisplayName("a transfer handed over reaches the node's pool")
	void aTransferHandedOver_reachesThePool() throws IOException
	{
		SignedTransaction first = transfer(0L, 3L);
		try (Node node = Node.on(genesis))
		{
			int port = node.listen(0);

			Handover.send(new PeerAddress("127.0.0.1", port), genesis, first);

			await("the transfer in the pool", () -> node.pending().equals(List.of(first)));
		}
	}

	@Test
	@DisplayName("a handover to a node on another genesis block is refused with the reason")
	void aHandoverToANodeOnAnotherGenesis_isRefused() throws IOException
	{
		List<BlockBody> other = genesis(Chain.TEST_IDENTIFIER, Bytes.of(new byte[] { 2 }));
		try (Node node = Node.on(other))
		{
			int port = node.listen(0);

			IOException refused = assertThrows(IOException.class,
				() -> Handover.send(new PeerAddress("127.0.0.1", port), genesis,
					transfer(0L, 3L)));

			assertTrue(refused.getMessage().contains("genesis"), refused.getMessage());
			assertEquals(List.of(), node.pending());
		}
	}

	@Test
	@DisplayName("a switch to a heavier fork is written, with the rolled-back transfers waiting")
	void aSwitchToAHeavierFork_isWritten() throws IOException
	{
		SignedTransaction first = transfer(0L, 3L);
		List<BlockBody> lighter = extended(genesis, Bytes.of(new byte[] { 8 }), List.of(first));
		List<BlockBody> heavier = extended(extended(genesis, MINER, List.of()), MINER, List.of());
		ChainFile file = fileWith("chain.lethenon", lighter, List.of());
		try (Node heavy = Node.on(heavier); Node light = Node.serving(file))
		{
			light.connect("127.0.0.1", heavy.listen(0));

			await("the switch in the file", () -> chainOf(file).equals(heavier));
			assertEquals(List.of(first), file.readPending());
		}
	}

	private static List<BlockBody> chainOf(final ChainFile file)
	{
		try
		{
			return file.read();
		}
		catch (IOException unreadable)
		{
			throw new AssertionError(unreadable);
		}
	}
}
