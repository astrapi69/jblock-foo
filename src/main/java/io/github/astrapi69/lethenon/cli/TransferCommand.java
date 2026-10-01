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

import java.io.IOException;
import java.math.BigDecimal;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainState;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.Replay;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionBody;
import io.github.astrapi69.lethenon.Wallet;
import picocli.CommandLine.Option;

/**
 * Signs a transfer and leaves it waiting for the next block - what {@code send} and
 * {@code faucet} share.
 * <p>
 * The nonce and the balance are taken from the replayed chain and the transfers already waiting
 * from the same account, so a second {@code send} before the next block gets the next nonce and
 * cannot spend the same money twice. A transfer the account cannot cover is refused before
 * anything is signed.
 */
abstract class TransferCommand extends ChainCommand
{

	@Option(names = "--to", required = true,
		description = "the recipient's account key, in hex, as every command prints it")
	String recipient;

	@Option(names = "--fee", defaultValue = "0",
		description = "the fee in LETH, up to 8 decimals; default: 0")
	String fee;

	@Option(names = "--suite", defaultValue = "ed25519",
		description = "the signature suite of the sending account: ed25519 or ml-dsa-65")
	String suite;

	/**
	 * Creates the command; picocli instantiates it reflectively
	 */
	TransferCommand()
	{
	}

	/**
	 * Signs a transfer from the wallet's account and appends it to the waiting transfers
	 *
	 * @param out
	 *            where to report
	 * @param sender
	 *            the opened wallet
	 * @param blocks
	 *            the chain, already read
	 * @param amount
	 *            what is transferred
	 * @param memo
	 *            the signed memo
	 * @return the exit code
	 * @throws IOException
	 *             if the waiting transfers cannot be read or written
	 */
	int transfer(final PrintStream out, final Wallet sender, final List<BlockBody> blocks,
		final Amount amount, final String memo) throws IOException
	{
		SignatureSuite signatureSuite = SignatureSuite.withIdentifier(suite);
		Bytes account = sender.spendKey(signatureSuite);
		ChainState state = Replay.verify(blocks).finalState();
		List<SignedTransaction> waiting = new ArrayList<>(readPending());
		Amount transferFee = amountOf(fee);
		Amount due = amount.plus(transferFee);
		Amount available = availableTo(account, state, waiting);
		if (available.compareTo(due) < 0)
		{
			throw new IllegalArgumentException("account " + hex(account) + " holds "
				+ state.balanceOf(account) + " LETH, " + available
				+ " after the transfers already waiting, and this transfer needs " + due);
		}
		long nonce = state.nextNonceOf(account) + countFrom(account, waiting);
		SignedTransaction signed = sender.sign(new TransactionBody(Chain.IDENTIFIER, nonce, account,
			Destination.direct(Bytes.ofHex(recipient)), amount, transferFee, memo),
			signatureSuite);
		waiting.add(signed);
		writePending(waiting);
		out.println("signed a transfer of " + amount + " LETH to " + recipient + " with nonce "
			+ nonce + "; it waits for the next block (" + waiting.size() + " waiting)");
		return 0;
	}

	/**
	 * An amount as a person types it - "12.5", "1984", "0.00000001" - turned into lethe exactly.
	 * {@link Amount#parse(String)} stays the strict form with all eight decimals; this is the
	 * forgiving one for the command line, and it still never rounds
	 *
	 * @param text
	 *            the amount in LETH
	 * @return the amount
	 * @throws IllegalArgumentException
	 *             for more than eight decimals, a negative amount, or no number at all
	 */
	static Amount amountOf(final String text)
	{
		try
		{
			long lethe = new BigDecimal(text.strip()).movePointRight(Amount.DECIMALS)
				.longValueExact();
			return Amount.ofLethe(lethe);
		}
		catch (ArithmeticException | NumberFormatException unreadable)
		{
			throw new IllegalArgumentException("'" + text + "' is not an amount of LETH with at "
				+ "most " + Amount.DECIMALS + " decimals", unreadable);
		}
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
