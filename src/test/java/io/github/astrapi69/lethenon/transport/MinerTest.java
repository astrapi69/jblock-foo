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
import static io.github.astrapi69.lethenon.transport.Networks.testGenesis;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.astrapi69.lethenon.Amount;
import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.Destination;
import io.github.astrapi69.lethenon.Replay;
import io.github.astrapi69.lethenon.SignatureSuite;
import io.github.astrapi69.lethenon.SignedTransaction;
import io.github.astrapi69.lethenon.TransactionBody;
import io.github.astrapi69.lethenon.Wallet;

/**
 * A node that mines extends its chain from its pool, and the other nodes follow (ADR 0003, the node
 * command)
 */
class MinerTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private final Wallet holder = Wallet.create();

	private final Bytes account = holder.spendKey(SignatureSuite.ED25519);

	private final List<BlockBody> genesis = testGenesis(account);

	private SignedTransaction transfer(final long nonce)
	{
		return holder.sign(new TransactionBody(Chain.TEST_IDENTIFIER, nonce, account,
			Destination.direct(Bytes.of(new byte[] { 7 })), Amount.ofLeth(3L), Amount.ZERO, ""),
			SignatureSuite.ED25519);
	}

	@Test
	@DisplayName("a mining node carries the waiting transfer, and the other node follows")
	void aMiningNode_carriesTheWaitingTransfer_andTheOtherFollows() throws IOException
	{
		SignedTransaction first = transfer(0L);
		try (Node mining = Node.on(genesis); Node following = Node.on(genesis))
		{
			following.connect("127.0.0.1", mining.listen(0));
			await("the handshake", () -> mining.peers().size() == 1);
			mining.submitTransfer(first);
			await("the transfer at the other node", () -> following.pending().size() == 1);

			try (Miner miner = Miner.start(mining, MINER, "a pun"))
			{
				await("three blocks at the other node", () -> following.chain().size() >= 3);
				await("every pool empty",
					() -> mining.pending().isEmpty() && following.pending().isEmpty());
			}

			List<BlockBody> chain = following.chain();
			assertEquals(1L, Replay.verify(chain).transactions());
			assertEquals(MINER, chain.getLast().beneficiary());
		}
	}

	@Test
	@DisplayName("a mined block carries at most 500 waiting transfers")
	void aMinedBlock_carriesAtMost500Transfers()
	{
		List<SignedTransaction> waiting = Collections.nCopies(Miner.TRANSFERS_PER_BLOCK + 1,
			transfer(0L));

		assertEquals(Miner.TRANSFERS_PER_BLOCK, Miner.carried(waiting).size());
		assertEquals(1, Miner.carried(waiting.subList(0, 1)).size());
	}
}
