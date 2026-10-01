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
 * How hard a block has to be to mine, and how late its timestamp has to be - as decided in
 * lethenon#24, after Bitcoin's rules, with the difficulty counted in whole bits.
 * <p>
 * Every rule here is a function of the blocks before, so a replay reaches the same answer from the
 * same bytes on any machine at any time. Nothing reads a clock:
 * <ul>
 * <li>the genesis block is mined at the {@link #MINIMUM}, the way Bitcoin's genesis was mined at
 * its proof-of-work limit, and the retarget raises it from there</li>
 * <li>between retargets a block has the difficulty of the block before</li>
 * <li>every {@link #INTERVAL} blocks the difficulty takes a step towards
 * {@link #TARGET_BLOCK_MILLIS}: one bit more when the last interval ran at least twice as fast as
 * the target, two at four times, and the same downwards - never more than {@link #MAXIMUM_STEP}
 * bits, Bitcoin's clamp to a factor of four, and never below the minimum or above the length of
 * the hash</li>
 * <li>a timestamp has to be later than the median of the {@link #MEDIAN_SPAN} timestamps before
 * it, Bitcoin's median-time-past, so back-dated blocks cannot drag the retarget</li>
 * </ul>
 * With whole bits the step cannot be finer: anything between half and double the target time
 * leaves the difficulty where it is.
 */
public final class DifficultyRule
{

	/** The lowest difficulty there is, and the genesis block's */
	public static final int MINIMUM = 8;

	/** The highest: every bit of the SHA-256 block hash zero */
	public static final int MAXIMUM = 256;

	/** The block time the retarget steers towards, two minutes (#2) */
	public static final long TARGET_BLOCK_MILLIS = 120_000L;

	/** How many blocks lie between two retargets, one hour at the target */
	public static final int INTERVAL = 30;

	/** The most bits one retarget may move, a factor of four */
	public static final int MAXIMUM_STEP = 2;

	/** How many previous timestamps the median is taken over */
	public static final int MEDIAN_SPAN = 11;

	private DifficultyRule()
	{
	}

	/**
	 * The difficulty the next block has to declare
	 *
	 * @param previous
	 *            the blocks before it, genesis first; empty for the genesis block itself
	 * @return the difficulty in leading zero bits
	 */
	public static int requiredFor(final List<BlockBody> previous)
	{
		if (previous.isEmpty())
		{
			return MINIMUM;
		}
		int last = previous.getLast().difficulty();
		int height = previous.size();
		if (height % INTERVAL != 0)
		{
			return last;
		}
		long first = previous.get(height - INTERVAL).timestamp();
		long actual = previous.getLast().timestamp() - first;
		long expected = (INTERVAL - 1) * TARGET_BLOCK_MILLIS;
		return Math.clamp(last + stepFor(actual, expected), MINIMUM, MAXIMUM);
	}

	/**
	 * The median of the last {@link #MEDIAN_SPAN} timestamps, or of all of them while there are
	 * fewer
	 *
	 * @param previous
	 *            the blocks before, at least one
	 * @return the median timestamp
	 */
	public static long medianTimePast(final List<BlockBody> previous)
	{
		List<BlockBody> window = previous.subList(Math.max(0, previous.size() - MEDIAN_SPAN),
			previous.size());
		long[] timestamps = window.stream().mapToLong(BlockBody::timestamp).sorted().toArray();
		return timestamps[timestamps.length / 2];
	}

	/**
	 * Whether a block may carry a timestamp
	 *
	 * @param previous
	 *            the blocks before it
	 * @param timestamp
	 *            its timestamp
	 * @return true for the genesis block, and when the timestamp is later than the median of the
	 *         ones before
	 */
	public static boolean timestampAllowed(final List<BlockBody> previous, final long timestamp)
	{
		return previous.isEmpty() || timestamp > medianTimePast(previous);
	}

	/**
	 * How many bits to move: positive when the interval ran faster than expected. An interval
	 * that took no time or went backwards counts as the fastest there is
	 */
	private static int stepFor(final long actual, final long expected)
	{
		if (actual <= 0)
		{
			return MAXIMUM_STEP;
		}
		int faster = 0;
		while (faster < MAXIMUM_STEP && actual * (1L << (faster + 1)) <= expected)
		{
			faster++;
		}
		if (faster > 0)
		{
			return faster;
		}
		int slower = 0;
		while (slower < MAXIMUM_STEP && actual >= expected * (1L << (slower + 1)))
		{
			slower++;
		}
		return -slower;
	}
}
