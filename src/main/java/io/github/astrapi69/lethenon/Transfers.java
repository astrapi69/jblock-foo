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
 * Prepares a transfer from a wallet's account (lethenon#32).
 * <p>
 * A transfer is prepared against the replayed chain AND against the transfers already waiting for
 * the next block: they come before it, so its nonce follows theirs, and what they spend is not
 * available to it. A transfer the next block could not apply is refused here, with the numbers,
 * rather than signed and left to fail at mining.
 */
public final class Transfers
{

	private Transfers()
	{
	}

	/**
	 * Signs a transfer from the wallet's account of the given suite
	 *
	 * @param sender
	 *            the opened wallet
	 * @param suite
	 *            the signature suite of the sending account
	 * @param chain
	 *            the chain, genesis first; it is replayed here
	 * @param waiting
	 *            the transfers already waiting for the next block, in signing order
	 * @param recipient
	 *            where the transfer goes
	 * @param amount
	 *            what it carries
	 * @param fee
	 *            what it pays to the pool
	 * @param memo
	 *            the signed memo
	 * @return the signed transfer, to be appended to the waiting ones
	 * @throws ChainRejected
	 *             when the chain does not verify
	 * @throws IllegalArgumentException
	 *             when the account does not hold the amount plus the fee after the waiting transfers
	 */
	public static SignedTransaction prepare(final Wallet sender, final SignatureSuite suite,
		final List<BlockBody> chain, final List<SignedTransaction> waiting,
		final Destination recipient, final Amount amount, final Amount fee, final String memo)
	{
		Bytes account = sender.spendKey(suite);
		ChainState state = Replay.verify(chain).finalState();
		Amount due = amount.plus(fee);
		Amount available = availableTo(account, state, waiting);
		if (available.compareTo(due) < 0)
		{
			throw new IllegalArgumentException("account " + account + " holds "
				+ state.balanceOf(account) + " LETH, " + available
				+ " after the transfers already waiting, and this transfer needs " + due);
		}
		long nonce = state.nextNonceOf(account) + countFrom(account, waiting);
		return sender.sign(new TransactionBody(Chain.IDENTIFIER, nonce, account, recipient, amount,
			fee, memo), suite);
	}

	private static Amount availableTo(final Bytes account, final ChainState state,
		final List<SignedTransaction> waiting)
	{
		Amount available = state.balanceOf(account);
		for (SignedTransaction transfer : waiting)
		{
			if (transfer.body().sender().equals(account))
			{
				available = available.minus(transfer.body().amount().plus(transfer.body().fee()));
			}
		}
		return available;
	}

	private static long countFrom(final Bytes account, final List<SignedTransaction> waiting)
	{
		return waiting.stream().filter(transfer -> transfer.body().sender().equals(account))
			.count();
	}
}
