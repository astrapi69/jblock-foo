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

import java.util.ArrayList;
import java.util.List;

/**
 * A short mined chain around given transfers, one transfer per block after the genesis block, for
 * tests that need a chain to replay rather than a chain to examine
 */
final class TestChains
{

	/** The account every block after the genesis block pays its reward to */
	static final Bytes MINER = Bytes.of(new byte[] { 5 });

	private TestChains()
	{
	}

	/**
	 * A genesis block allocating to the holder, and one mined block per transfer, each paying
	 * {@link #MINER}
	 *
	 * @param holder
	 *            the account the genesis block pays its block reward to
	 * @param transfers
	 *            the transfers, in chain order
	 * @return the blocks, genesis first
	 */
	static List<BlockBody> chainWith(final Bytes holder, final SignedTransaction... transfers)
	{
		BlockBody genesis = Blocks
			.mine(new BlockBody(Chain.IDENTIFIER, 0L, Bytes.of(new byte[32]), holder,
				new ArrayList<>(), 1_759_000_000_000L, 8, "in the beginning was the pun"),
				1_000_000L)
			.orElseThrow();
		List<BlockBody> chain = new ArrayList<>(List.of(genesis));
		for (SignedTransaction transfer : transfers)
		{
			BlockBody previous = chain.getLast();
			chain.add(Blocks
				.mine(new BlockBody(Chain.IDENTIFIER, previous.height() + 1L,
					Blocks.hashOf(previous), MINER, List.of(transfer),
					1_759_000_000_000L + 60_000L * chain.size(), 8, "block " + chain.size()),
					1_000_000L)
				.orElseThrow());
		}
		return chain;
	}
}
