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
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainRejected;
import io.github.astrapi69.lethenon.ConsensusRules;
import io.github.astrapi69.lethenon.Genesis;
import io.github.astrapi69.lethenon.SignatureSuite;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Mines a candidate genesis block and prints what would be filed as its anchor in the code: the
 * chain, the block's hash and its canonical bytes. It writes nothing (#104).
 */
@Command(name = "genesis", description = "Mine a candidate genesis block, allocating to this "
	+ "wallet, and print its hash and canonical bytes for a genesis anchor in the code. Writes "
	+ "nothing. The password is the first line of standard input.")
class GenesisCommand implements Callable<Integer>
{

	@Option(names = "--wallet", required = true, description = "the wallet the genesis allocates to")
	Path wallet;

	@Option(names = "--testnet", description = "a genesis block of lethenon-test-1")
	boolean testnet;

	@Option(names = "--pun", defaultValue = "in the beginning was the pun",
		description = "the words mining varies; default: ${DEFAULT-VALUE}")
	String pun;

	/**
	 * Creates the command; picocli instantiates it reflectively
	 */
	GenesisCommand()
	{
	}

	@Override
	public Integer call()
	{
		try
		{
			return run(System.out);
		}
		catch (ChainRejected | IllegalArgumentException | IllegalStateException
			| SecurityException | IOException refused)
		{
			System.err.println(refused.getMessage());
			return 1;
		}
	}

	private int run(final PrintStream out) throws IOException
	{
		String chain = testnet ? Chain.TEST_IDENTIFIER : Chain.IDENTIFIER;
		if (ConsensusRules.LETHENON.anchorFor(chain).isPresent())
		{
			throw new IllegalStateException("chain '" + chain
				+ "' has a genesis block fixed in the code already");
		}
		BlockBody genesis = Genesis.start(chain, ChainCommand
			.openWallet(wallet, ChainCommand.firstLineOfStandardInput())
			.spendKey(SignatureSuite.ED25519), pun, System.currentTimeMillis());
		out.println("chain " + chain);
		out.println("hash " + Blocks.hashOf(genesis));
		out.println("anchor " + Genesis.anchorOf(genesis).canonicalHex());
		return 0;
	}
}
