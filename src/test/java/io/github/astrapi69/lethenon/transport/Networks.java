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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.Mining;
import io.github.astrapi69.lethenon.SignedTransaction;

/**
 * Chains and waiting for the test networks: nodes run on their own threads, so a test waits for a
 * condition with a deadline instead of sleeping
 */
final class Networks
{

	static final long GENESIS_TIME = 1_759_500_000_000L;

	private Networks()
	{
	}

	/**
	 * A test chain of one genesis block, paying the given holder
	 */
	static List<BlockBody> testGenesis(final Bytes holder)
	{
		return genesis(Chain.TEST_IDENTIFIER, holder);
	}

	static List<BlockBody> genesis(final String chainIdentifier, final Bytes holder)
	{
		return List.of(Blocks.mine(Mining.nextBlock(chainIdentifier, List.of(), holder, List.of(),
			"in the beginning", GENESIS_TIME), 1_000_000L).orElseThrow());
	}

	/**
	 * The chain extended by one mined block carrying the given transfers
	 */
	static List<BlockBody> extended(final List<BlockBody> chain, final Bytes miner,
		final List<SignedTransaction> transfers)
	{
		List<BlockBody> extended = new ArrayList<>(chain);
		extended.add(Blocks.mine(Mining.nextBlock(chain, miner, transfers, "block " + chain.size(),
			GENESIS_TIME + 60_000L * chain.size()), 1_000_000L).orElseThrow());
		return List.copyOf(extended);
	}

	/**
	 * Waits until the condition holds, at most ten seconds
	 *
	 * @throws AssertionError
	 *             naming what was waited for, when the deadline passes
	 */
	static void await(final String what, final BooleanSupplier condition)
	{
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!condition.getAsBoolean())
		{
			if (System.nanoTime() > deadline)
			{
				throw new AssertionError("waited ten seconds for " + what);
			}
			Thread.onSpinWait();
			try
			{
				Thread.sleep(10);
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted while waiting for " + what);
			}
		}
	}
}
