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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A block's timestamp may lie at most two hours after the verifying node's clock, Monero's
 * CRYPTONOTE_BLOCK_FUTURE_TIME_LIMIT; on the test chain from height 0 (#96), and on the main chain
 * from height 0 (#109)
 */
class FutureTimestampTest
{

	private static final Bytes HOLDER = Bytes.of(new byte[] { 1 });

	private static final long TWO_HOURS = 2L * 60L * 60L * 1_000L;

	private static List<BlockBody> chainOf(final String identifier, final long genesisTime,
		final long nextTime)
	{
		BlockBody genesis = Blocks.mine(Mining.nextBlock(identifier, List.of(), HOLDER, List.of(),
			"in the beginning", genesisTime), 1_000_000L).orElseThrow();
		List<BlockBody> chain = new ArrayList<>(List.of(genesis));
		chain.add(Blocks.mine(Mining.nextBlock(identifier, chain, HOLDER, List.of(), "next",
			nextTime), 1_000_000L).orElseThrow());
		return List.copyOf(chain);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("the limit is Monero's two hours on both chains")
	void theLimit_isTwoHours(final String chainIdentifier)
	{
		assertEquals(TWO_HOURS,
			ConsensusRules.LETHENON.limitsFor(chainIdentifier).orElseThrow().futureMillis());
	}

	@ParameterizedTest(name = "{0}, the clock {1} ms before the block time minus two hours")
	@CsvSource({ "lethenon-test-1, 1", "lethenon-test-1, 1000", "lethenon-test-1, 3600000",
		"lethenon-1, 1", "lethenon-1, 1000", "lethenon-1, 3600000" })
	void aBlockMoreThanTwoHoursAhead_isRejected(final String chainIdentifier,
		final long tooEarly)
	{
		List<BlockBody> chain = chainOf(chainIdentifier, 1_759_000_000_000L,
			1_759_000_000_000L + 5L * 60L * 60L * 1_000L);
		long now = chain.getLast().timestamp() - TWO_HOURS - tooEarly;

		ChainRejected rejected = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, ConsensusRules.LETHENON, now));

		assertTrue(rejected.getMessage().contains("block 1"), rejected.getMessage());
		assertTrue(rejected.getMessage().contains("in the future"), rejected.getMessage());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("a block exactly two hours ahead is accepted on both chains")
	void aBlockExactlyTwoHoursAhead_isAccepted(final String chainIdentifier)
	{
		List<BlockBody> chain = chainOf(chainIdentifier, 1_759_000_000_000L, 1_759_000_120_000L);

		assertEquals(2L, Replay.verify(chain, ConsensusRules.LETHENON,
			chain.getLast().timestamp() - TWO_HOURS).blocks());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("the genesis block is held to the same bound on both chains")
	void theGenesisBlock_isHeldToTheBound(final String chainIdentifier)
	{
		List<BlockBody> chain = chainOf(chainIdentifier, 1_759_000_000_000L, 1_759_000_120_000L);

		ChainRejected rejected = assertThrows(ChainRejected.class, () -> Replay
			.verify(chain.subList(0, 1), ConsensusRules.LETHENON,
				chain.getFirst().timestamp() - TWO_HOURS - 1L));

		assertTrue(rejected.getMessage().contains("block 0"), rejected.getMessage());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("a chain mined in the past keeps verifying as the clock moves on")
	void aChainMinedInThePast_keepsVerifying(final String chainIdentifier)
	{
		List<BlockBody> chain = chainOf(chainIdentifier, 1_759_000_000_000L, 1_759_000_120_000L);

		assertEquals(2L, Replay.verify(chain).blocks());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { Chain.TEST_IDENTIFIER, Chain.IDENTIFIER })
	@DisplayName("with the real clock, a block three hours ahead does not verify on either chain")
	void withTheRealClock_aBlockThreeHoursAhead_doesNotVerify(final String chainIdentifier)
	{
		long now = System.currentTimeMillis();
		List<BlockBody> chain = chainOf(chainIdentifier, now, now + 3L * 60L * 60L * 1_000L);

		assertThrows(ChainRejected.class, () -> Replay.verify(chain));
		assertEquals(1L, Replay.verify(chain.subList(0, 1)).blocks());
	}
}
