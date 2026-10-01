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

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Sends an amount from this wallet's account
 */
@Command(name = "send", description = "Sign a transfer from this wallet's account; it waits for "
	+ "the next block. The password is the first line of standard input.")
class SendCommand extends TransferCommand
{

	@Option(names = "--amount", required = true,
		description = "the amount in LETH, up to 8 decimals, e.g. 12.5")
	String amount;

	@Option(names = "--memo", defaultValue = "", description = "the text signed with the transfer")
	String memo;

	/**
	 * Creates the command; picocli instantiates it reflectively
	 */
	SendCommand()
	{
	}

	@Override
	int run(final PrintStream out) throws IOException
	{
		return transfer(out, openWallet(), requireChain(), amountOf(amount), memo);
	}
}
