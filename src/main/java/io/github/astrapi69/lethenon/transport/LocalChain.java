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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.ChainRejected;
import io.github.astrapi69.lethenon.ChainWork;
import io.github.astrapi69.lethenon.Replay;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionPool;

/**
 * A node's chain and its pool, behind one lock: what the node knows, how it answers requests for
 * it, and the only place where the chain changes
 * <p>
 * It holds no socket. The node reads frames and sends them; this class decides what a block, a
 * locator or a peer's chain means for the chain, so that the decision can be tested without a
 * network.
 */
final class LocalChain
{

	/**
	 * What to do with a peer's CHAIN answer
	 */
	sealed interface Plan
	{
		/** the peer has nothing this node lacks */
		record Nothing() implements Plan
		{
		}

		/** the peer's chain goes on from a block of this one: fetch these blocks */
		record Fetching(Fetch fetch) implements Plan
		{
		}
	}

	/**
	 * What became of fetched blocks
	 */
	sealed interface Outcome
	{
		/** this chain no longer has the block the fetch goes on from */
		record Stale() implements Outcome
		{
		}

		/** the candidate carries no more work than this chain yet; the blocks wait in the fetch */
		record Waiting(Fetch fetch) implements Outcome
		{
		}

		/** the candidate carried more work and is this chain now; the rest of the fetch goes on */
		record Switched(Fetch rest) implements Outcome
		{
		}
	}

	private List<BlockBody> chain;

	private final List<Bytes> hashes = new ArrayList<>();

	private final Map<Bytes, Integer> heights = new HashMap<>();

	private final TransactionPool pool;

	/**
	 * @param chain
	 *            a verified chain, genesis first
	 * @param replay
	 *            its replay
	 */
	LocalChain(final List<BlockBody> chain, final Replay replay)
	{
		this.chain = List.copyOf(chain);
		for (BlockBody block : chain)
		{
			remember(Blocks.hashOf(block));
		}
		this.pool = new TransactionPool(replay);
	}

	TransactionPool pool()
	{
		return pool;
	}

	synchronized List<BlockBody> blocks()
	{
		return chain;
	}

	synchronized boolean knows(final BlockBody block)
	{
		return heights.containsKey(Blocks.hashOf(block));
	}

	synchronized Locator locator()
	{
		return Locator.of(hashes);
	}

	/**
	 * Adopts a block that extends the tip, if the chain then verifies
	 *
	 * @return whether it was adopted; false for a known block and for one whose parent is not the
	 *         tip
	 * @throws ProtocolViolation
	 *             when it extends the tip and the chain then does not verify
	 */
	synchronized boolean extendWith(final BlockBody block) throws ProtocolViolation
	{
		if (knows(block) || block.height() != chain.size()
			|| !block.previousHash().equals(hashes.getLast()))
		{
			return false;
		}
		adopt(List.of(block));
		return true;
	}

	/**
	 * Considers the blocks fetched so far: the candidate is this chain up to the fetch's base, then
	 * the fetched blocks, and it becomes this chain if its cumulative work is strictly greater and
	 * it verifies. On equal work the chain seen first stays (ADR 0003). The blocks this chain drops
	 * give their transfers back to the pool, ahead of the waiting ones, if they still fit.
	 *
	 * @throws ProtocolViolation
	 *             when the candidate carries more work and does not verify; the chain stays as it
	 *             was
	 */
	synchronized Outcome consider(final Fetch fetch) throws ProtocolViolation
	{
		int base = (int)fetch.base();
		if (base >= hashes.size() || !hashes.get(base).equals(fetch.baseHash()))
		{
			return new Outcome.Stale();
		}
		List<BlockBody> candidate = new ArrayList<>(chain.subList(0, base + 1));
		candidate.addAll(fetch.fetched());
		if (ChainWork.of(candidate).compareTo(ChainWork.of(chain)) <= 0)
		{
			return new Outcome.Waiting(fetch);
		}
		Replay replay = verified(candidate);
		List<SignedTransaction> rolledBack = new ArrayList<>();
		for (BlockBody dropped : chain.subList(base + 1, chain.size()))
		{
			rolledBack.addAll(dropped.transactions());
		}
		forgetAbove(base);
		for (BlockBody block : fetch.fetched())
		{
			remember(Blocks.hashOf(block));
		}
		chain = List.copyOf(candidate);
		pool.advanceTo(replay, rolledBack);
		return new Outcome.Switched(fetch.rebasedOn(chain.size() - 1L, hashes.getLast()));
	}

