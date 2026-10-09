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
import java.util.concurrent.Callable;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainRejected;
import io.github.astrapi69.lethenon.ConsensusRules;
import io.github.astrapi69.lethenon.Genesis;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Mines a candidate genesis block and prints what would be filed as its anchor in the code: the
 * chain, the block's hash and its canonical bytes. It writes nothing (#104).
 * <p>
 * The block's words are a headline of the day it is mined, chosen by the maintainer that day
 * (#148, ADR 0005), so the block cannot have been mined before it. It pays its reward to the burn
 * account, like every genesis block, so no wallet is needed.
 */
@Command(name = "genesis", description = "Mine a candidate genesis block from a headline of the "
	+ "day and print its hash and canonical bytes for a genesis anchor in the code. Its reward goes "
	+ "to the burn account. Writes nothing.")
class GenesisCommand implements Callable<Integer>
{

	@Option(names = "--testnet", description = "a genesis block of " + Chain.TEST_IDENTIFIER)
	boolean testnet;

	@Option(names = "--headline", required = true,
		description = "a headline of the day the block is mined, the words mining varies")
	String headline;

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
		if (headline.isBlank())
		{
			throw new IllegalArgumentException("the headline is empty: a genesis block carries a "
				+ "headline of the day it is mined");
		}
		BlockBody genesis = Genesis.candidate(chain, headline.strip(), System.currentTimeMillis());
		out.println("chain " + chain);
		out.println("hash " + Blocks.hashOf(genesis));
		out.println("words \"" + genesis.pun() + "\"");
		out.println("reward to the burn account " + genesis.beneficiary());
		out.println("anchor " + Genesis.anchorOf(genesis).canonicalHex());
		return 0;
	}
}
