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
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import io.github.astrapi69.lethenon.Wallet;

/**
 * Two chains from the same genesis block: the one with more work wins, the other is rolled back,
 * and its transfers go back into the pool if they still fit (ADR 0003, forks)
 */
class ForkChoiceTest
{

	private static final Bytes OURS = Bytes.of(new byte[] { 8 });

	private static final Bytes THEIRS = Bytes.of(new byte[] { 9 });

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	private final Wallet holder = Wallet.create();

	private final Bytes account = holder.spendKey(SignatureSuite.ED25519);

	private final List<BlockBody> genesis = testGenesis(account);

	private SignedTransaction transfer(final long nonce, final long leth)
	{
		return holder.sign(new TransactionBody(genesis.getFirst().chainIdentifier(), nonce,
			account, SOMEONE, Amount.ofLeth(leth), Amount.ZERO, leth + " LETH"),
			SignatureSuite.ED25519);
	}

	private List<BlockBody> chainOf(final Bytes miner, final int blocks)
	{
		List<BlockBody> chain = genesis;
		while (chain.size() < blocks)
		{
			chain = extended(chain, miner, List.of());
		}
		return chain;
	}

	@Test
	@DisplayName("a node on a lighter fork switches to the heavier one it connects to")
	void aNodeOnALighterFork_switchesWhenItConnects() throws IOException
	{
		List<BlockBody> lighter = chainOf(OURS, 3);
		List<BlockBody> heavier = chainOf(THEIRS, 4);
		try (Node heavy = Node.on(heavier); Node light = Node.on(lighter))
		{
			light.connect("127.0.0.1", heavy.listen(0));

			await("the switch", () -> light.chain().equals(heavier));
			assertEquals(heavier, heavy.chain());
		}
	}

	@Test
	@DisplayName("a node on a lighter fork switches when the heavier one connects to it")
	void aNodeOnALighterFork_switchesWhenTheHeavierConnects() throws IOException
	{
		List<BlockBody> lighter = chainOf(OURS, 3);
		List<BlockBody> heavier = chainOf(THEIRS, 4);
		try (Node heavy = Node.on(heavier); Node light = Node.on(lighter))
		{
			heavy.connect("127.0.0.1", light.listen(0));

			await("the switch", () -> light.chain().equals(heavier));
			assertEquals(heavier, heavy.chain());
		}
	}

	@Test
	@DisplayName("on equal work each node keeps its chain, until one more block decides")
	void onEqualWork_eachKeepsItsChain_untilOneMoreBlockDecides() throws IOException
	{
		List<BlockBody> ours = chainOf(OURS, 3);
		List<BlockBody> theirs = chainOf(THEIRS, 3);
		List<BlockBody> decided = extended(theirs, THEIRS, List.of());
		try (Node a = Node.on(theirs); Node b = Node.on(ours))
		{
			b.connect("127.0.0.1", a.listen(0));
			await("the handshake", () -> a.peers().size() == 1);

			assertEquals(ours, b.chain());
			assertEquals(theirs, a.chain());

			a.submitBlock(decided.getLast());

			await("the decision", () -> b.chain().equals(decided));
		}
	}

	@Test
	@DisplayName("rolled-back transfers go back into the pool, ahead of the waiting ones")
	void rolledBackTransfers_goBackIntoThePool_aheadOfTheWaitingOnes() throws IOException
	{
		SignedTransaction mined = transfer(0L, 3L);
		SignedTransaction waiting = transfer(1L, 4L);
		List<BlockBody> ours = extended(genesis, OURS, List.of(mined));
		List<BlockBody> heavier = chainOf(THEIRS, 3);
		try (Node heavy = Node.on(heavier); Node light = Node.on(ours))
		{
			assertEquals(Outcome.ADMITTED, light.submitTransfer(waiting).outcome());

			light.connect("127.0.0.1", heavy.listen(0));

			await("the switch", () -> light.chain().equals(heavier));
			assertEquals(List.of(mined, waiting), light.pending());
		}
	}

