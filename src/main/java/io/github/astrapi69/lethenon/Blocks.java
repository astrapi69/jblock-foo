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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.astrapi69.crypt.api.algorithm.HashAlgorithm;
import io.github.astrapi69.crypt.data.hash.HashExtensions;

/**
 * What a block hashes to, and whether it was mined.
 * <p>
 * The Merkle root comes from crypt-data's {@code merkleTreeHash}, which is RFC 6962: a leaf and an
 * interior node hash differently, so a list of concatenated child hashes cannot be passed off as
 * the leaves below it. Writing that again here would have been the third Merkle implementation in
 * this family and the only untested one.
 */
public final class Blocks
{

	private Blocks()
	{
	}

	/**
	 * The Merkle root over the transaction hashes of a block, or the hash of nothing for an empty
	 * block
	 *
	 * @param body
	 *            the block
	 * @return the root
	 */
	public static Bytes merkleRoot(final BlockBody body)
	{
		List<byte[]> leaves = new ArrayList<>();
		for (SignedTransaction transaction : body.transactions())
		{
			leaves.add(SigningPayload.digestOf(CanonicalEncoding.encode(transaction.body())));
		}
		return Bytes.of(HashExtensions.merkleTreeHash(leaves, HashAlgorithm.SHA_256));
	}

	/**
	 * The hash of a block: the digest of its canonical encoding, with its Merkle root in place of
	 * the transactions themselves
	 *
	 * @param body
	 *            the block
	 * @return the hash
	 */
	public static Bytes hashOf(final BlockBody body)
	{
		return hashOf(body, merkleRoot(body));
	}

	/**
	 * The hash of a block whose Merkle root is already known, for mining, which varies only the
	 * pun and so hashes the same transfers on every attempt (#151)
	 */
	private static Bytes hashOf(final BlockBody body, final Bytes merkleRoot)
	{
		return Bytes.of(SigningPayload.digestOf(CanonicalEncoding.encode(body, merkleRoot)));
	}

	/**
	 * Whether a block satisfies its own difficulty: its hash has to start with that many zero BITS.
	 * <p>
	 * Bits rather than bytes, so that one step of difficulty is a factor of two instead of 256 -
	 * crypt-data's {@code Block.getLeadingZerosCount} counts bytes, which is too coarse to tune a
	 * chain by.
	 *
	 * @param body
	 *            the block
	 * @return true when the block is mined
	 */
	public static boolean isMined(final BlockBody body)
	{
		return leadingZeroBits(hashOf(body).toByteArray()) >= body.difficulty();
	}

	/**
	 * How many zero bits a hash starts with
	 *
	 * @param hash
	 *            the hash
	 * @return the count
	 */
	public static int leadingZeroBits(final byte[] hash)
	{
		int zeros = 0;
		for (byte current : hash)
		{
			if (current != 0)
			{
				return zeros + Integer.numberOfLeadingZeros(current & 0xFF) - 24;
			}
			zeros += 8;
		}
		return zeros;
	}

	/**
	 * Looks for a pun that satisfies the difficulty, by appending a counter to the one given.
	 * <p>
	 * This is proof of work with wordplay in place of a nonce: the miner picks the words, the
	 * counter does the searching, and both are inside the hash. The transfers do not change while
	 * the pun does, so their Merkle root is computed once rather than on every attempt: computed
	 * per attempt, a block of 40 transfers mined 40 times slower than an empty one (#151).
	 *
	 * @param body
	 *            the block to mine, whose pun is the starting point
	 * @param attempts
	 *            how many variations to try before giving up
	 * @return the mined block, or empty when the attempts ran out
	 */
	public static java.util.Optional<BlockBody> mine(final BlockBody body, final long attempts)
	{
		Bytes merkleRoot = merkleRoot(body);
		for (long attempt = 0; attempt < attempts; attempt++)
		{
			String pun = body.pun() + " #" + attempt;
			if (pun.length() > BlockBody.PUN_LIMIT)
			{
				return java.util.Optional.empty();
			}
			BlockBody candidate = new BlockBody(body.chainIdentifier(), body.height(),
				body.previousHash(), body.beneficiary(), body.transactions(), body.timestamp(),
				body.difficulty(), pun);
			if (leadingZeroBits(hashOf(candidate, merkleRoot).toByteArray()) >= body.difficulty())
			{
				return java.util.Optional.of(candidate);
			}
		}
		return java.util.Optional.empty();
	}

	/** The bytes of a text, for the encoder */
	static byte[] utf8(final String text)
	{
		return text.getBytes(StandardCharsets.UTF_8);
	}
}
