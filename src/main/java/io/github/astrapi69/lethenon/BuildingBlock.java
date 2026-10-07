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
 * The parts of a transfer that a privacy scheme can replace, each independently of the others
 * (privacy block, phase B).
 * <p>
 * Today each is filled by schemes that hide nothing but the recipient: the sender authorizes with
 * a signature under its public account key, the recipient is an account key or a one-time
 * destination, and the amount is a number in the clear. A scheme that hides the sender replaces
 * the authorization, one that hides the amount replaces the amount, and the consensus rule
 * ({@link ConsensusRules}) decides per chain and height which scheme of each block a transfer may
 * use.
 */
public enum BuildingBlock
{

	/** Who may spend, and the proof of it: today a signature suite */
	AUTHORIZATION("authorization"),

	/** Where the funds go: today an address scheme */
	RECIPIENT("recipient"),

	/** How much moves: today a number in the clear */
	AMOUNT("amount");

	private final String label;

	BuildingBlock(final String label)
	{
		this.label = label;
	}

	/**
	 * The name of the block in a message a reader can act on
	 *
	 * @return the label
	 */
	public String label()
	{
		return label;
	}
}
