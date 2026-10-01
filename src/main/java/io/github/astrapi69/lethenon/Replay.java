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
		if (chain.isEmpty())
		{
			throw new ChainRejected("an empty chain has no genesis block");
		}
		ChainState state = new ChainState();
		state.allocateGenesis(chain.getFirst());
		long transactions = 0;
		long signatures = 0;
		Bytes previousHash = Bytes.of(new byte[32]);
		for (int height = 0; height < chain.size(); height++)
		{
			BlockBody block = chain.get(height);
			requireThat(block.height() == height,
				"block " + height + " claims height " + block.height());
			requireThat(Chain.IDENTIFIER.equals(block.chainIdentifier()),
				"block " + height + " belongs to chain '" + block.chainIdentifier() + "'");
			requireThat(previousHash.equals(block.previousHash()), "block " + height
				+ " names " + block.previousHash() + " as the previous hash, and the block before "
				+ "it hashes to " + previousHash);
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
