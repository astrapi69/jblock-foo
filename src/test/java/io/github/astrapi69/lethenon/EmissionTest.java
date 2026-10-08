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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The numbers the chain's money is built on, as executable facts rather than prose in an issue.
 * <p>
 * The genesis block puts the whole genesis supply into a pool; a block pays a millionth of the
 * pool, and never less than the tail reward, whose missing part is minted (#111, #133).
 */
class EmissionTest
{

	@Test
	@DisplayName("the genesis supply is 1,984,000,000 LETH, all of it in the pool")
	void theGenesisSupply_isTheDecidedNumber_andAllOfItIsThePool()
	{
		assertEquals(Amount.ofLeth(1_984_000_000L), Emission.GENESIS_SUPPLY);
		assertEquals(Emission.GENESIS_SUPPLY, Emission.MINING_POOL,
			"no share is allocated before the first block is mined (#111)");
	}

	@Test
	@DisplayName("the first reward is exactly 1,984 LETH, all of it out of the pool")
	void theFirstReward_isExactly1984Leth()
	{
		BlockReward first = Emission.rewardFor(Emission.MINING_POOL);

		assertEquals(Amount.ofLeth(1_984L), first.fromPool(),
			"a millionth of 1,984,000,000 LETH: the year stays on the first block");
		assertEquals(Amount.ZERO, first.minted());
		assertEquals(Amount.ofLeth(1_984L), Emission.FIRST_REWARD);
	}

	@Test
	@DisplayName("the tail reward is 66 LETH, the divisor a million")
	void theSchedule_isTheDecidedOne()
	{
		assertEquals(Amount.ofLeth(66L), Emission.TAIL_REWARD,
			"0.8748 % of the genesis supply a year, against Monero's 0.8702 % (#133)");
		assertEquals(1_000_000L, Emission.EMISSION_DIVISOR);
		assertEquals(new EmissionSchedule(1_000_000L, Amount.ofLeth(66L)), Emission.SCHEDULE);
	}

	@Test
	@DisplayName("without fees, the tail starts at block 3,403,214, after about 12.9 years")
	void theTail_startsWhereTheCalculationIn133SaysItDoes()
	{
		long pool = Emission.MINING_POOL.lethe();
		long height = 0L;
		// bounded, so a schedule without a tail fails here instead of never returning
		while (Emission.rewardFor(Amount.ofLethe(pool)).minted().equals(Amount.ZERO)
			&& height <= 4_000_000L)
		{
			pool -= Emission.rewardFor(Amount.ofLethe(pool)).fromPool().lethe();
			height++;
		}

		assertEquals(3_403_214L, height, "the first block that mints");
		assertEquals(12.94, height / (365.25 * 24 * 60 / 2), 0.005, "years of two-minute blocks");
	}

	static Stream<Arguments> pools()
	{
		return Stream.of(
			Arguments.of("the whole pool", Amount.ofLeth(1_984_000_000L), Amount.ofLeth(1_984L),
				Amount.ZERO),
			Arguments.of("the last pool that pays the tail without minting",
				Amount.ofLeth(66_000_000L), Amount.ofLeth(66L), Amount.ZERO),
			Arguments.of("one lethe less: the share falls short by one lethe, which is minted",
				Amount.ofLeth(66_000_000L).minus(Amount.ofLethe(1L)),
				Amount.ofLethe(6_599_999_999L), Amount.ofLethe(1L)),
			Arguments.of("just under the flat reward of 0.3.0, where 0.3.0 paid nothing",
				Amount.ofLeth(1_984L).minus(Amount.ofLethe(1L)), Amount.ofLethe(198_399L),
				Amount.ofLeth(66L).minus(Amount.ofLethe(198_399L))),
			Arguments.of("a share below one lethe: the tail is minted whole",
				Amount.ofLethe(999_999L), Amount.ZERO, Amount.ofLeth(66L)),
			Arguments.of("an empty pool", Amount.ZERO, Amount.ZERO, Amount.ofLeth(66L)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("pools")
	@DisplayName("a block pays the pool's millionth, and mints what is missing to the tail")
	void theReward_isTheSharePlusWhatIsMissingToTheTail(final String pool, final Amount held,
		final Amount fromPool, final Amount minted)
	{
		BlockReward reward = Emission.rewardFor(held);

		assertEquals(fromPool, reward.fromPool(), pool);
		assertEquals(minted, reward.minted(), pool);
		assertTrue(reward.total().compareTo(Emission.TAIL_REWARD) >= 0,
			"a block never pays less than the tail: " + reward.total());
	}

	static Stream<Arguments> feesOnPools()
	{
		return Stream.of(Arguments.of(Amount.ofLeth(1_983L), Amount.ofLeth(1L)),
			Arguments.of(Amount.ofLeth(1_984_000_000L), Amount.ofLethe(1L)),
			Arguments.of(Amount.ZERO, Amount.ofLeth(1_984L)),
			Arguments.of(Amount.ofLeth(66_000_000L), Amount.ofLeth(500_000L)),
			Arguments.of(Amount.ofLeth(1_000L), Amount.ofLeth(1_000_000L)));
	}

	@ParameterizedTest(name = "pool {0}, fee {1}")
	@MethodSource("feesOnPools")
	@DisplayName("a fee in the pool raises the next reward by its millionth, never by a lump")
	void aFee_isPaidOutAShareAtATime_neverAsALottery(final Amount pool, final Amount fee)
	{
		BlockReward without = Emission.rewardFor(pool);
		BlockReward with = Emission.rewardFor(pool.plus(fee));

		long raised = with.fromPool().lethe() - without.fromPool().lethe();
		assertTrue(raised <= fee.lethe() / Emission.EMISSION_DIVISOR + 1L,
			"the pool's share grows by at most the fee's millionth: " + raised);
		assertTrue(with.total().compareTo(without.total()) >= 0, "a fee never lowers the reward");
		assertTrue(with.minted().compareTo(without.minted()) <= 0,
			"in the tail a fee lowers what is minted instead");
	}

	@Test
	@DisplayName("a schedule with a divisor below 1 does not exist")
	void aDivisorBelowOne_isRefused()
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> new EmissionSchedule(0L, Amount.ZERO));

		assertTrue(refused.getMessage().contains("divided by 0"), refused.getMessage());
	}
}
