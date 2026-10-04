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
import io.github.astrapi69.mystic.crypt.pw.PassphraseEnvelope;

/**
 * The wallet's own file: separate from anything else, opened with its own password (lethenon#2,
 * milestone 4).
 * <p>
 * Sealed with mystic-crypt's {@code PassphraseEnvelope} under the marker {@code LETHWF}:
 * key-committing AES-GCM under a key that PBKDF2-HMAC-SHA256 derives from the password, over a
 * fresh 16 byte salt, 600,000 rounds, with marker, salt and iteration count as associated data -
 * the envelope of mystic-crypt-ui's vault, byte for byte (#45, mystic-crypt#160). A wallet 0.1.0
 * wrote is in {@code PassphraseCryptor}'s {@code MCRYPT} layout instead; it keeps opening, and
 * since nothing here writes over an existing file, it stays in that layout for good.
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

	/**
	 * The marker the file starts with since #45: mystic-crypt's {@code PassphraseEnvelope} under
	 * lethenon's own name. A wallet 0.1.0 wrote starts with {@code PassphraseCryptor}'s
	 * {@code MCRYPT} instead and keeps opening - nothing here writes over an existing wallet, so
	 * it is never rewritten
	 */
	static final byte[] FILE_MAGIC = "LETHWF".getBytes(StandardCharsets.US_ASCII);

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
			// the envelope reads the password and leaves it alone; the array stays the caller's
			byte[] sealed = envelope(() -> PassphraseEnvelope.encrypt(FILE_MAGIC, content, password));
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

	/**
	 * Opens either layout a wallet file comes in: the envelope it is written in since #45, or the
	 * {@code MCRYPT} layout 0.1.0 wrote. Opening reads and nothing more - the old layout is not
	 * rewritten, because a migration is an action of the user and not a side effect of reading
	 * (mystic-crypt#160)
	 */
	private static byte[] open(final Path file, final byte[] sealed, final char[] password)
	{
		boolean writtenBy010 = PassphraseCryptor.isEncrypted(sealed);
		if (!writtenBy010 && !PassphraseEnvelope.hasMagic(sealed, FILE_MAGIC))
		{
			throw new IllegalArgumentException(file + " is not a wallet file: it is not sealed "
				+ "with a password the way a wallet file is");
		}
		try
		{
			// PassphraseCryptor zeroes the array it is given, so it gets a copy of its own
			return writtenBy010
				? PassphraseCryptor.decrypt(password.clone(), sealed)
				: envelope(() -> PassphraseEnvelope.decrypt(FILE_MAGIC, sealed, password));
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

	/**
	 * Runs a call into {@code PassphraseEnvelope}, which declares {@code throws Exception}
	 * although everything it throws for a reason a caller can act on is already a runtime
	 * exception - {@code IllegalArgumentException} for "not this format", {@code SecurityException}
	 * for "would not open" - and both pass through here untouched. What is left is the JCA failing
	 * underneath, no PBKDF2 or AES-GCM provider: a broken installation, not a bad wallet.
	 * Workaround until mystic-crypt#182 gives the envelope PassphraseCryptor's contract
	 *
	 * @param call
	 *            the call
	 * @return what it returned
	 */
	private static byte[] envelope(final EnvelopeCall call)
	{
		try
		{
			return call.run();
		}
		catch (RuntimeException asItWas)
		{
			throw asItWas;
		}
		catch (Exception brokenInstallation)
		{
			throw new IllegalStateException("the password envelope cannot run on this "
				+ "installation: " + brokenInstallation.getMessage(), brokenInstallation);
		}
	}

	/** A call into PassphraseEnvelope, see {@link #envelope(EnvelopeCall)} */
	@FunctionalInterface
	private interface EnvelopeCall
	{
		byte[] run() throws Exception;
	}
}
