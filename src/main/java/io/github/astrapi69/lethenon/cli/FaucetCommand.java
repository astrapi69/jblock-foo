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
import java.util.List;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.Wallet;
import picocli.CommandLine.Command;

/**
 * Hands out a fixed amount from the genesis holder's account (lethenon#2, decision 7).
 * <p>
 * Not a mint and no rule of the chain: the chain sees an ordinary signed transfer from the account
 * the genesis block allocated to. Only that wallet can run it.
 */
@Command(name = "faucet", description = "Send " + FaucetCommand.AMOUNT_TEXT + " LETH from the "
	+ "genesis holder's wallet to an account. The password is the first line of standard input.")
class FaucetCommand extends TransferCommand
{

	/** What the faucet hands out: one block reward's worth */
	static final String AMOUNT_TEXT = "1984";

	/**
	 * Creates the command; picocli instantiates it reflectively
	 */
	FaucetCommand()
	{
	}

	@Override
	int run(final PrintStream out) throws IOException
	{
		Wallet holder = openWallet();
		List<BlockBody> blocks = requireChain();
		Bytes genesisHolder = blocks.getFirst().beneficiary();
		if (!genesisHolder.equals(holder.spendKey(SignatureSuite.ED25519)))
		{
			throw new IllegalArgumentException("the faucet pays from the genesis holder's account "
				+ hex(genesisHolder) + ", and this wallet is not that account");
		}
		return transfer(out, holder, blocks, amountOf(AMOUNT_TEXT), "from the faucet");
	}
}
