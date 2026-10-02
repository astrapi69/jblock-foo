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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A chain on disk, and the transfers waiting for its next block in a file next to it (lethenon#32).
 * <p>
 * The chain file holds the canonical encoding of the blocks and nothing else. The waiting
 * transfers live in {@code <chain>.pending}: a version byte, the number of transfers, then each
 * transfer as its length and its canonical encoding, in the order they were signed. Both are
 * written through a file next to the target and an atomic move, so a crash leaves the old content
 * or the new one and never half of either.
 * <p>
 * The command line and the desktop plugin both read and write chains through this class, so there
 * is one format and one way to write it.
 */
public final class ChainFile
{

	/** The version of the pending file described above */
	public static final byte PENDING_VERSION = 1;

	private final Path chain;

	/**
	 * A chain file at the given path, which need not exist yet
	 *
	 * @param chain
	 *            the chain file
	 */
	public ChainFile(final Path chain)
	{
		this.chain = Objects.requireNonNull(chain);
	}

	/**
	 * The chain as the file holds it, empty when there is no file yet
	 *
	 * @return the blocks, genesis first
	 * @throws IOException
	 *             if the file cannot be read
	 */
	public List<BlockBody> read() throws IOException
	{
		if (!Files.exists(chain))
		{
			return List.of();
		}
		return CanonicalEncoding.readChain(Files.readAllBytes(chain));
	}

	/**
	 * The chain, which has to exist
	 *
	 * @return the blocks, genesis first
	 * @throws IllegalArgumentException
	 *             when there is no chain yet
	 * @throws IOException
	 *             if the file cannot be read
	 */
	public List<BlockBody> require() throws IOException
	{
		List<BlockBody> blocks = read();
		if (blocks.isEmpty())
		{
			throw new IllegalArgumentException(
				"there is no chain at " + chain + " yet; mine its genesis block first");
		}
		return blocks;
	}

	/**
	 * Writes the whole chain, replacing what the file held
	 *
	 * @param blocks
	 *            the whole chain, genesis first
	 * @throws IOException
	 *             if it cannot be written
	 */
	public void write(final List<BlockBody> blocks) throws IOException
	{
		writeReplacing(chain, CanonicalEncoding.encodeChain(blocks));
	}

	/**
	 * The transfers waiting for the next block
	 *
	 * @return the transfers, in the order they were signed; none when there is no pending file
	 * @throws IllegalArgumentException
	 *             when the pending file is of another version
	 * @throws IOException
	 *             if the file cannot be read
	 */
	public List<SignedTransaction> readPending() throws IOException
	{
		Path file = pendingFile();
		if (!Files.exists(file))
		{
			return List.of();
		}
		ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(file));
		byte version = buffer.get();
		if (version != PENDING_VERSION)
		{
			throw new IllegalArgumentException(file + " is pending-file version " + version
				+ " and this build reads version " + PENDING_VERSION);
		}
		int count = buffer.getInt();
		List<SignedTransaction> transfers = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
		{
			byte[] encoded = new byte[buffer.getInt()];
			buffer.get(encoded);
			transfers.add(CanonicalEncoding.readSignedTransaction(encoded));
		}
		return transfers;
	}

	/**
	 * Replaces the transfers waiting for the next block
	 *
	 * @param transfers
	 *            all of them; none removes the pending file
	 * @throws IOException
	 *             if it cannot be written
	 */
	public void writePending(final List<SignedTransaction> transfers) throws IOException
	{
		if (transfers.isEmpty())
		{
			Files.deleteIfExists(pendingFile());
			return;
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.write(PENDING_VERSION);
		bytes.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(transfers.size()).array());
		for (SignedTransaction transfer : transfers)
		{
			byte[] encoded = CanonicalEncoding.encode(transfer);
			bytes.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
			bytes.writeBytes(encoded);
		}
		writeReplacing(pendingFile(), bytes.toByteArray());
	}

	private Path pendingFile()
	{
		return chain.resolveSibling(chain.getFileName() + ".pending");
	}

	private static void writeReplacing(final Path target, final byte[] content) throws IOException
	{
		Path temporary = target.resolveSibling(target.getFileName() + ".writing");
		Files.write(temporary, content);
		Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
			StandardCopyOption.ATOMIC_MOVE);
	}
}
