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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The difficulty rule of lethenon#24, as a function of the blocks before: the minimum at genesis,
 * the previous block's difficulty between retargets, and every 30 blocks a step of at most two
 * bits towards two-minute blocks. Plus Bitcoin's median-time-past for timestamps.
 * <p>
 * None of these blocks is mined: the rule reads only heights, timestamps and difficulties, so it
 * is tested without paying for proof of work.
 */
class DifficultyRuleTest
{

	private static final long START = 1_759_000_000_000L;

	@Test
	void theGenesisBlock_isMinedAtTheMinimum()
	{
		assertEquals(8, DifficultyRule.MINIMUM);
		assertEquals(DifficultyRule.MINIMUM, DifficultyRule.requiredFor(List.of()));
	}

	@Test
	void betweenRetargets_theDifficultyIsThePreviousBlocks()
	{
		assertEquals(11, DifficultyRule.requiredFor(blocks(5, 11, 1_000L)));
		assertEquals(11, DifficultyRule.requiredFor(blocks(29, 11, 1_000L)));
		assertEquals(11, DifficultyRule.requiredFor(blocks(31, 11, 1_000L)));
	}

	/**
	 * The hypothesis put to #151: the difficulty moves only every 30 blocks, so a difficulty that
	 * is too high for the miners there are stays until 30 more blocks have come, and falls by at
	 * most two bits then. Set artificially to 40 bits, a day between blocks changes nothing until
	 * the retarget. The property holds; it was not what stopped the miner in #151, whose next block
	 * needed the 26 bits the 30 blocks before had come at 171.6 s apart (MiningRateTest is that
	 * cause).
	 */
	@Test
	void aDifficultySetTooHigh_staysUntilThirtyMoreBlocks_andFallsByTwoBitsAtMost()
	{
		long day = 24L * 60L * 60L * 1_000L;
		List<BlockBody> tooHigh = blocks(30, 40, 1_000L);
		List<BlockBody> slow = new java.util.ArrayList<>(tooHigh);
		while (slow.size() < 60)
		{
			slow.add(block(slow.size(), slow.getLast().timestamp() + day, 40));
			if (slow.size() < 60)
			{
				assertEquals(40, DifficultyRule.requiredFor(slow),
					"a day per block, and block " + slow.size() + " still needs 40 bits");
			}
		}

		assertEquals(38, DifficultyRule.requiredFor(slow),
			"after 30 blocks a day apart: two bits less, the most one retarget moves");
	}

	@ParameterizedTest(name = "30 blocks at difficulty {0}, {1} ms apart: block 30 needs {2}")
	@CsvSource({
			// on target, and just inside the band where whole bits cannot move
			"12, 120000, 12", "12, 61000, 12", "12, 239000, 12",
			// twice and four times as fast: one and two bits more, and never more than two
			"12, 60000, 13", "12, 30000, 14", "12, 1000, 14",
			// twice and four times as slow: one and two bits less, and never more than two
			"12, 240000, 11", "12, 480000, 10", "12, 5000000, 10",
			// the floor and the ceiling hold
			"8, 480000, 8", "9, 480000, 8", "256, 1000, 256", "255, 1000, 256" })
	void everyThirtyBlocks_theDifficultySteersTowardsTwoMinutes(final int difficulty,
		final long spacing, final int required)
	{
		assertEquals(required, DifficultyRule.requiredFor(blocks(30, difficulty, spacing)));
	}

	@Test
	void timestampsThatGoBackwards_countAsTheFastestInterval()
	{
		List<BlockBody> onTarget = blocks(30, 12, 120_000L);
		List<BlockBody> backwards = new ArrayList<>(onTarget);
		backwards.set(29, block(29, START - 5_000L, 12));

		assertEquals(12, DifficultyRule.requiredFor(onTarget));
		assertEquals(14, DifficultyRule.requiredFor(backwards));
	}

	@Test
	void aTimestamp_hasToBeLaterThanTheMedianOfTheElevenBefore()
	{
		// eleven blocks one second apart: the median is the sixth, START + 5000
		List<BlockBody> previous = blocks(11, 8, 1_000L);

		assertEquals(START + 5_000L, DifficultyRule.medianTimePast(previous));
		assertFalse(DifficultyRule.timestampAllowed(previous, START + 5_000L));
		assertTrue(DifficultyRule.timestampAllowed(previous, START + 5_001L));
	}

	@Test
	void theMedian_isTakenOverTheLastElevenOnly_andOverFewerAtTheStart()
	{
		assertEquals(START + 15_000L, DifficultyRule.medianTimePast(blocks(21, 8, 1_000L)));
		assertEquals(START + 1_000L, DifficultyRule.medianTimePast(blocks(3, 8, 1_000L)));
		assertTrue(DifficultyRule.timestampAllowed(List.of(), 0L), "the genesis block has none");
	}

	private static List<BlockBody> blocks(final int count, final int difficulty,
		final long spacing)
	{
		List<BlockBody> blocks = new ArrayList<>();
		for (int height = 0; height < count; height++)
		{
			blocks.add(block(height, START + spacing * height, difficulty));
		}
		return blocks;
	}

	private static BlockBody block(final long height, final long timestamp, final int difficulty)
	{
		return new BlockBody(Chain.IDENTIFIER, height, Bytes.of(new byte[32]),
			Bytes.of(new byte[] { 5 }), List.of(), timestamp, difficulty, "unmined");
	}
}