	@Test
	@DisplayName("a rolled-back transfer whose nonce the new chain spent is dropped")
	void aRolledBackTransferWhoseNonceTheNewChainSpent_isDropped() throws IOException
	{
		SignedTransaction ours = transfer(0L, 3L);
		SignedTransaction theirs = transfer(0L, 5L);
		List<BlockBody> lighter = extended(genesis, OURS, List.of(ours));
		List<BlockBody> heavier = extended(extended(genesis, THEIRS, List.of(theirs)), THEIRS,
			List.of());
		try (Node heavy = Node.on(heavier); Node light = Node.on(lighter))
		{
			light.connect("127.0.0.1", heavy.listen(0));

			await("the switch", () -> light.chain().equals(heavier));
			assertEquals(List.of(), light.pending());
		}
	}

	@Test
	@DisplayName("a heavier fork that does not verify disconnects its peer and changes nothing")
	void aHeavierForkThatDoesNotVerify_disconnects() throws IOException
	{
		List<BlockBody> ours = chainOf(OURS, 2);
		BlockBody first = extended(genesis, THEIRS, List.of()).getLast();
		List<BlockBody> forkStart = List.of(genesis.getFirst(), first);
		Wallet nobody = Wallet.create();
		Bytes empty = nobody.spendKey(SignatureSuite.ED25519);
		SignedTransaction unfunded = nobody.sign(new TransactionBody(
			genesis.getFirst().chainIdentifier(), 0L, empty, SOMEONE, Amount.ofLeth(1L),
			Amount.ZERO, "from nothing"), SignatureSuite.ED25519);
		BlockBody invalid = Blocks.mine(Mining.nextBlock(forkStart, THEIRS, List.of(unfunded),
			"unfunded", Networks.GENESIS_TIME + 2 * DifficultyRule.TARGET_BLOCK_MILLIS),
			1_000_000L).orElseThrow();
		List<BlockBody> fork = List.of(first, invalid);
		Hello claim = Hello.of(genesis);
		Hello heavy = new Hello(claim.protocolVersion(), claim.chainIdentifier(),
			claim.genesisHash(), 2L, Blocks.hashOf(invalid), BigInteger.TWO.pow(40));
		try (Node node = Node.on(ours); Socket socket = new Socket("127.0.0.1", node.listen(0)))
		{
			socket.setSoTimeout(10_000);
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			Frames.write(out, new Frame(MessageType.HELLO, heavy.encode()));
			Frames.read(in, Frames.MAXIMUM_FRAME);
			assertEquals(MessageType.GET_CHAIN, Frames.read(in, Frames.MAXIMUM_FRAME).type());
			Frames.write(out, new Frame(MessageType.CHAIN, new ChainEntry(0L,
				fork.stream().map(Blocks::hashOf).toList()).encode()));
			assertEquals(new BlockRequest(1L, 2),
				BlockRequest.decode(Frames.read(in, Frames.MAXIMUM_FRAME).payload()));

			Frames.write(out, new Frame(MessageType.BLOCKS, CanonicalEncoding.encodeChain(fork)));

			assertThrows(EOFException.class, () -> Frames.read(in, Frames.MAXIMUM_FRAME));
			await("the reason", () -> node.refusals().stream()
				.anyMatch(reason -> reason.contains("does not verify")));
			assertEquals(ours, node.chain());
		}
	}

	@Test
	@DisplayName("a fork that has not overtaken after one CHAIN answer is recorded and not followed")
	void aForkDeeperThanOneChainAnswer_isRecordedAndNotFollowed() throws IOException
	{
		List<BlockBody> ours = chainOf(OURS, ChainEntry.LIMIT + 30);
		List<BlockBody> heavier = chainOf(THEIRS, ChainEntry.LIMIT + 40);
		try (Node heavy = Node.on(heavier); Node light = Node.on(ours))
		{
			light.connect("127.0.0.1", heavy.listen(0));

			await("the record", () -> light.refusals().stream()
				.anyMatch(reason -> reason.contains("not followed")));
			assertEquals(ours, light.chain());
		}
	}
}
