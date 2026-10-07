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

/**
 * How a transfer carries its amount.
 * <p>
 * Version 1 of the transaction encoding carries the amount and the fee as numbers in the clear,
 * and names no amount scheme on the wire: {@link #PLAIN} is implied by that version. A scheme that
 * hides the amount needs a transaction version that names it, which is a format decision of its
 * own (phase C).
 */
public enum AmountScheme implements Scheme
{

	/** The amount and the fee as numbers anybody can read */
	PLAIN("plain");

	private final String identifier;

	AmountScheme(final String identifier)
	{
		this.identifier = identifier;
	}

	@Override
	public String identifier()
	{
		return identifier;
	}

	@Override
	public BuildingBlock block()
	{
		return BuildingBlock.AMOUNT;
	}
}