	/**
	 * The answer to a locator: the newest of its hashes this chain has, and at most
	 * {@link ChainEntry#LIMIT} hashes after it
	 *
	 * @throws ProtocolViolation
	 *             when the locator shares not even the genesis block
	 */
	synchronized ChainEntry answer(final Locator locator) throws ProtocolViolation
	{
		for (Bytes hash : locator.hashes())
		{
			Integer shared = heights.get(hash);
			if (shared != null)
			{
				int end = Math.min(hashes.size(), shared + 1 + ChainEntry.LIMIT);
				return new ChainEntry(shared, hashes.subList(shared + 1, end));
			}
		}
		throw new ProtocolViolation("a locator that shares not even the genesis block");
	}

	/**
	 * The blocks a request asks for, as far as this chain has them
	 */
	synchronized List<BlockBody> blocksFor(final BlockRequest request)
	{
		int first = (int)Math.min(request.first(), chain.size());
		int end = (int)Math.min(chain.size(), request.first() + request.count());
		return List.copyOf(chain.subList(first, end));
	}

	/**
	 * What a peer's answer to this chain's locator means: nothing to fetch, or blocks that go on
	 * from a block of this chain, its tip or one below it
	 *
	 * @throws ProtocolViolation
	 *             when the answer shares a height this chain does not have
	 */
	synchronized Plan plan(final ChainEntry entry) throws ProtocolViolation
	{
		if (entry.sharedHeight() >= chain.size())
		{
			throw new ProtocolViolation("a CHAIN answer sharing height " + entry.sharedHeight()
				+ " of a locator whose newest block is at " + (chain.size() - 1));
		}
		long shared = entry.sharedHeight();
		List<Bytes> after = entry.hashes();
		while (!after.isEmpty() && shared + 1 < chain.size()
			&& after.getFirst().equals(hashes.get((int)shared + 1)))
		{
			shared++;
			after = after.subList(1, after.size());
		}
		if (after.isEmpty())
		{
			return new Plan.Nothing();
		}
		return new Plan.Fetching(new Fetch(shared, hashes.get((int)shared), after, List.of(),
			entry.hashes().size() == ChainEntry.LIMIT));
	}

	/**
	 * Extends the chain by the given blocks if the whole chain then verifies, and moves the pool
	 * onto the new tip
	 *
	 * @throws ProtocolViolation
	 *             when the extended chain does not verify; the chain stays as it was
	 */
	private void adopt(final List<BlockBody> blocks) throws ProtocolViolation
	{
		List<BlockBody> candidate = new ArrayList<>(chain);
		candidate.addAll(blocks);
		Replay replay = verified(candidate);
		for (BlockBody block : blocks)
		{
			remember(Blocks.hashOf(block));
		}
		chain = List.copyOf(candidate);
		pool.advanceTo(replay);
	}

	private static Replay verified(final List<BlockBody> candidate) throws ProtocolViolation
	{
		try
		{
			return Replay.verify(candidate);
		}
		catch (ChainRejected rejected)
		{
			throw new ProtocolViolation("a block that does not verify: " + rejected.getMessage());
		}
	}

	private void forgetAbove(final int base)
	{
		List<Bytes> above = hashes.subList(base + 1, hashes.size());
		above.forEach(heights::remove);
		above.clear();
	}

	private void remember(final Bytes hash)
	{
		heights.put(hash, hashes.size());
		hashes.add(hash);
	}
}
