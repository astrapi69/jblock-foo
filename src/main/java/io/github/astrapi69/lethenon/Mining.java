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
package io.github.astrapi69.lethenon;

import java.util.List;
import java.util.Optional;

/**
 * The block a miner looks for a pun for (lethenon#32).
 * <p>
 * The difficulty is the one {@link DifficultyRule} requires at this height - a block cannot choose
 * how hard it is (lethenon#24) - and the timestamp is the given time, or one millisecond after the
 * median of the blocks before when that time is not later: a clock behind the chain still has to
 * produce a block the replay accepts. The time is a parameter so that a test, or a replay of a
 * mining run, can fix it.
 */
public final class Mining
{

	/**
	 * The most bytes mining adds to a pun: {@link Blocks#mine} appends " #" and the attempt, at
	 * most 19 digits
	 */
	static final int MINING_SUFFIX_BYTES = 2 + 19;

	private Mining()
	{
	}

	/**
	 * The next block of the chain, before it was mined, on the chain its genesis block names - the
	 * main chain when there is no genesis block yet
	 *
	 * @param chain
	 *            the chain so far, genesis first; empty for a new main chain
	 * @param beneficiary
	 *            whom the block pays: on an empty chain the genesis holder, otherwise the miner
	 * @param waiting
	 *            the transfers to carry, in the order they wait; the block carries the longest
	 *            prefix that fits the chain's size limit. Ignored for the genesis block, which
	 *            carries the
	 *            allocation instead
	 * @param pun
	 *            the words mining starts from
	 * @param now
	 *            the current time, milliseconds since the epoch
	 * @return the block to mine with {@link Blocks#mine}
	 */
	public static BlockBody nextBlock(final List<BlockBody> chain, final Bytes beneficiary,
		final List<SignedTransaction> waiting, final String pun, final long now)
	{
		String chainIdentifier = chain.isEmpty()
			? Chain.IDENTIFIER
			: chain.getFirst().chainIdentifier();
		return nextBlock(chainIdentifier, chain, beneficiary, waiting, pun, now);
	}

	/**
	 * The next block of the named chain, before it was mined
	 *
	 * @param chainIdentifier
	 *            {@link Chain#IDENTIFIER} or {@link Chain#TEST_IDENTIFIER}; for an empty chain it
	 *            chooses the chain, otherwise it has to be the one the genesis block carries
	 * @param chain
	 *            the chain so far, genesis first; empty for a new chain
	 * @param beneficiary
	 *            whom the block pays: on an empty chain the genesis holder, otherwise the miner
	 * @param waiting
	 *            the transfers to carry, in the order they wait; the block carries the longest
	 *            prefix that fits the chain's size limit. Ignored for the genesis block, which
	 *            carries the
	 *            allocation instead
	 * @param pun
	 *            the words mining starts from
	 * @param now
	 *            the current time, milliseconds since the epoch
	 * @return the block to mine with {@link Blocks#mine}
	 * @throws IllegalArgumentException
	 *             when the identifier names no chain, or not the one the genesis block decided
	 */
	public static BlockBody nextBlock(final String chainIdentifier, final List<BlockBody> chain,
		final Bytes beneficiary, final List<SignedTransaction> waiting, final String pun,
		final long now)
	{
		Optional<String> retired = Chain.retiredBecause(chainIdentifier);
		if (retired.isPresent())
		{
			throw new IllegalArgumentException(retired.get());
		}
		if (!Chain.isKnown(chainIdentifier))
		{
			throw new IllegalArgumentException("'" + chainIdentifier + "' names no chain; there are '"
				+ Chain.IDENTIFIER + "' and '" + Chain.TEST_IDENTIFIER + "'");
		}
		if (!chain.isEmpty() && !chainIdentifier.equals(chain.getFirst().chainIdentifier()))
		{
			throw new IllegalArgumentException("this chain is '"
				+ chain.getFirst().chainIdentifier() + "', as its genesis block decided, so it "
				+ "takes no block for '" + chainIdentifier + "'");
		}
		int difficulty = DifficultyRule.requiredFor(chain);
		if (chain.isEmpty())
		{
			return new BlockBody(chainIdentifier, 0L, Bytes.of(new byte[32]), beneficiary,
				List.of(), now, difficulty, pun);
		}
		long timestamp = Math.max(now, DifficultyRule.medianTimePast(chain) + 1L);
		BlockBody last = chain.getLast();
		BlockBody empty = new BlockBody(chainIdentifier, last.height() + 1L, Blocks.hashOf(last),
			beneficiary, List.of(), timestamp, difficulty, pun);
		return new BlockBody(chainIdentifier, empty.height(), empty.previousHash(), beneficiary,
			fitting(empty, waiting), timestamp, difficulty, pun);
	}

	/**
	 * The longest prefix of the waiting transfers that keeps the block within its chain's size
	 * limit (#99), with room for what {@link Blocks#mine} adds to the pun. A prefix, because a
	 * sender's transfers wait in nonce order: one left out would make every later one of that
	 * sender invalid.
	 */
	private static List<SignedTransaction> fitting(final BlockBody empty,
		final List<SignedTransaction> waiting)
	{
		Optional<BlockLimits> limits = ConsensusRules.LETHENON
			.limitsFor(empty.chainIdentifier());
		if (limits.isEmpty())
		{
			return waiting;
		}
		return fitting(empty, waiting, limits.get().maximumBytes());
	}

	/**
	 * The longest prefix of the waiting transfers that keeps the block within the given size,
	 * with room for what mining adds to the pun
	 */
	static List<SignedTransaction> fitting(final BlockBody empty,
		final List<SignedTransaction> waiting, final int maximumBytes)
	{
		long room = (long)maximumBytes - CanonicalEncoding.blockSize(empty)
			- MINING_SUFFIX_BYTES;
		int carried = 0;
		for (SignedTransaction transfer : waiting)
		{
			room -= CanonicalEncoding.sizeInBlock(transfer);
			if (room < 0)
			{
				break;
			}
			carried++;
		}
		return List.copyOf(waiting.subList(0, carried));
	}
}
