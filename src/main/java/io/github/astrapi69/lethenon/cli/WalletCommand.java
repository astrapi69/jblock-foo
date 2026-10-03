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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.github.astrapi69.mystic.crypt.secret.SecretBuffers;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.Wallet;
import io.github.astrapi69.lethenon.WalletFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Makes a wallet file: a new wallet, or one brought back from its 24 words
 */
@Command(name = "wallet", description = "Create a wallet, or restore one from its 24 words.",
	subcommands = { WalletCommand.Create.class, WalletCommand.Restore.class })
class WalletCommand
{

	/**
	 * Creates the command; picocli instantiates it reflectively
	 */
	WalletCommand()
	{
	}

	/**
	 * A new wallet, written to a new file, its 24 words printed once
	 */
	@Command(name = "create", description = "Create a new wallet file. The password is the first "
		+ "line of standard input. Prints the account and the 24 words to write down.")
	static class Create implements Callable<Integer>
	{

		@Option(names = "--wallet", required = true, description = "the wallet file to create")
		Path wallet;

		/**
		 * Creates the command; picocli instantiates it reflectively
		 */
		Create()
		{
		}

		@Override
		public Integer call()
		{
			try
			{
				BufferedReader input = ChainCommand.standardInput();
				char[] password = ChainCommand
					.nextLineOfStandardInput(input, "the new wallet's password").toCharArray();
				Wallet created = Wallet.create();
				write(created, password);
				System.out.println("write these 24 words down - with them, or with enough "
					+ "shares, the wallet comes back without this file:");
				System.out.println(created.phrase());
				return 0;
			}
			catch (IllegalArgumentException | IOException refused)
			{
				System.err.println(refused.getMessage());
				return 1;
			}
		}

		private void write(final Wallet created, final char[] password) throws IOException
		{
			try
			{
				WalletFile.write(wallet, created, password);
			}
			finally
			{
				SecretBuffers.wipe(password);
			}
			System.out.println("wallet written to " + wallet);
			System.out.println(
				"account (ed25519): " + ChainCommand.hex(created.spendKey(SignatureSuite.ED25519)));
			// what a payer needs in order to pay unlinkably, in the form --to-address takes (#37)
			System.out.println("address (publish this): " + created.address().toText());
		}
	}

	/**
	 * A wallet brought back from its 24 words, written to a new file
	 */
	@Command(name = "restore", description = "Restore a wallet from its 24 words into a new file. "
		+ "Standard input: the words on the first line, the new password on the second.")
	static class Restore implements Callable<Integer>
	{

		@Option(names = "--wallet", required = true, description = "the wallet file to create")
		Path wallet;

		/**
		 * Creates the command; picocli instantiates it reflectively
		 */
		Restore()
		{
		}

		@Override
		public Integer call()
		{
			try
			{
				BufferedReader input = ChainCommand.standardInput();
				Wallet restored = Wallet
					.fromPhrase(ChainCommand.nextLineOfStandardInput(input, "the 24 words"));
				char[] password = ChainCommand
					.nextLineOfStandardInput(input, "the new wallet's password").toCharArray();
				try
				{
					WalletFile.write(wallet, restored, password);
				}
				finally
				{
					SecretBuffers.wipe(password);
				}
				System.out.println("wallet restored to " + wallet);
				System.out.println("account (ed25519): "
					+ ChainCommand.hex(restored.spendKey(SignatureSuite.ED25519)));
				System.out.println("address (publish this): " + restored.address().toText());
				return 0;
			}
			catch (IllegalArgumentException | IOException refused)
			{
				System.err.println(refused.getMessage());
				return 1;
			}
		}
	}
}
