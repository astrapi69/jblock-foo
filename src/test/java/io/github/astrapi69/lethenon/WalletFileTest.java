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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.astrapi69.mystic.crypt.pw.PassphraseCryptor;
import io.github.astrapi69.mystic.crypt.pw.PassphraseEnvelope;

/**
 * The wallet's own file: a separate file with a separate password, sealed with the same AEAD and
 * key derivation as the vault of mystic-crypt-ui - mystic-crypt's {@code PassphraseEnvelope} under
 * lethenon's own marker since #45, and {@code PassphraseCryptor}'s {@code MCRYPT} layout for the
 * wallets 0.1.0 wrote, which keep opening and are never rewritten.
 */
class WalletFileTest
{

	/** Made at test time, like every other piece of key material in this project's tests */
	private final char[] password = Base64.getEncoder().encodeToString(randomBytes(18))
		.toCharArray();

	/** The password of the wallet 0.1.0 wrote; it protects synthetic entropy and nothing else */
	private static final char[] PASSWORD_OF_THE_010_WALLET = "wallet written by 0.1.0".toCharArray();

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

		assertTrue(PassphraseEnvelope.hasMagic(content, WalletFile.FILE_MAGIC),
			"a wallet written today is sealed in the envelope under lethenon's own marker (#45)");
		assertFalse(PassphraseCryptor.isEncrypted(content),
			"and no longer in the MCRYPT layout 0.1.0 wrote");
		assertEquals(600_000, PassphraseEnvelope.iterationsOf(WalletFile.FILE_MAGIC, content));
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

	/**
	 * A wallet 0.1.0 wrote is in the MCRYPT layout, and lethenon never writes a wallet file over
	 * an existing one - so that wallet stays in that layout for good, and has to keep opening. The
	 * file is the release's own output, see src/test/resources/wallet/README.md
	 */
	@Test
	void aWalletWrittenBy010_opens_andTheFileIsLeftAsItWas(@TempDir Path copyDirectory)
		throws Exception
	{
		Path file = copyOf010Wallet(copyDirectory);
		byte[] onDiskBefore = Files.readAllBytes(file);

		Wallet read = WalletFile.read(file, PASSWORD_OF_THE_010_WALLET.clone());

		byte[] expected = new byte[Wallet.ENTROPY_LENGTH];
		for (int i = 0; i < expected.length; i++)
		{
			expected[i] = (byte)(0x40 + i);
		}
		assertArrayEquals(expected, read.entropy(), "the wallet 0.1.0 sealed is the wallet read");
		assertArrayEquals(onDiskBefore, Files.readAllBytes(file),
			"opening a wallet reads it and nothing more - a migration is the user's action");
	}

	@Test
	void aWrongPasswordOnAWalletWrittenBy010_isStillRefusedAsAWrongPassword(
		@TempDir Path copyDirectory) throws Exception
	{
		Path file = copyOf010Wallet(copyDirectory);

		SecurityException refused = assertThrows(SecurityException.class,
			() -> WalletFile.read(file, "not the password".toCharArray()));

		assertTrue(refused.getMessage().contains(file.toString()), refused.getMessage());
	}

	@Test
	void somethingElseInTheEnvelopeUnderTheWalletMarker_isRefusedAsNotAWallet() throws Exception
	{
		Path file = directory.resolve("other.lethwf");
		Files.write(file, PassphraseEnvelope.encrypt(WalletFile.FILE_MAGIC, new byte[37],
			password.clone()));

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> WalletFile.read(file, password.clone()));

		assertTrue(refused.getMessage().contains("not a lethenon wallet"), refused.getMessage());
	}

	@Test
	void theWalletMarker_isNotTheOneOfTheReleasedLayout()
	{
		assertFalse(java.util.Arrays.equals(PassphraseCryptor.MAGIC, WalletFile.FILE_MAGIC),
			"MCRYPT means the old layout and nothing else, or the two could not be told apart");
	}

	/**
	 * Keeps every buffer handed out, and a copy of it as it was then: the copy shows the buffer held
	 * a secret, the buffer itself shows whether it still does
	 */
	private static final class Buffers implements Consumer<byte[]>
	{
		final List<byte[]> held = new ArrayList<>();

		final List<byte[]> asHanded = new ArrayList<>();

		@Override
		public void accept(final byte[] buffer)
		{
			held.add(buffer);
			asHanded.add(buffer.clone());
		}

		void assertEachHeldASecretAndIsZeroNow(final int expected)
		{
			assertEquals(expected, held.size(), "buffers handed out");
			for (int index = 0; index < held.size(); index++)
			{
				byte[] then = asHanded.get(index);
				assertFalse(java.util.Arrays.equals(new byte[then.length], then),
					"buffer " + index + " held something when it was handed out");
				assertArrayEquals(new byte[then.length], held.get(index),
					"buffer " + index + " is zero-filled now, not merely dropped");
			}
		}
	}

	@Test
	@DisplayName("writing overwrites the entropy and the content it held before it returns (#43)")
	void write_overwritesEveryBufferThatHeldTheSecret() throws Exception
	{
		Buffers buffers = new Buffers();

		WalletFile.write(directory.resolve("wallet.lethwf"), Wallet.create(), password.clone(),
			buffers);

		buffers.assertEachHeldASecretAndIsZeroNow(2);
	}

	@Test
	@DisplayName("reading overwrites the content it decrypted before it returns (#43)")
	void read_overwritesTheDecryptedContent() throws Exception
	{
		Wallet original = Wallet.create();
		Path file = directory.resolve("wallet.lethwf");
		WalletFile.write(file, original, password.clone());
		Buffers buffers = new Buffers();

		Wallet read = WalletFile.read(file, password.clone(), buffers);

		assertArrayEquals(original.entropy(), read.entropy(), "the wallet read is the wallet written");
		buffers.assertEachHeldASecretAndIsZeroNow(1);
	}

	@Test
	@DisplayName("reading a wallet 0.1.0 wrote overwrites the content it decrypted too (#43)")
	void read_ofAWalletWrittenBy010_overwritesTheDecryptedContent(@TempDir Path copyDirectory)
		throws Exception
	{
		Buffers buffers = new Buffers();

		WalletFile.read(copyOf010Wallet(copyDirectory), PASSWORD_OF_THE_010_WALLET.clone(), buffers);

		buffers.assertEachHeldASecretAndIsZeroNow(1);
	}

	@Test
	@DisplayName("content that is refused as not a wallet is overwritten as well (#43)")
	void read_ofSomethingThatIsNotAWallet_overwritesTheContent() throws Exception
	{
		Path file = directory.resolve("other.lethwf");
		byte[] notAWallet = new byte[37];
		java.util.Arrays.fill(notAWallet, (byte)7);
		Files.write(file, PassphraseEnvelope.encrypt(WalletFile.FILE_MAGIC, notAWallet,
			password.clone()));
		Buffers buffers = new Buffers();

		assertThrows(IllegalArgumentException.class,
			() -> WalletFile.read(file, password.clone(), buffers));

		buffers.assertEachHeldASecretAndIsZeroNow(1);
	}

	private static Path copyOf010Wallet(final Path directory) throws Exception
	{
		Path copy = directory.resolve("written-by-0.1.0.lethw");
		try (var source = WalletFileTest.class.getResourceAsStream("/wallet/written-by-0.1.0.lethw"))
		{
			Files.copy(source, copy);
		}
		return copy;
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
