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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.crypt.api.algorithm.HashAlgorithm;
import io.github.astrapi69.crypt.data.hash.HashExtensions;

/**
 * What a block hashes to, and what being mined means here.
 * <p>
 * Proof of pun is proof of work with wordplay in place of a nonce: the pun is inside the hash, so
 * finding one is the work, and anybody can check it by hashing the block again.
 */
class BlocksTest
{

	private static SignedTransaction aTransfer(final long nonce)
	{
		KeyPair signer = TransactionSigner.newKeyPair(SignatureSuite.ED25519);
		TransactionBody body = new TransactionBody(Chain.IDENTIFIER, nonce,
			TransactionSigner.asBytes(signer.getPublic()),
			new Destination(AddressScheme.DIRECT, Bytes.of(new byte[] { 3 }), Bytes.of(new byte[0]),
				0),
			Amount.ofLeth(1L), Amount.ZERO, "watching is not protecting");
		return TransactionSigner.sign(body, SignatureSuite.ED25519, signer.getPrivate());
	}

	private static BlockBody aBlock(final int difficulty, final String pun,
		final List<SignedTransaction> transactions)
	{
		return aBlockPaying(Bytes.of(new byte[] { 5 }), difficulty, pun, transactions);
	}

	private static BlockBody aBlockPaying(final Bytes beneficiary, final int difficulty,
		final String pun, final List<SignedTransaction> transactions)
	{
		return new BlockBody(Chain.IDENTIFIER, 1L, Bytes.of(new byte[32]), beneficiary,
			transactions, 1_759_000_000_000L, difficulty, pun);
	}

	@Test
	@DisplayName("the same block always hashes the same, and a different pun hashes differently")
	void theHash_isAFunctionOfTheBlock()
	{
		List<SignedTransaction> transactions = List.of(aTransfer(1L));

		assertArrayEquals(Blocks.hashOf(aBlock(0, "a pun", transactions)).toByteArray(),
			Blocks.hashOf(aBlock(0, "a pun", transactions)).toByteArray(),
			"a third party has to be able to recompute it, which is what the replay does");
		assertNotEquals(Blocks.hashOf(aBlock(0, "a pun", transactions)),
			Blocks.hashOf(aBlock(0, "another pun", transactions)),
			"a pun the hash did not cover would prove nothing when it is mined");
	}

	@Test
	@DisplayName("who a block pays is inside its hash: another beneficiary is another block")
	void theBeneficiary_isCoveredByTheHash()
	{
		List<SignedTransaction> transactions = List.of(aTransfer(1L));

		assertNotEquals(
			Blocks.hashOf(aBlockPaying(Bytes.of(new byte[] { 5 }), 0, "a pun", transactions)),
			Blocks.hashOf(aBlockPaying(Bytes.of(new byte[] { 6 }), 0, "a pun", transactions)),
			"a miner the hash did not cover could be swapped by whoever replays the chain");
	}

	@Test
	@DisplayName("a block that names nobody to pay is refused when it is made")
	void aBlockPayingNobody_isRefused()
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> aBlockPaying(Bytes.of(new byte[0]), 0, "a pun", List.of()));

		assertTrue(refused.getMessage().contains("names none"), refused.getMessage());
	}

	@Test
	@DisplayName("the Merkle root is RFC 6962, from crypt-data, not a second implementation here")
	void theMerkleRoot_comesFromTheLibrary()
	{
		SignedTransaction transaction = aTransfer(1L);
		BlockBody block = aBlock(0, "one leaf", List.of(transaction));

		byte[] expected = HashExtensions.merkleTreeHash(
			List.of(SigningPayload.digestOf(CanonicalEncoding.encode(transaction.body()))),
			HashAlgorithm.SHA_256);

		assertArrayEquals(expected, Blocks.merkleRoot(block).toByteArray());
	}

	@Test
	@DisplayName("an empty block has the Merkle root of nothing, rather than none")
	void anEmptyBlock_hasTheRootOfNothing()
	{
		byte[] root = Blocks.merkleRoot(aBlock(0, "nothing to carry", new ArrayList<>()))
			.toByteArray();

		assertArrayEquals(HashExtensions.merkleTreeHash(new ArrayList<>(), HashAlgorithm.SHA_256),
			root, "the deprecated getMerkleRootHash answers null here, which is how a null reaches "
				+ "a block hash");
		assertEquals(32, root.length);
	}

	@Test
	@DisplayName("changing one transaction changes the block hash, through the root")
	void aChangedTransaction_changesTheBlockHash()
	{
		BlockBody one = aBlock(0, "same pun", List.of(aTransfer(1L)));
		BlockBody two = aBlock(0, "same pun", List.of(aTransfer(2L)));

		assertNotEquals(Blocks.hashOf(one), Blocks.hashOf(two));
	}

	@Test
	@DisplayName("mining finds a pun whose hash carries the required zero bits")
	void mining_findsAPunThatSatisfiesTheDifficulty()
	{
		BlockBody unmined = aBlock(8, "the panopticon is a poor joke", List.of(aTransfer(1L)));

		assertFalse(Blocks.isMined(unmined), "the precondition: it is not mined yet");
		Optional<BlockBody> mined = Blocks.mine(unmined, 100_000L);

		assertTrue(mined.isPresent(), "eight bits is four hundred attempts on average");
		assertTrue(Blocks.isMined(mined.get()));
		assertTrue(Blocks.leadingZeroBits(Blocks.hashOf(mined.get()).toByteArray()) >= 8);
		assertTrue(mined.get().pun().startsWith("the panopticon is a poor joke"),
			"the miner's words stay; only the counter behind them moves");
	}

	@Test
	@DisplayName("zero bits are counted in bits, not in bytes")
	void leadingZeroBits_countsBits()
	{
		assertEquals(0, Blocks.leadingZeroBits(new byte[] { (byte)0xFF }));
		assertEquals(1, Blocks.leadingZeroBits(new byte[] { 0x7F }));
		assertEquals(4, Blocks.leadingZeroBits(new byte[] { 0x0F }));
		assertEquals(8, Blocks.leadingZeroBits(new byte[] { 0x00, (byte)0xFF }));
		assertEquals(12, Blocks.leadingZeroBits(new byte[] { 0x00, 0x0F }));
		assertEquals(16, Blocks.leadingZeroBits(new byte[] { 0x00, 0x00 }),
			"one step of difficulty is a factor of two here, not of 256 as it would be with bytes");
	}

	@Test
	@DisplayName("a pun longer than the limit is refused where the block is built")
	void aPun_beyondTheLimit_isRefused()
	{
		assertThrows(IllegalArgumentException.class,
			() -> aBlock(0, "x".repeat(BlockBody.PUN_LIMIT + 1), new ArrayList<>()));
	}
}
