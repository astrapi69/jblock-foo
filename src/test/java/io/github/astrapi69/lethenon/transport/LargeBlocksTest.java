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
package io.github.astrapi69.lethenon.transport;

import static io.github.astrapi69.lethenon.transport.Networks.await;
import static io.github.astrapi69.lethenon.transport.Networks.extended;
import static io.github.astrapi69.lethenon.transport.Networks.testGenesis;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionBody;
import io.github.astrapi69.lethenon.Wallet;

/**
 * Blocks that are each close to the size limit do not fit twenty to a frame; a node answering
 * GET_BLOCKS sends as many as fit, and the node behind fetches them all (#98)
 */
class LargeBlocksTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	/** ML-DSA-65 transfers per block: 55 of 5,376 bytes stay under 300,000 */
	private static final int TRANSFERS_PER_BLOCK = 55;

	private static final int LARGE_BLOCKS = 16;

	@Test
	@DisplayName("a node behind a chain of near-limit blocks fetches every one of them")
	void aNodeBehindNearLimitBlocks_fetchesThemAll() throws IOException
	{
		Wallet holder = Wallet.create();
		List<BlockBody> genesis = testGenesis(holder.spendKey(SignatureSuite.ED25519));
		List<BlockBody> ahead = largeChain(holder, genesis);
		int bytes = CanonicalEncoding.encodeChain(ahead.subList(2, ahead.size())).length;
		assertTrue(bytes > Frames.MAXIMUM_FRAME,
			bytes + " bytes of large blocks, more than one frame");
		try (Node full = Node.on(ahead); Node behind = Node.on(genesis))
		{
			behind.connect("127.0.0.1", full.listen(0));

			try
			{
				await("the whole chain behind", () -> behind.chain().equals(ahead));
			}
			catch (AssertionError stuck)
			{
				throw new AssertionError(stuck.getMessage() + "; behind holds "
					+ behind.chain().size() + " of " + ahead.size() + " blocks, refused "
					+ behind.refusals() + ", the full node refused " + full.refusals(), stuck);
			}
		}
	}

	/**
	 * The genesis block, a block that funds the holder's ML-DSA-65 account, then blocks of
	 * ML-DSA-65 transfers from it
	 */
	private static List<BlockBody> largeChain(final Wallet holder, final List<BlockBody> genesis)
	{
		Bytes classical = holder.spendKey(SignatureSuite.ED25519);
		Bytes postQuantum = holder.spendKey(SignatureSuite.ML_DSA_65);
		Destination someone = Destination.direct(Bytes.of(new byte[] { 7 }));
		SignedTransaction funding = holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, 0L,
			classical, Destination.direct(postQuantum), Amount.ofLeth(1_000L), Amount.ZERO,
			"funding"), SignatureSuite.ED25519);
		List<BlockBody> chain = extended(genesis, MINER, List.of(funding));
		long nonce = 0L;
		for (int block = 0; block < LARGE_BLOCKS; block++)
		{
			List<SignedTransaction> transfers = new ArrayList<>();
			for (int index = 0; index < TRANSFERS_PER_BLOCK; index++)
			{
				transfers.add(holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce++,
					postQuantum, someone, Amount.ofLethe(1L), Amount.ZERO, ""),
					SignatureSuite.ML_DSA_65));
			}
			chain = extended(chain, MINER, transfers);
		}
		return chain;
	}
}
