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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mining varies only the pun, so what it hashes per attempt must not grow with the transfers a
 * block carries (#151). The Merkle root was computed again for every attempt, which made a block
 * of 40 transfers about 40 times slower to mine than an empty one: 9,212 against 373,750 attempts
 * a second, measured on lethenon 0.3.0. On a test network with transfers arriving faster than
 * blocks, the waiting transfers then made every next block slower still, until none came.
 */
class MiningRateTest
{

	private static final KeyPair SENDER = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private static List<SignedTransaction> transfers(final int count)
	{
		Bytes sender = TransactionSigner.asBytes(SENDER.getPublic());
		List<SignedTransaction> signed = new ArrayList<>();
		for (int nonce = 0; nonce < count; nonce++)
		{
			signed.add(TransactionSigner.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce,
				sender, Destination.direct(Bytes.of(new byte[] { 7 })), Amount.ofLeth(1L),
				Amount.ZERO, "a waiting transfer " + nonce), SignatureSuite.ED25519,
				SENDER.getPrivate()));
		}
		return signed;
	}

	/** A block nobody can mine, so that every attempt is made and the time is the attempts' time */
	private static BlockBody unmineable(final List<SignedTransaction> carried)
	{
		return new BlockBody(Chain.TEST_IDENTIFIER, 1L, Bytes.of(new byte[32]),
			Bytes.of(new byte[] { 1 }), carried, 1_759_000_000_000L, DifficultyRule.MAXIMUM, "rate");
	}

	private static double attemptsPerSecond(final BlockBody block, final long attempts)
	{
		Blocks.mine(block, attempts / 10);
		long start = System.nanoTime();
		Blocks.mine(block, attempts);
		return attempts / ((System.nanoTime() - start) / 1e9);
	}

	@Test
	@DisplayName("a block of 40 transfers mines at least a quarter as fast as an empty one")
	void theTransfersCarried_doNotSlowEveryAttempt()
	{
		double empty = attemptsPerSecond(unmineable(List.of()), 100_000L);
		double full = attemptsPerSecond(unmineable(transfers(40)), 100_000L);

		assertTrue(full >= empty / 4,
			String.format("%,.0f attempts a second with 40 transfers against %,.0f with none", full,
				empty));
	}

	@Test
	@DisplayName("mining with the root computed once finds the block the full hash accepts")
	void aMinedBlockWithTransfers_isMinedByTheFullHash()
	{
		BlockBody block = new BlockBody(Chain.TEST_IDENTIFIER, 1L, Bytes.of(new byte[32]),
			Bytes.of(new byte[] { 1 }), transfers(5), 1_759_000_000_000L, 12, "found");

		BlockBody mined = Blocks.mine(block, 1_000_000L).orElseThrow();

		assertTrue(Blocks.isMined(mined));
		assertEquals(block.transactions(), mined.transactions());
		assertTrue(Blocks.leadingZeroBits(Blocks.hashOf(mined).toByteArray()) >= 12);
	}
}
