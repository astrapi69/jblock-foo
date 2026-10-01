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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.astrapi69.mystic.crypt.pw.PassphraseCryptor;

/**
 * The wallet's own file: a separate file with a separate password, sealed with the same AEAD and
 * key derivation as the vault of mystic-crypt-ui - mystic-crypt's {@code PassphraseCryptor}, until
 * mystic-crypt#160 brings the vault's own envelope into the library.
 */
class WalletFileTest
{

	/** Made at test time, like every other piece of key material in this project's tests */
	private final char[] password = Base64.getEncoder().encodeToString(randomBytes(18))
		.toCharArray();

	@TempDir
	Path directory;

	@Test
	void aWalletWrittenToItsFile_comesBackWithEveryKey_andThePasswordIsLeftToItsOwner()
		throws Exception
	{
		Wallet original = Wallet.create();
		Path file = directory.resolve("wallet.lethenon");
		char[] given = password.clone();

		WalletFile.write(file, original, given);
		Wallet read = WalletFile.read(file, given);

		assertArrayEquals(password, given, "the caller's array is the caller's to wipe");
		assertEquals(original.phrase(), read.phrase());
		for (SignatureSuite suite : SignatureSuite.values())
		{
			assertEquals(original.spendKey(suite), read.spendKey(suite), suite.identifier());
		}
	}

	@Test
	void theFileIsSealedWithTheVaultsCipherAndCost_andCarriesNoKeyMaterialInTheClear()
		throws Exception
	{
		Wallet wallet = Wallet.create();
		Path file = directory.resolve("wallet.lethenon");

		WalletFile.write(file, wallet, password.clone());
		byte[] content = Files.readAllBytes(file);

		assertTrue(PassphraseCryptor.isEncrypted(content));
		assertEquals(600_000, PassphraseCryptor.iterationsOf(content));
		String hex = HexFormat.of().formatHex(content);
		assertFalse(hex.contains(HexFormat.of().formatHex(wallet.entropy())),
			"the entropy is in the file in the clear");
		assertFalse(new String(content, StandardCharsets.ISO_8859_1)
			.contains(wallet.phrase().split(" ")[0] + " "), "the phrase is in the file");
	}

	@Test
	void aWrongPassword_isRefusedAsAWrongPassword() throws Exception
	{
		Path file = directory.resolve("wallet.lethenon");
		WalletFile.write(file, Wallet.create(), password.clone());

		SecurityException refused = assertThrows(SecurityException.class,
			() -> WalletFile.read(file, (String.valueOf(password) + "x").toCharArray()));

		assertTrue(refused.getMessage().contains(file.toString()), refused.getMessage());
		assertTrue(refused.getMessage().contains("password is wrong"), refused.getMessage());
	}

	@Test
	void aFileChangedInTheLastByte_doesNotOpen() throws Exception
	{
		Path file = directory.resolve("wallet.lethenon");
		WalletFile.write(file, Wallet.create(), password.clone());
		byte[] content = Files.readAllBytes(file);
		content[content.length - 1] ^= 1;
		Files.write(file, content);

		assertThrows(SecurityException.class, () -> WalletFile.read(file, password.clone()));
	}

	@Test
	void aFileThatIsNotEncryptedAtAll_isRefusedAsNotAWalletFile() throws Exception
	{
		Path file = directory.resolve("notes.txt");
		Files.writeString(file, "nothing to see here, and nothing encrypted either");

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> WalletFile.read(file, password.clone()));

		assertTrue(refused.getMessage().contains(file.toString()), refused.getMessage());
	}

	@Test
	void somethingElseEncryptedWithTheSamePassword_isRefusedAsNotAWallet() throws Exception
	{
		Path file = directory.resolve("other.mcrypt");
		Files.write(file, PassphraseCryptor.encrypt(password.clone(), new byte[37]));

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> WalletFile.read(file, password.clone()));

		assertTrue(refused.getMessage().contains("not a lethenon wallet"), refused.getMessage());
	}

	@Test
	void anExistingFile_isNeverOverwritten_becauseItMayHoldTheOnlyCopyOfAnotherWallet()
		throws Exception
	{
		Path file = directory.resolve("wallet.lethenon");
		WalletFile.write(file, Wallet.create(), password.clone());
		byte[] before = Files.readAllBytes(file);

		assertThrows(FileAlreadyExistsException.class,
			() -> WalletFile.write(file, Wallet.create(), password.clone()));

		assertArrayEquals(before, Files.readAllBytes(file));
	}

	private static byte[] randomBytes(final int length)
	{
		byte[] bytes = new byte[length];
		new SecureRandom().nextBytes(bytes);
		return bytes;
	}
}
