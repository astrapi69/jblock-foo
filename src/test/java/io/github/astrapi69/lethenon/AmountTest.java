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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Money that cannot go wrong quietly.
 * <p>
 * An amount is a count of lethe, the base unit: 10^8 lethe are one LETH. It is a long, never a
 * floating point number, and every operation on it is checked - an overflow throws where the plain
 * operator would wrap around and turn a balance into its opposite.
 */
class AmountTest
{

	@Test
	@DisplayName("one LETH is a hundred million lethe")
	void oneLeth_isTenToTheEightLethe()
	{
		assertEquals(100_000_000L, Amount.ofLeth(1).lethe(),
			"the decimals are fixed at eight and are part of what the genesis block signs");
	}

	@Test
	@DisplayName("adding beyond the range of a long throws instead of wrapping")
	void addition_beyondTheRange_throws()
	{
		Amount nearlyEverything = Amount.ofLethe(Long.MAX_VALUE - 1);

		assertThrows(ArithmeticException.class, () -> nearlyEverything.plus(Amount.ofLethe(2)),
			"a wrapped sum is a balance that became its opposite without anybody noticing");
	}

	@Test
	@DisplayName("subtracting more than there is throws")
	void subtraction_belowZero_throws()
	{
		Amount one = Amount.ofLethe(1);

		assertThrows(ArithmeticException.class, () -> one.minus(Amount.ofLethe(2)),
			"there is no negative balance: a transfer that cannot be paid is refused, not owed");
	}

	@Test
	@DisplayName("a negative amount cannot be built at all")
	void negative_isRefusedAtConstruction()
	{
		assertThrows(IllegalArgumentException.class, () -> Amount.ofLethe(-1));
	}

	@ParameterizedTest(name = "{0} lethe reads back as itself")
	@ValueSource(longs = { 0L, 1L, 99_999_999L, 100_000_000L, 198_400_000_000_000_000L })
	void text_roundTrips(final long lethe)
	{
		Amount amount = Amount.ofLethe(lethe);

		assertEquals(amount, Amount.parse(amount.toString()),
			"what is printed is what parses back; a format that loses a lethe loses money");
	}

	@Test
	@DisplayName("the text is fixed to eight decimals, so amounts sort and compare as text too")
	void text_hasEightDecimals()
	{
		assertEquals("1.00000000", Amount.ofLeth(1).toString());
		assertEquals("0.00000001", Amount.ofLethe(1).toString());
		assertEquals("1984000000.00000000", Amount.ofLeth(1_984_000_000L).toString());
	}

	@Test
	@DisplayName("the whole supply fits into a long with room to spare")
	void theSupply_fitsWithHeadroom()
	{
		long supply = Emission.TOTAL_SUPPLY.lethe();

		assertTrue(supply < Long.MAX_VALUE / 40,
			"every sum over all accounts stays far below an overflow - measured headroom is what "
				+ "made a 64 bit integer the right choice instead of BigInteger");
	}

	@org.junit.jupiter.params.ParameterizedTest(name = "\"{0}\" is {1} lethe")
	@org.junit.jupiter.params.provider.CsvSource({ "12.5, 1250000000", "1984, 198400000000",
			"0.00000001, 1", " 3 , 300000000" })
	void parseLeth_readsWhatAPersonTypes_exactly(final String typed, final long lethe)
	{
		org.junit.jupiter.api.Assertions.assertEquals(Amount.ofLethe(lethe), Amount.parseLeth(typed));
	}

	@org.junit.jupiter.params.ParameterizedTest(name = "\"{0}\" is refused")
	@org.junit.jupiter.params.provider.ValueSource(strings = { "0.000000001", "-1", "twelve", "" })
	void parseLeth_refusesWhatIsNoAmountOrWouldHaveToBeRounded(final String typed)
	{
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> Amount.parseLeth(typed));
	}
}
