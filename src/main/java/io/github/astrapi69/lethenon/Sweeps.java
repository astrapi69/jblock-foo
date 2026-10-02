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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves what was paid to a wallet's one-time destinations onto the wallet's own account.
 * <p>
 * One transfer per destination, because each destination IS an account: its own balance, its own
 * nonce, and its own one-time key. A single transfer cannot spend two of them.
 * <p>
 * Sweeping is what makes a stealth payment usable, and it is also the moment the payment stops
 * being unlinkable: the transfer names the destination as its sender and the account as its
 * recipient, so whoever reads the chain sees that these two belong together. That is a decision for
 * the holder, not for this class - it sweeps what it is asked to sweep, and the command that calls
 * it says so out loud (#37).
 */
public final class Sweeps
{

	private Sweeps()
	{
	}

	/**
	 * Prepares one transfer per one-time destination that holds more than the fee
	 *
	 * @param wallet
	 *            the wallet whose payments are swept; its view key recognises them and its spend
	 *            key signs for them
	 * @param chain
	 *            the blocks, genesis first - replayed here, so a chain that does not verify yields
	 *            no sweep at all rather than a balance taken on trust
	 * @param waiting
	 *            the transfers already signed and not yet in a block; a destination one of them
	 *            empties is left out, because a second transfer from it would reuse its nonce
	 * @param fee
	 *            the fee of each transfer, taken out of what that destination holds
	 * @param memo
	 *            the memo signed with each transfer
     * @return the transfers, in the order the chain carries their destinations, empty when there
	 *         is nothing to sweep
	 * @throws ChainRejected
	 *             when the chain does not verify
	 */
	public static List<SignedTransaction> prepare(final Wallet wallet,
		final List<BlockBody> chain, final List<SignedTransaction> waiting, final Amount fee,
		final String memo)
	{
		WalletScan scan = WalletScan.over(chain, wallet.address(),
			wallet.viewKeyPair().getPrivate());
		ChainState state = Replay.verify(chain).finalState();
		Bytes account = wallet.spendKey(SignatureSuite.ED25519);
		List<SignedTransaction> sweep = new ArrayList<>();
		for (Destination destination : destinationsOf(scan))
		{
			if (isAlreadyOnItsWay(destination, waiting))
			{
				continue;
			}
			Amount held = state.balanceOf(destination.key());
			if (held.compareTo(fee) <= 0)
			{
				// moving a destination for exactly its fee gains nothing and publishes the link
				// between it and the account for free
				continue;
			}
			sweep.add(TransactionSigner.sign(
				new TransactionBody(Chain.IDENTIFIER, state.nextNonceOf(destination.key()),
					destination.key(), Destination.direct(account), held.minus(fee), fee, memo),
				wallet.oneTimeKey(destination)));
		}
		return List.copyOf(sweep);
	}

	/**
	 * The destinations the scan recognised, each once: two payments can share a destination when a
	 * sender reuses an ephemeral key, and the state holds their sum already
	 */
	private static Set<Destination> destinationsOf(final WalletScan scan)
	{
		Set<Destination> destinations = new LinkedHashSet<>();
		for (WalletScan.Received received : scan.received())
		{
			destinations.add(received.destination());
		}
		return destinations;
	}

	private static boolean isAlreadyOnItsWay(final Destination destination,
		final List<SignedTransaction> waiting)
	{
		return waiting.stream()
			.anyMatch(transfer -> transfer.body().sender().equals(destination.key()));
	}
}
