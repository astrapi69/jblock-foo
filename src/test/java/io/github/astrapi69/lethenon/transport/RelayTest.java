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
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.DifficultyRule;
import io.github.astrapi69.lethenon.Mining;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionBody;
import io.github.astrapi69.lethenon.TransactionPool.Outcome;
import io.github.astrapi69.lethenon.Transfers;
import io.github.astrapi69.lethenon.Wallet;

/**
 * Several nodes in one test: what one node accepts reaches the others, and a node that is behind
 * fetches what it misses (ADR 0003)
 */
class RelayTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	private final Wallet holder = Wallet.create();

	private final List<BlockBody> genesis = testGenesis(holder.spendKey(SignatureSuite.ED25519));

	private SignedTransaction transfer(final List<SignedTransaction> waiting, final long leth)
	{
		return Transfers.prepare(holder, SignatureSuite.ED25519, genesis, waiting, SOMEONE,
			Amount.ofLeth(leth), Amount.ZERO, leth + " LETH");
	}

	/**
	 * Three nodes in a line, a - b - c: a and c reach each other only through b
	 */
	private static List<Node> line(final List<BlockBody> chain) throws IOException
	{
		Node a = Node.on(chain).discoverPeers(false);
		Node b = Node.on(chain).discoverPeers(false);
		Node c = Node.on(chain).discoverPeers(false);
		int portOfA = a.listen(0);
		int portOfB = b.listen(0);
		b.connect("127.0.0.1", portOfA);
		c.connect("127.0.0.1", portOfB);
		await("a line of three", () -> a.peers().size() == 1 && b.peers().size() == 2
			&& c.peers().size() == 1);
		return List.of(a, b, c);
	}

	private static void closeAll(final List<Node> nodes)
	{
		nodes.forEach(Node::close);
	}

	@Test
	@DisplayName("a block accepted at one end of a line of three reaches the other end")
	void aBlockAcceptedAtOneEnd_reachesTheOtherEnd() throws IOException
	{
		List<Node> line = line(genesis);
		try
		{
			List<BlockBody> longer = extended(genesis, MINER, List.of());

			assertTrue(line.getFirst().submitBlock(longer.getLast()));

			await("the block at the far end", () -> line.getLast().chain().equals(longer));
			assertEquals(longer, line.get(1).chain());
		}
		finally
		{
			closeAll(line);
		}
	}

	@Test
	@DisplayName("a transfer admitted at one end waits in every pool")
	void aTransferAdmittedAtOneEnd_waitsInEveryPool() throws IOException
	{
		List<Node> line = line(genesis);
		try
		{
			SignedTransaction first = transfer(List.of(), 3L);

			assertEquals(Outcome.ADMITTED, line.getLast().submitTransfer(first).outcome());

			await("the transfer at the far end",
				() -> line.getFirst().pending().equals(List.of(first)));
			assertEquals(List.of(first), line.get(1).pending());
		}
		finally
		{
			closeAll(line);
		}
	}

	@Test
	@DisplayName("a double spend is refused across the network, and the first transfer stays")
	void aDoubleSpend_isRefusedAcrossTheNetwork() throws IOException
	{
		List<Node> line = line(genesis);
		try
		{
			SignedTransaction first = transfer(List.of(), 3L);
			SignedTransaction again = transfer(List.of(), 4L);
			line.getFirst().submitTransfer(first);
			await("the first transfer at the far end",
				() -> line.getLast().pending().equals(List.of(first)));

			assertEquals(Outcome.REFUSED, line.getLast().submitTransfer(again).outcome());

			for (Node node : line)
			{
				assertEquals(List.of(first), node.pending());
			}
			assertTrue(line.getLast().refusals().getLast().contains("double spend"),
				line.getLast().refusals().toString());
		}
		finally
		{
			closeAll(line);
		}
	}

	@Test
	@DisplayName("a block carrying a waiting transfer empties every pool")
	void aBlockCarryingAWaitingTransfer_emptiesEveryPool() throws IOException
	{
		List<Node> line = line(genesis);
		try
		{
			SignedTransaction first = transfer(List.of(), 3L);
			line.getFirst().submitTransfer(first);
			await("the transfer at the far end",
				() -> line.getLast().pending().equals(List.of(first)));
			List<BlockBody> carrying = extended(genesis, MINER, List.of(first));

			line.getFirst().submitBlock(carrying.getLast());

			await("every pool empty", () -> line.stream()
				.allMatch(node -> node.pending().isEmpty() && node.chain().equals(carrying)));
		}
		finally
		{
			closeAll(line);
		}
	}

	@Test
	@DisplayName("a node behind by more than one batch fetches every missing block")
	void aNodeBehind_fetchesEveryMissingBlock() throws IOException
	{
		List<BlockBody> ahead = chainOf(3 * BlockRequest.LIMIT + 5);
		List<BlockBody> longer = extended(ahead, MINER, List.of());
		try (Node a = Node.on(ahead); Node behind = Node.on(genesis))
		{
			behind.connect("127.0.0.1", a.listen(0));
			await("the handshake", () -> a.peers().size() == 1);

			a.submitBlock(longer.getLast());

			await("the whole chain behind", () -> behind.chain().equals(longer));
		}
	}

	@Test
	@DisplayName("a node behind by more than one CHAIN answer asks again and fetches everything")
	void aNodeBehindByMoreThanOneChainAnswer_asksAgain() throws IOException
	{
		List<BlockBody> ahead = chainOf(ChainEntry.LIMIT + 2 * BlockRequest.LIMIT);
		List<BlockBody> longer = extended(ahead, MINER, List.of());
		try (Node a = Node.on(ahead); Node behind = Node.on(genesis))
		{
			behind.connect("127.0.0.1", a.listen(0));
			await("the handshake", () -> a.peers().size() == 1);

			a.submitBlock(longer.getLast());

			await("the whole chain behind", () -> behind.chain().equals(longer));
		}
	}

	@Test
	@DisplayName("a block that does not extend the tip is not adopted by a submit")
	void aBlockThatDoesNotExtendTheTip_isNotAdopted()
	{
		List<BlockBody> two = extended(extended(genesis, MINER, List.of()), MINER, List.of());
		try (Node node = Node.on(genesis))
		{
			assertEquals(false, node.submitBlock(two.getLast()));
			assertEquals(genesis, node.chain());
		}
	}

	private List<BlockBody> chainOf(final int blocks)
	{
		List<BlockBody> chain = genesis;
		while (chain.size() < blocks)
		{
			chain = extended(chain, MINER, List.of());
		}
		return chain;
	}

	record Misbehaviour(String name, Function<RelayTest, Frame> frame, String reasonNames)
	{
		@Override
		public String toString()
		{
			return name;
		}
	}

	static Stream<Misbehaviour> misbehaviours()
	{
		return Stream.of(
			new Misbehaviour("a second HELLO",
				test -> new Frame(MessageType.HELLO, Hello.of(test.genesis).encode()),
				"HELLO after the handshake"),
			new Misbehaviour("a BLOCK that does not decode",
				test -> new Frame(MessageType.BLOCK, new byte[] { 1, 2, 3 }),
				"do not decode as blocks"),
			new Misbehaviour("a TRANSFER that does not decode",
				test -> new Frame(MessageType.TRANSFER, new byte[] { 1, 2, 3 }),
				"do not decode as a transfer"),
			new Misbehaviour("a BLOCK with two blocks",
				test -> new Frame(MessageType.BLOCK,
					CanonicalEncoding.encodeChain(test.genesis)),
				"carries 2 blocks"),
			new Misbehaviour("a BLOCK that does not verify",
				test -> new Frame(MessageType.BLOCK,
					CanonicalEncoding.encodeChain(List.of(test.unfundedBlock()))),
				"does not verify"),
			new Misbehaviour("CHAIN nobody asked for",
				test -> new Frame(MessageType.CHAIN, new ChainEntry(0L, List.of()).encode()),
				"CHAIN answer nobody asked for"),
			new Misbehaviour("BLOCKS nobody asked for",
				test -> new Frame(MessageType.BLOCKS, CanonicalEncoding.encodeChain(test.genesis)),
				"not asked for"),
			new Misbehaviour("GET_BLOCKS for one block more than a batch",
				test -> new Frame(MessageType.GET_BLOCKS,
					new BlockRequest(0L, BlockRequest.LIMIT + 1).encode()),
				"1 to " + BlockRequest.LIMIT));
	}

	/**
	 * A mined block on the genesis block that carries a transfer from an account holding nothing
	 */
	private BlockBody unfundedBlock()
	{
		Wallet nobody = Wallet.create();
		Bytes account = nobody.spendKey(SignatureSuite.ED25519);
		SignedTransaction unfunded = nobody.sign(new TransactionBody(
			genesis.getFirst().chainIdentifier(), 0L, account, SOMEONE, Amount.ofLeth(1L),
			Amount.ZERO, "from nothing"), SignatureSuite.ED25519);
		return Blocks.mine(Mining.nextBlock(genesis, MINER, List.of(unfunded), "unfunded",
			Networks.GENESIS_TIME + DifficultyRule.TARGET_BLOCK_MILLIS), 1_000_000L).orElseThrow();
	}

	@ParameterizedTest(name = "{0} disconnects the peer")
	@MethodSource("misbehaviours")
	void aPeerThatMisbehaves_isDisconnected_andTheReasonRecorded(final Misbehaviour misbehaviour)
		throws IOException
	{
		try (Node node = Node.on(genesis))
		{
			int port = node.listen(0);
			try (Socket socket = new Socket("127.0.0.1", port))
			{
				socket.setSoTimeout(10_000);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				Frames.write(out, new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
				assertEquals(MessageType.HELLO, Frames.read(in, Frames.MAXIMUM_FRAME).type());
				await("the handshake", () -> node.peers().size() == 1);

				Frames.write(out, misbehaviour.frame().apply(this));

				assertThrows(EOFException.class, () -> Frames.read(in, Frames.MAXIMUM_FRAME),
					"the node closes the connection");
			}
			await("the reason", () -> node.refusals().stream()
				.anyMatch(reason -> reason.contains(misbehaviour.reasonNames())));
			assertEquals(genesis, node.chain());
			assertEquals(List.of(), node.peers());
		}
	}

	@Test
	@DisplayName("a node answers GET_BLOCKS with the blocks it has, and GET_CHAIN from the shared block")
	void aNodeAnswersRequestsFromItsChain() throws IOException
	{
		List<BlockBody> chain = chainOf(5);
		List<Bytes> hashes = new ArrayList<>();
		chain.forEach(block -> hashes.add(Blocks.hashOf(block)));
		try (Node node = Node.on(chain))
		{
			int port = node.listen(0);
			try (Socket socket = new Socket("127.0.0.1", port))
			{
				socket.setSoTimeout(10_000);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				Frames.write(out, new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
				Frames.read(in, Frames.MAXIMUM_FRAME);

				Frames.write(out, new Frame(MessageType.GET_CHAIN,
					Locator.of(hashes.subList(0, 2)).encode()));
				ChainEntry entry = ChainEntry.decode(Frames.read(in, Frames.MAXIMUM_FRAME).payload());
				Frames.write(out, new Frame(MessageType.GET_BLOCKS, new BlockRequest(3L, 5).encode()));
				Frame blocks = Frames.read(in, Frames.MAXIMUM_FRAME);

				assertEquals(new ChainEntry(1L, hashes.subList(2, 5)), entry);
				assertEquals(MessageType.BLOCKS, blocks.type());
				assertEquals(chain.subList(3, 5), CanonicalEncoding.readChain(blocks.payload()),
					"the blocks it has, not more than were asked for and not more than exist");
			}
		}
	}

	@Test
	@DisplayName("a peer that answers GET_BLOCKS with more blocks than asked for is disconnected")
	void aPeerThatSendsMoreBlocksThanAskedFor_isDisconnected() throws IOException
	{
		List<BlockBody> ahead = chainOf(BlockRequest.LIMIT + 3);
		List<Bytes> hashes = new ArrayList<>();
		ahead.forEach(block -> hashes.add(Blocks.hashOf(block)));
		try (Node node = Node.on(genesis))
		{
			int port = node.listen(0);
			try (Socket socket = new Socket("127.0.0.1", port))
			{
				socket.setSoTimeout(10_000);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				Frames.write(out, new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
				Frames.read(in, Frames.MAXIMUM_FRAME);
				Frames.write(out, new Frame(MessageType.BLOCK,
					CanonicalEncoding.encodeChain(List.of(ahead.getLast()))));
				assertEquals(MessageType.GET_CHAIN, Frames.read(in, Frames.MAXIMUM_FRAME).type());
				Frames.write(out, new Frame(MessageType.CHAIN,
					new ChainEntry(0L, hashes.subList(1, hashes.size())).encode()));
				BlockRequest asked = BlockRequest
					.decode(Frames.read(in, Frames.MAXIMUM_FRAME).payload());

				Frames.write(out, new Frame(MessageType.BLOCKS, CanonicalEncoding.encodeChain(
					ahead.subList(genesis.size(), genesis.size() + asked.count() + 1))));

				assertEquals(new BlockRequest(genesis.size(), BlockRequest.LIMIT), asked);
				assertThrows(EOFException.class, () -> Frames.read(in, Frames.MAXIMUM_FRAME),
					"the node closes the connection");
			}
			await("the reason", () -> node.refusals().stream()
				.anyMatch(reason -> reason.contains("for a request of " + BlockRequest.LIMIT)));
			assertEquals(genesis, node.chain());
		}
	}

	@Test
	@DisplayName("two blocks with an unknown parent in a row start one synchronisation, not two")
	void twoUnknownParentsInARow_startOneSynchronisation() throws IOException
	{
		List<BlockBody> ahead = chainOf(5);
		List<Bytes> hashes = new ArrayList<>();
		ahead.forEach(block -> hashes.add(Blocks.hashOf(block)));
		Frame tip = new Frame(MessageType.BLOCK,
			CanonicalEncoding.encodeChain(List.of(ahead.getLast())));
		try (Node node = Node.on(genesis))
		{
			int port = node.listen(0);
			try (Socket socket = new Socket("127.0.0.1", port))
			{
				socket.setSoTimeout(10_000);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				Frames.write(out, new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
				Frames.read(in, Frames.MAXIMUM_FRAME);
				Frames.write(out, tip);
				Frames.write(out, tip);
				assertEquals(MessageType.GET_CHAIN, Frames.read(in, Frames.MAXIMUM_FRAME).type());

				Frames.write(out, new Frame(MessageType.CHAIN,
					new ChainEntry(0L, hashes.subList(1, hashes.size())).encode()));

				assertEquals(MessageType.GET_BLOCKS, Frames.read(in, Frames.MAXIMUM_FRAME).type(),
					"the second unknown parent waits for the synchronisation in flight");
				Frames.write(out, new Frame(MessageType.BLOCKS,
					CanonicalEncoding.encodeChain(ahead.subList(genesis.size(), ahead.size()))));
				await("the chain", () -> node.chain().equals(ahead));
			}
		}
	}

	@Test
	@DisplayName("a block with an unknown parent during a fetch asks once more when the fetch ends")
	void anUnknownParentDuringAFetch_asksOnceMoreAfterIt() throws IOException
	{
		List<BlockBody> ahead = chainOf(5);
		List<BlockBody> further = extended(ahead, MINER, List.of());
		List<Bytes> hashes = new ArrayList<>();
		ahead.forEach(block -> hashes.add(Blocks.hashOf(block)));
		try (Node node = Node.on(genesis))
		{
			int port = node.listen(0);
			try (Socket socket = new Socket("127.0.0.1", port))
			{
				socket.setSoTimeout(10_000);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				Frames.write(out, new Frame(MessageType.HELLO, Hello.of(genesis).encode()));
				Frames.read(in, Frames.MAXIMUM_FRAME);
				Frames.write(out, new Frame(MessageType.BLOCK,
					CanonicalEncoding.encodeChain(List.of(ahead.getLast()))));
				Frames.read(in, Frames.MAXIMUM_FRAME);
				Frames.write(out, new Frame(MessageType.CHAIN,
					new ChainEntry(0L, hashes.subList(1, hashes.size())).encode()));
				Frames.read(in, Frames.MAXIMUM_FRAME);
				Frames.write(out, new Frame(MessageType.BLOCK,
					CanonicalEncoding.encodeChain(List.of(further.getLast()))));

				Frames.write(out, new Frame(MessageType.BLOCKS,
					CanonicalEncoding.encodeChain(ahead.subList(genesis.size(), ahead.size()))));

				assertEquals(MessageType.GET_CHAIN, Frames.read(in, Frames.MAXIMUM_FRAME).type(),
					"the block that arrived during the fetch is asked for after it");
				await("the fetched chain", () -> node.chain().equals(ahead));
			}
		}
	}

	@Test
	@DisplayName("a block more than two hours ahead of the node's clock is refused")
	void aBlockFromTheFuture_isRefused()
	{
		long now = System.currentTimeMillis();
		List<BlockBody> recent = List.of(Blocks.mine(Mining.nextBlock(
			genesis.getFirst().chainIdentifier(), List.of(), MINER, List.of(), "now", now),
			1_000_000L).orElseThrow());
		BlockBody ahead = Blocks.mine(Mining.nextBlock(recent, MINER, List.of(), "ahead",
			now + 3L * 60L * 60L * 1_000L), 1_000_000L).orElseThrow();
		try (Node node = Node.on(recent))
		{
			assertEquals(false, node.submitBlock(ahead));
			assertEquals(recent, node.chain());
			assertTrue(node.refusals().getLast().contains("in the future"),
				node.refusals().toString());
		}
	}
}
