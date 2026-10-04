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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.astrapi69.lethenon.Wallet;

/**
 * The commands own the password arrays they read from standard input, and overwrite them once the
 * wallet file has been written or opened. WalletFile reads a password and leaves it alone - since
 * #45 for both layouts - so the commands' wipes are the only ones there are, and they are asserted
 * on the array's content after the call, not on a reference being dropped.
 */
class PasswordsAreWipedTest
{

	@TempDir
	Path directory;

	@Test
	void creatingAWallet_overwritesThePassword() throws Exception
	{
		WalletCommand.Create create = new WalletCommand.Create();
		create.wallet = directory.resolve("created.lethenon");
		char[] password = "a new wallet's password".toCharArray();

		create.write(Wallet.create(), password);

		assertArrayEquals(new char[password.length], password,
			"the password must be zero-filled once the wallet is written");
	}

	@Test
	void restoringAWallet_overwritesThePassword() throws Exception
	{
		WalletCommand.Restore restore = new WalletCommand.Restore();
		restore.wallet = directory.resolve("restored.lethenon");
		char[] password = "a restored wallet's password".toCharArray();

		restore.write(Wallet.create(), password);

		assertArrayEquals(new char[password.length], password,
			"the password must be zero-filled once the wallet is written");
	}

	@Test
	void openingAWallet_overwritesThePassword() throws Exception
	{
		BalanceCommand command = aCommandOn(writtenWallet("the password"));
		char[] password = "the password".toCharArray();

		command.openWallet(password);

		assertArrayEquals(new char[password.length], password,
			"the password must be zero-filled once the wallet is open");
	}

	@Test
	void openingAWalletWrittenBy010_overwritesThePassword() throws Exception
	{
		Path copy = directory.resolve("written-by-0.1.0.lethw");
		try (var source = getClass().getResourceAsStream("/wallet/written-by-0.1.0.lethw"))
		{
			Files.copy(source, copy);
		}
		char[] password = "wallet written by 0.1.0".toCharArray();

		aCommandOn(copy).openWallet(password);

		assertArrayEquals(new char[password.length], password,
			"the old layout's path must leave the command's array zero-filled as well");
	}

	@Test
	void aWrongPassword_isOverwrittenAsWell() throws Exception
	{
		BalanceCommand command = aCommandOn(writtenWallet("the password"));
		char[] wrong = "not the password".toCharArray();

		assertThrows(SecurityException.class, () -> command.openWallet(wrong));

		assertArrayEquals(new char[wrong.length], wrong,
			"a wrong password is still a password to overwrite");
	}

	private Path writtenWallet(final String password) throws Exception
	{
		Path file = directory.resolve("wallet.lethenon");
		WalletCommand.Create create = new WalletCommand.Create();
		create.wallet = file;
		create.write(Wallet.create(), password.toCharArray());
		return file;
	}

	private static BalanceCommand aCommandOn(final Path wallet)
	{
		BalanceCommand command = new BalanceCommand();
		command.wallet = wallet;
		return command;
	}
}
