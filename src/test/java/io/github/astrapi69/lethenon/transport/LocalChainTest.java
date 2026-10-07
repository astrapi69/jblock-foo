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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Replay;

/**
 * What a peer's chain means for a node's own, decided without a socket
 */
class LocalChainTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private final List<BlockBody> genesis = testGenesis(Bytes.of(new byte[] { 1 }));

	private final List<BlockBody> two = extended(extended(genesis, MINER, List.of()), MINER,
		List.of());

	private final List<BlockBody> four = extended(extended(two, MINER, List.of()), MINER,
		List.of());

	private static LocalChain on(final List<BlockBody> chain)
	{
		return new LocalChain(chain, Replay.verify(chain));
	}

	private static List<Bytes> hashesOf(final List<BlockBody> blocks)
	{
		return blocks.stream().map(Blocks::hashOf).toList();
	}

	@Test
	@DisplayName("a peer whose chain extends this one is fetched from the tip on")
	void aChainThatExtendsThisOne_isFetchedFromTheTip() throws ProtocolViolation
	{
		LocalChain.Plan plan = on(two).plan(new ChainEntry(0L, hashesOf(four.subList(1, 5))));

		Fetch fetch = assertInstanceOfFetching(plan);
		assertEquals(2L, fetch.base(), "the hashes this chain has are skipped");
		assertEquals(3L, fetch.nextHeight());
		assertEquals(hashesOf(four.subList(3, 5)), fetch.expected());
		assertFalse(fetch.more());
	}

	@Test
	@DisplayName("a full answer is marked so that a further GET_CHAIN follows")
	void aFullAnswer_isMarkedForMore() throws ProtocolViolation
	{
		List<Bytes> full = java.util.Collections.nCopies(ChainEntry.LIMIT, Bytes.of(new byte[] { 3 }));

		assertTrue(assertInstanceOfFetching(on(genesis).plan(new ChainEntry(0L, full))).more());
	}

	@Test
	@DisplayName("a peer that has nothing this chain lacks is left alone")
	void aChainThatIsAPrefix_meansNothing() throws ProtocolViolation
	{
		assertEquals(new LocalChain.Plan.Nothing(),
			on(four).plan(new ChainEntry(0L, hashesOf(two.subList(1, 3)))));
		assertEquals(new LocalChain.Plan.Nothing(), on(four).plan(new ChainEntry(4L, List.of())));
	}

	@Test
	@DisplayName("an answer sharing a height this chain does not have is a protocol violation")
	void anAnswerBeyondTheTip_isAViolation()
	{
		assertThrows(ProtocolViolation.class,
			() -> on(two).plan(new ChainEntry(3L, List.of())));
	}

	@Test
	@DisplayName("a peer whose chain leaves this one below the tip is fetched from the fork point")
	void aChainThatLeavesBelowTheTip_isFetchedFromTheForkPoint() throws ProtocolViolation
	{
		List<BlockBody> theirs = extended(genesis, Bytes.of(new byte[] { 8 }), List.of());

		Fetch fetch = assertInstanceOfFetching(
			on(two).plan(new ChainEntry(0L, hashesOf(theirs.subList(1, 2)))));

		assertEquals(0L, fetch.base());
		assertEquals(Blocks.hashOf(genesis.getFirst()), fetch.baseHash());
		assertEquals(1L, fetch.nextHeight());
	}

	private static Fetch fetchOn(final List<BlockBody> base, final List<BlockBody> fetched)
	{
		return new Fetch(base.size() - 1L, Blocks.hashOf(base.getLast()), List.of(), fetched,
			false);
	}

	@Test
	@DisplayName("a candidate with more work becomes the chain, and the rest of the fetch goes on")
	void aCandidateWithMoreWork_becomesTheChain() throws ProtocolViolation
	{
		List<BlockBody> theirs = extended(extended(extended(genesis, Bytes.of(new byte[] { 8 }),
			List.of()), MINER, List.of()), MINER, List.of());
		LocalChain local = on(two);

		LocalChain.Outcome outcome = local.consider(fetchOn(genesis, theirs.subList(1, 4)));

		assertEquals(theirs, local.blocks());
		assertEquals(new LocalChain.Outcome.Switched(new Fetch(3L, Blocks.hashOf(theirs.getLast()),
			List.of(), List.of(), false)), outcome);
		assertTrue(local.knows(theirs.getLast()));
		assertFalse(local.knows(two.getLast()), "the dropped blocks are forgotten");
	}

	@Test
	@DisplayName("a candidate with equal work waits, and the chain seen first stays")
	void aCandidateWithEqualWork_waits() throws ProtocolViolation
	{
		List<BlockBody> theirs = extended(extended(genesis, Bytes.of(new byte[] { 8 }), List.of()),
			MINER, List.of());
		LocalChain local = on(two);
		Fetch fetch = fetchOn(genesis, theirs.subList(1, 3));

		assertEquals(new LocalChain.Outcome.Waiting(fetch), local.consider(fetch));
		assertEquals(two, local.blocks());
	}

	@Test
	@DisplayName("a fetch whose base the chain no longer has is stale")
	void aFetchWhoseBaseIsGone_isStale() throws ProtocolViolation
	{
		List<BlockBody> theirs = extended(genesis, Bytes.of(new byte[] { 8 }), List.of());

		assertEquals(new LocalChain.Outcome.Stale(),
			on(two).consider(fetchOn(theirs, four.subList(3, 5))));
		assertEquals(new LocalChain.Outcome.Stale(),
			on(two).consider(fetchOn(four, List.of())));
	}

	@Test
	@DisplayName("a heavier candidate that does not verify is refused and changes nothing")
	void aHeavierCandidateThatDoesNotVerify_isRefused()
	{
		List<BlockBody> broken = List.of(four.get(1), four.get(2), four.get(4));
		LocalChain local = on(two);

		assertThrows(ProtocolViolation.class, () -> local.consider(fetchOn(genesis, broken)));
		assertEquals(two, local.blocks());
	}

	private static Fetch assertInstanceOfFetching(final LocalChain.Plan plan)
	{
		if (plan instanceof LocalChain.Plan.Fetching fetching)
		{
			return fetching.fetch();
		}
		throw new AssertionError("expected blocks to fetch, and the plan is " + plan);
	}
}
