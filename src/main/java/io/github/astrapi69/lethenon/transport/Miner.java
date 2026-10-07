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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.Mining;

/**
 * Mines on a node: the next block on the node's tip, carrying the node's waiting transfers, paid to
 * one account, handed to the node, which verifies, writes and relays it like any block
 * <p>
 * It mines in rounds of {@link #ATTEMPTS_PER_ROUND} attempts and looks at the tip between rounds,
 * so a block that arrives from a peer stops work on a tip that is no longer the tip.
 */
public final class Miner implements AutoCloseable
{

	/** How many puns one round tries before the tip is looked at again */
	static final long ATTEMPTS_PER_ROUND = 100_000L;

	private final AtomicBoolean running = new AtomicBoolean(true);

	private final AtomicLong mined = new AtomicLong();

	private Thread thread;

	private Miner()
	{
	}

	/**
	 * Starts mining on a thread of its own
	 *
	 * @param node
	 *            the node whose tip and pool it mines on
	 * @param beneficiary
	 *            the account the blocks pay
	 * @param pun
	 *            the words mining varies
	 * @return the running miner
	 */
	public static Miner start(final Node node, final Bytes beneficiary, final String pun)
	{
		Miner miner = new Miner();
		miner.thread = Thread.ofVirtual().name("lethenon-miner")
			.start(() -> miner.mine(node, beneficiary, pun));
		return miner;
	}

	/**
	 * How many blocks this miner found that the node adopted
	 *
	 * @return the count
	 */
	public long minedBlocks()
	{
		return mined.get();
	}

	private void mine(final Node node, final Bytes beneficiary, final String pun)
	{
		while (running.get())
		{
			BlockBody next = Mining.nextBlock(Chain.TEST_IDENTIFIER, node.chain(), beneficiary,
				node.pending(), pun, System.currentTimeMillis());
			Optional<BlockBody> found = Blocks.mine(next, ATTEMPTS_PER_ROUND);
			if (found.isPresent() && node.submitBlock(found.get()))
			{
				mined.incrementAndGet();
			}
		}
	}

	/**
	 * Stops after the round in progress
	 */
	@Override
	public void close()
	{
		running.set(false);
		try
		{
			thread.join(Duration.ofSeconds(30));
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}
}
