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
package io.github.astrapi69.lethenon.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.astrapi69.lethenon.Amount;

/**
 * An amount as a person types it on the command line, turned into lethe exactly - never rounded
 */
class AmountOnTheCommandLineTest
{

	@ParameterizedTest(name = "''{0}'' is {1} lethe")
	@CsvSource({ "12.5, 1250000000", "1984, 198400000000", "0.00000001, 1", "0, 0",
			"' 7 ', 700000000", "1.10000000, 110000000" })
	void anAmountIsReadExactly(final String typed, final long lethe)
	{
		assertEquals(Amount.ofLethe(lethe), TransferCommand.amountOf(typed));
	}

	@ParameterizedTest(name = "''{0}'' is refused")
	@ValueSource(strings = { "0.000000001", "-1", "twelve", "", "1e3x" })
	void whatIsNoAmountOrWouldHaveToBeRounded_isRefused(final String typed)
	{
		assertThrows(IllegalArgumentException.class, () -> TransferCommand.amountOf(typed));
	}
}
