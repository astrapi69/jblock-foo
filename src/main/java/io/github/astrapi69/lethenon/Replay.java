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
 * Replays a chain from genesis and says what it checked.
 * <p>
 * This is the acceptance of milestone 1 and the point of the whole project: anybody can take the
 * bytes, recompute every hash, check every signature and apply every transfer themselves. Nothing
 * has to be taken on trust from whoever produced the chain, and the check needs no identity, no
 * account and no server.
 * <p>
 * It answers with a report of what it verified, or refuses with a reason. A verifier that has never
 * rejected anything is untested, which is why a deliberately corrupted chain is part of the test
 * material.
 *
 * @param blocks
 *            how many blocks were replayed
 * @param transactions
 *            how many transfers were applied
 * @param signatures
 *            how many signatures were checked
 * @param finalState
 *            the state after the last block
 */
public record Replay(long blocks, long transactions, long signatures, ChainState finalState)
{

	/**
	 * Replays a chain
	 *
	 * @param chain
	 *            the blocks, lowest height first; the first is the genesis block. Nothing else is
	 *            needed: who holds the genesis allocation and who mined each block are named by the
	 *            blocks themselves, inside their hashes (lethenon#23)
	 * @return what was verified
	 * @throws ChainRejected
	 *             with the reason, at the first thing that does not hold
	 */
	public static Replay verify(final List<BlockBody> chain)
	{
		return verify(chain, ConsensusRules.LETHENON);
	}

	/**
	 * Replays a chain under a given consensus rule against this node's clock
	 */
	static Replay verify(final List<BlockBody> chain, final ConsensusRules rules)
	{
		return verify(chain, rules, System.currentTimeMillis());
	}

	/**
	 * Replays a chain under a given consensus rule and a given clock; the public entry runs
	 * {@link ConsensusRules#LETHENON}, and this one exists so that the rule can be shown to bite
	 * with a table that admits less
	 *
	 * @param chain
	 *            the blocks, genesis first
	 * @param rules
	 *            which schemes the chain admits at which height, and its block limits
	 * @param now
	 *            this node's clock, milliseconds since the epoch
	 * @return what was verified
	 * @throws ChainRejected
	 *             with the reason, at the first thing that does not hold
	 */
	static Replay verify(final List<BlockBody> chain, final ConsensusRules rules, final long now)
	{
		if (chain.isEmpty())
		{
			throw new ChainRejected("an empty chain has no genesis block");
		}
		BlockBody genesis = chain.getFirst();
		Optional<String> retired = Chain.retiredBecause(genesis.chainIdentifier());
		requireThat(retired.isEmpty(), "block 0: " + retired.orElse(""));
		requireThat(Chain.isKnown(genesis.chainIdentifier()), "block 0 belongs to chain '"
			+ genesis.chainIdentifier() + "', which is neither '" + Chain.IDENTIFIER + "' nor '"
			+ Chain.TEST_IDENTIFIER + "'");
		Optional<BlockBody> anchor = rules.anchorFor(genesis.chainIdentifier());
		if (anchor.isPresent())
		{
			requireThat(Blocks.hashOf(anchor.get()).equals(Blocks.hashOf(genesis)), "chain '"
				+ genesis.chainIdentifier() + "' starts with the genesis block fixed in the code, "
				+ Blocks.hashOf(anchor.get()) + ", and this chain starts with "
				+ Blocks.hashOf(genesis));
		}
		ChainState state = new ChainState(rules);
		state.allocateGenesis(genesis);
		long transactions = 0;
		long signatures = 0;
		Bytes previousHash = Bytes.of(new byte[32]);
		for (int height = 0; height < chain.size(); height++)
		{
			BlockBody block = chain.get(height);
			requireThat(block.height() == height,
				"block " + height + " claims height " + block.height());
			requireThat(genesis.chainIdentifier().equals(block.chainIdentifier()),
				"block " + height + " belongs to chain '" + block.chainIdentifier()
					+ "', and its genesis block to '" + genesis.chainIdentifier() + "'");
			requireThat(previousHash.equals(block.previousHash()), "block " + height
				+ " names " + block.previousHash() + " as the previous hash, and the block before "
				+ "it hashes to " + previousHash);
			requireRules(chain.subList(0, height), block);
			requireLimits(rules.limitsFor(genesis.chainIdentifier()), block, now);
			requireThat(Blocks.isMined(block), "block " + height + " is not mined: its hash carries "
				+ Blocks.leadingZeroBits(Blocks.hashOf(block).toByteArray())
				+ " leading zero bits and its difficulty asks for " + block.difficulty());
			if (height > 0)
			{
				// the genesis block carries the allocation rather than transfers
				state.apply(block);
				transactions += block.transactions().size();
				signatures += block.transactions().size();
			}
			previousHash = Blocks.hashOf(block);
		}
		return new Replay(chain.size(), transactions, signatures, state);
	}

	/**
	 * The two rules of lethenon#24: the difficulty a block declares is the one the rule gives for
	 * its place in the chain - a block cannot choose how hard it is - and its timestamp is later
	 * than the median of the ones before
	 */
	private static void requireRules(final List<BlockBody> before, final BlockBody block)
	{
		int required = DifficultyRule.requiredFor(before);
		requireThat(block.difficulty() == required, "block " + block.height()
			+ " declares difficulty " + block.difficulty() + ", and the rule requires " + required);
		if (!DifficultyRule.timestampAllowed(before, block.timestamp()))
		{
			throw new ChainRejected("block " + block.height() + " has timestamp "
				+ block.timestamp() + ", which is not after the median "
				+ DifficultyRule.medianTimePast(before) + " of the timestamps before it");
		}
	}

	/**
	 * The block limits of the chain, if it has any: a timestamp at most the future limit after
	 * this node's clock (#96) - the clock only moves on, so a chain that met this once meets it
	 * from then on - and a size at most the chain's maximum (#99)
	 */
	private static void requireLimits(final Optional<BlockLimits> limits, final BlockBody block,
		final long now)
	{
		if (limits.isEmpty())
		{
			return;
		}
		long latest = now + limits.get().futureMillis();
		requireThat(block.timestamp() <= latest, "block " + block.height() + " has timestamp "
			+ block.timestamp() + ", " + (block.timestamp() - now) + " ms in the future, and the "
			+ "chain allows " + limits.get().futureMillis() + " ms after this node's clock");
		int size = CanonicalEncoding.blockSize(block);
		requireThat(size <= limits.get().maximumBytes(), "block " + block.height() + " is "
			+ size + " bytes, and the chain allows " + limits.get().maximumBytes());
	}

	private static void requireThat(final boolean held, final String reason)
	{
		if (!held)
		{
			throw new ChainRejected(reason);
		}
	}

	/**
	 * What this replay checked, in one line, for a report a reader can act on
	 *
	 * @return the line
	 */
	public String describe()
	{
		return "replayed " + blocks + " blocks, applied " + transactions + " transfers, checked "
			+ signatures + " signatures; the sum of all balances is " + finalState.total()
			+ " LETH, which is the supply";
	}
}
