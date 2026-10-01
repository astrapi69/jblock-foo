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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The one encoder, and the only one.
 * <p>
 * Two encoders that disagree by a byte produce a chain split: the same transaction hashes
 * differently on two machines, the signatures stop verifying across them, and the network forks
 * over an implementation detail. So there is one, it is deterministic - every field in a fixed
 * order, every variable length prefixed, big endian throughout - and it carries its own version in
 * the first byte, so a build that meets a newer encoding refuses rather than guesses.
 */
public final class CanonicalEncoding
{

	/** The version of this encoding, the first byte of everything it writes */
	public static final byte VERSION = 1;

	private CanonicalEncoding()
	{
	}

	/**
	 * The canonical bytes of a transfer
	 *
	 * @param body
	 *            the transfer
	 * @return its bytes
	 */
	public static byte[] encode(final TransactionBody body)
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.write(VERSION);
		writeText(bytes, body.chainIdentifier());
		writeLong(bytes, body.nonce());
		writeBytes(bytes, body.sender());
		writeDestination(bytes, body.recipient());
		writeLong(bytes, body.amount().lethe());
		writeLong(bytes, body.fee().lethe());
		writeText(bytes, body.memo());
		return bytes.toByteArray();
	}

	/**
	 * The canonical bytes of a block: its header, with the Merkle root standing for the
	 * transactions it carries. The pun is in here like every other field - a pun the hash did not
	 * cover would prove nothing when it is mined
	 *
	 * @param body
	 *            the block
	 * @param merkleRoot
	 *            the root over its transactions
	 * @return its bytes
	 */
	public static byte[] encode(final BlockBody body, final Bytes merkleRoot)
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.write(VERSION);
		writeText(bytes, body.chainIdentifier());
		writeLong(bytes, body.height());
		writeBytes(bytes, body.previousHash());
		writeBytes(bytes, merkleRoot);
		writeLong(bytes, body.timestamp());
		writeLong(bytes, body.difficulty());
		writeText(bytes, body.pun());
		return bytes.toByteArray();
	}

	/**
	 * The canonical bytes of a signed transfer: the transfer, the suite that signed it and the
	 * signature
	 *
	 * @param transaction
	 *            the signed transfer
	 * @return its bytes
	 */
	public static byte[] encode(final SignedTransaction transaction)
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		writeBytes(bytes, Bytes.of(encode(transaction.body())));
		writeText(bytes, transaction.suite().identifier());
		writeBytes(bytes, transaction.signature());
		return bytes.toByteArray();
	}

	/**
	 * The canonical bytes of a whole chain: every block with the transactions it carries, so that
	 * another process can replay exactly what this one wrote
	 *
	 * @param chain
	 *            the blocks, lowest height first
	 * @return its bytes
	 */
	public static byte[] encodeChain(final List<BlockBody> chain)
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.write(VERSION);
		writeLong(bytes, chain.size());
		for (BlockBody block : chain)
		{
			writeBytes(bytes, Bytes.of(encode(block, Blocks.merkleRoot(block))));
			writeLong(bytes, block.transactions().size());
			for (SignedTransaction transaction : block.transactions())
			{
				writeBytes(bytes, Bytes.of(encode(transaction)));
			}
		}
		return bytes.toByteArray();
	}

	/**
	 * Reads back what {@link #encodeChain(List)} wrote
	 *
	 * @param encoded
	 *            the bytes
	 * @return the blocks, lowest height first
	 * @throws IllegalArgumentException
	 *             if the bytes carry a version this build does not know
	 */
	public static List<BlockBody> readChain(final byte[] encoded)
	{
		ByteBuffer buffer = ByteBuffer.wrap(encoded);
		requireKnownVersion(buffer.get());
		int blocks = (int)buffer.getLong();
		List<BlockBody> chain = new java.util.ArrayList<>();
		for (int height = 0; height < blocks; height++)
		{
			byte[] header = readBytes(buffer).toByteArray();
			int count = (int)buffer.getLong();
			List<SignedTransaction> transactions = new java.util.ArrayList<>();
			for (int index = 0; index < count; index++)
			{
				transactions.add(readSignedTransaction(readBytes(buffer).toByteArray()));
			}
			chain.add(readBlockHeader(header, transactions));
		}
		return chain;
	}

	private static SignedTransaction readSignedTransaction(final byte[] encoded)
	{
		ByteBuffer buffer = ByteBuffer.wrap(encoded);
		TransactionBody body = readTransaction(readBytes(buffer).toByteArray());
		SignatureSuite suite = SignatureSuite.withIdentifier(readText(buffer));
		return new SignedTransaction(body, suite, readBytes(buffer));
	}

	private static BlockBody readBlockHeader(final byte[] encoded,
		final List<SignedTransaction> transactions)
	{
		ByteBuffer buffer = ByteBuffer.wrap(encoded);
		requireKnownVersion(buffer.get());
		String chainIdentifier = readText(buffer);
		long height = buffer.getLong();
		Bytes previousHash = readBytes(buffer);
		// the Merkle root is not read back: it is recomputed from the transactions, so a chain
		// whose root does not match its transactions fails the hash check rather than being
		// believed
		readBytes(buffer);
		long timestamp = buffer.getLong();
		int difficulty = (int)buffer.getLong();
		String pun = readText(buffer);
		return new BlockBody(chainIdentifier, height, previousHash, transactions, timestamp,
			difficulty, pun);
	}

	private static void requireKnownVersion(final byte version)
	{
		if (version != VERSION)
		{
			throw new IllegalArgumentException("this build reads encoding version " + VERSION
				+ " and the bytes carry version " + version);
		}
	}

	/**
	 * Reads back what {@link #encode(TransactionBody)} wrote
	 *
	 * @param encoded
	 *            the bytes
	 * @return the transfer
	 * @throws IllegalArgumentException
	 *             if the bytes carry a version this build does not know
	 */
	public static TransactionBody readTransaction(final byte[] encoded)
	{
		ByteBuffer buffer = ByteBuffer.wrap(encoded);
		byte version = buffer.get();
		if (version != VERSION)
		{
			throw new IllegalArgumentException("this build reads encoding version " + VERSION
				+ " and the bytes carry version " + version
				+ "; what a newer version added cannot be guessed");
		}
		String chainIdentifier = readText(buffer);
		long nonce = buffer.getLong();
		Bytes sender = readBytes(buffer);
		Destination recipient = readDestination(buffer);
		Amount amount = Amount.ofLethe(buffer.getLong());
		Amount fee = Amount.ofLethe(buffer.getLong());
		String memo = readText(buffer);
		return new TransactionBody(chainIdentifier, nonce, sender, recipient, amount, fee, memo);
	}

	private static void writeDestination(final ByteArrayOutputStream bytes,
		final Destination destination)
	{
		writeText(bytes, destination.scheme().identifier());
		writeBytes(bytes, destination.key());
		writeBytes(bytes, destination.ephemeralKey());
		bytes.write(destination.viewTag());
	}

	private static Destination readDestination(final ByteBuffer buffer)
	{
		AddressScheme scheme = AddressScheme.withIdentifier(readText(buffer));
		Bytes key = readBytes(buffer);
		Bytes ephemeralKey = readBytes(buffer);
		int viewTag = Byte.toUnsignedInt(buffer.get());
		return new Destination(scheme, key, ephemeralKey, viewTag);
	}

	private static void writeLong(final ByteArrayOutputStream bytes, final long value)
	{
		bytes.writeBytes(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
	}

	private static void writeBytes(final ByteArrayOutputStream bytes, final Bytes value)
	{
		byte[] content = value.toByteArray();
		bytes.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(content.length).array());
		bytes.writeBytes(content);
	}

	private static void writeText(final ByteArrayOutputStream bytes, final String text)
	{
		writeBytes(bytes, Bytes.of(text.getBytes(StandardCharsets.UTF_8)));
	}

	private static Bytes readBytes(final ByteBuffer buffer)
	{
		byte[] content = new byte[buffer.getInt()];
		buffer.get(content);
		return Bytes.of(content);
	}

	private static String readText(final ByteBuffer buffer)
	{
		return new String(readBytes(buffer).toByteArray(), StandardCharsets.UTF_8);
	}
}
