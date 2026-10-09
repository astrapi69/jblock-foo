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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the chain fixtures in src/test/resources/chain, so that they can be made again when the
 * encoding changes instead of being bytes nobody can reproduce. Run with
 * {@code ./gradlew writeChainFixtures}.
 * <p>
 * Fresh keys every time: no private key is written anywhere, and none is needed to verify. What is
 * written is the chain, the same chain with one bit of its last signature flipped, and the two
 * public keys the chain names, so that a test can say whose balance it expects.
 */
final class ChainFixtureWriter
{

	private ChainFixtureWriter()
	{
	}

	/**
	 * Writes the fixtures
	 *
	 * @param arguments
	 *            the directory to write into
	 * @throws IOException
	 *             if a file cannot be written
	 */
	public static void main(final String[] arguments) throws IOException
	{
		Path directory = Path.of(arguments[0]);
		KeyPair holder = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		KeyPair miner = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		Bytes holderKey = TransactionSigner.asBytes(holder.getPublic());
		Bytes minerKey = TransactionSigner.asBytes(miner.getPublic());
		byte[] chain = CanonicalEncoding.encodeChain(aChain(holder, holderKey, minerKey));
		byte[] corrupted = chain.clone();
		// the file ends with the signature of the last transfer
		corrupted[corrupted.length - 1] ^= 1;
		Files.write(directory.resolve("chain.lethenon"), chain);
		Files.write(directory.resolve("chain-corrupted.lethenon"), corrupted);
		Files.write(directory.resolve("holder.key"), holderKey.toByteArray());
		Files.write(directory.resolve("miner.key"), minerKey.toByteArray());
		System.out.println("wrote the chain fixtures to " + directory + ": the chain file is "
			+ chain.length + " bytes");
	}

	private static List<BlockBody> aChain(final KeyPair holder, final Bytes holderKey,
		final Bytes minerKey)
	{
		SignedTransaction transfer = TransactionSigner.sign(
			new TransactionBody(Chain.TEST_IDENTIFIER, 0L, holderKey, Destination.direct(minerKey),
				Amount.ofLeth(42L), Amount.ofLethe(100L), "no permanent record about people"),
			SignatureSuite.ED25519, holder.getPrivate());
		BlockBody genesis = Blocks
			.mine(new BlockBody(Chain.TEST_IDENTIFIER, 0L, Bytes.of(new byte[32]), Genesis.NOBODY,
				new ArrayList<>(), 1_759_000_000_000L, 8, "in the beginning was the pun"),
				1_000_000L)
			.orElseThrow();
		BlockBody first = Blocks
			.mine(new BlockBody(Chain.TEST_IDENTIFIER, 1L, Blocks.hashOf(genesis), holderKey,
				new ArrayList<>(), 1_759_000_060_000L, 8, "the first reward"), 1_000_000L)
			.orElseThrow();
		BlockBody second = Blocks
			.mine(new BlockBody(Chain.TEST_IDENTIFIER, 2L, Blocks.hashOf(first), minerKey,
				List.of(transfer), 1_759_000_120_000L, 8, "surveillance is not security"),
				1_000_000L)
			.orElseThrow();
		return List.of(genesis, first, second);
	}
}
