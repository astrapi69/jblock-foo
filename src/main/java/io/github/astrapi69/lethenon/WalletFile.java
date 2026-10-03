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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import io.github.astrapi69.mystic.crypt.secret.SecretBuffers;
import io.github.astrapi69.mystic.crypt.pw.PassphraseCryptor;

/**
 * The wallet's own file: separate from anything else, opened with its own password (lethenon#2,
 * milestone 4).
 * <p>
 * Sealed with mystic-crypt's {@code PassphraseCryptor}: key-committing AES-GCM under a key that
 * PBKDF2-HMAC-SHA256 derives from the password, over a fresh 16 byte salt, 600,000 rounds - the
 * same AEAD and the same key derivation as the vault of mystic-crypt-ui. The vault's own envelope
 * (marker, salt and iteration count as associated data) is application code today;
 * mystic-crypt#160 moves it into the library, and this file follows once it is released. Until
 * then the outer layout is the library's {@code MCRYPT} one.
 * <p>
 * What is sealed is the wallet's entropy and nothing else - every key is derived from it - behind
 * a marker and a format version of this file's own, so a different thing sealed with the same
 * password is refused as not a wallet rather than read as one:
 *
 * <pre>
 * "LETHW"     5 bytes
 * version     1 byte    1
 * entropy    32 bytes
 * </pre>
 *
 * A file is never overwritten. The only copy of another wallet may be in it.
 */
public final class WalletFile
{

	/** The marker the sealed content starts with */
	static final byte[] MAGIC = "LETHW".getBytes(StandardCharsets.US_ASCII);

	/** The version of the sealed content described above */
	static final byte VERSION = 1;

	private static final int CONTENT_LENGTH = MAGIC.length + 1 + Wallet.ENTROPY_LENGTH;

	private WalletFile()
	{
	}

	/**
	 * Writes a wallet to a new file
	 *
	 * @param file
	 *            where to write; it must not exist yet
	 * @param wallet
	 *            the wallet
	 * @param password
	 *            the file's password; read, not modified - the array stays the caller's to wipe
	 * @throws java.nio.file.FileAlreadyExistsException
	 *             if the file exists, which is left untouched
	 * @throws IOException
	 *             if the file cannot be written
	 */
	public static void write(final Path file, final Wallet wallet, final char[] password)
		throws IOException
	{
		byte[] entropy = wallet.entropy();
		byte[] content = ByteBuffer.allocate(CONTENT_LENGTH).put(MAGIC).put(VERSION).put(entropy)
			.array();
		try
		{
			// PassphraseCryptor zeroes the array it is given, so it gets a copy of its own
			byte[] sealed = PassphraseCryptor.encrypt(password.clone(), content);
			Files.write(file, sealed, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
		finally
		{
			SecretBuffers.wipe(entropy);
			SecretBuffers.wipe(content);
		}
	}

	/**
	 * Reads a wallet from its file
	 *
	 * @param file
	 *            the wallet file
	 * @param password
	 *            the file's password; read, not modified
	 * @return the wallet
	 * @throws SecurityException
	 *             if the password is wrong or the file was altered
	 * @throws IllegalArgumentException
	 *             if the file is not a wallet file at all
	 * @throws IOException
	 *             if the file cannot be read
	 */
	public static Wallet read(final Path file, final char[] password) throws IOException
	{
		byte[] sealed = Files.readAllBytes(file);
		if (!PassphraseCryptor.isEncrypted(sealed))
		{
			throw new IllegalArgumentException(file + " is not a wallet file: it is not sealed "
				+ "with a password the way a wallet file is");
		}
		byte[] content = open(file, sealed, password);
		try
		{
			requireWalletContent(file, content);
			return Wallet.ofEntropy(Arrays.copyOfRange(content, MAGIC.length + 1, CONTENT_LENGTH));
		}
		finally
		{
			SecretBuffers.wipe(content);
		}
	}

	private static byte[] open(final Path file, final byte[] sealed, final char[] password)
	{
		try
		{
			return PassphraseCryptor.decrypt(password.clone(), sealed);
		}
		catch (SecurityException refused)
		{
			throw new SecurityException(
				file + " does not open: the password is wrong or the file was altered", refused);
		}
	}

	private static void requireWalletContent(final Path file, final byte[] content)
	{
		boolean wallet = content.length == CONTENT_LENGTH
			&& Arrays.equals(Arrays.copyOf(content, MAGIC.length), MAGIC)
			&& content[MAGIC.length] == VERSION;
		if (!wallet)
		{
			throw new IllegalArgumentException(file + " opens with this password, but what it "
				+ "holds is not a lethenon wallet of version " + VERSION);
		}
	}
}
