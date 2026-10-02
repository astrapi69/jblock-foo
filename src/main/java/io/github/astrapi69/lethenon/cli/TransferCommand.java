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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.Transfers;
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
		List<SignedTransaction> waiting = new ArrayList<>(readPending());
		SignedTransaction signed = Transfers.prepare(sender, SignatureSuite.withIdentifier(suite),
			blocks, waiting, Destination.direct(Bytes.ofHex(recipient)), amount, amountOf(fee),
			memo);
		long nonce = signed.body().nonce();
		waiting.add(signed);
		writePending(waiting);
		out.println("signed a transfer of " + amount + " LETH to " + recipient + " with nonce "
			+ nonce + "; it waits for the next block (" + waiting.size() + " waiting)");
		return 0;
	}

	/**
	 * An amount as a person types it - "12.5", "1984", "0.00000001" - turned into lethe exactly;
	 * {@link Amount#parseLeth(String)} since lethenon#32, where the desktop plugin reads it too
	 *
	 * @param text
	 *            the amount in LETH
	 * @return the amount
	 * @throws IllegalArgumentException
	 *             for more than eight decimals, a negative amount, or no number at all
	 */
	static Amount amountOf(final String text)
	{
		return Amount.parseLeth(text);
	}
}
