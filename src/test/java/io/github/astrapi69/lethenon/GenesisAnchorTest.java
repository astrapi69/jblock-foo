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
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A genesis block fixed in the code: the rule table carries it as canonical bytes, a chain with
 * another genesis block does not verify, and a chain is started with it instead of a mined one
 * (#104). Tested on both chains against test anchors; the main chain's real block is filed on
 * the day the main chain starts, with the headline of that day (#148, ADR 0005).
 */
class GenesisAnchorTest
{

	private static final Bytes HOLDER = Bytes.of(new byte[] { 1 });

	private static final long TIME = 1_759_000_000_000L;

	private static BlockBody genesis(final String chain, final Bytes holder, final String pun)
	{
		return Blocks.mine(Mining.nextBlock(chain, List.of(), holder, List.of(), pun, TIME),
			1_000_000L).orElseThrow();
	}

	private static String hexOf(final List<BlockBody> blocks)
	{
		return Bytes.of(CanonicalEncoding.encodeChain(blocks)).toString();
	}

	private static ConsensusRules anchoredTo(final BlockBody block)
	{
		return withAnchor(new GenesisAnchor(block.chainIdentifier(), hexOf(List.of(block))));
	}

	private static ConsensusRules withAnchor(final GenesisAnchor anchor)
	{
		ConsensusRules rules = ConsensusRules.LETHENON;
		List<BlockLimits> limits = new ArrayList<>();
		rules.limitsFor(Chain.IDENTIFIER).ifPresent(limits::add);
		rules.limitsFor(Chain.TEST_IDENTIFIER).ifPresent(limits::add);
		return new ConsensusRules(rules.activations(), limits, List.of(anchor));
	}

	private static List<BlockBody> extended(final BlockBody first)
	{
		List<BlockBody> chain = new ArrayList<>(List.of(first));
		chain.add(Blocks.mine(Mining.nextBlock(chain, HOLDER, List.of(), "next",
			TIME + DifficultyRule.TARGET_BLOCK_MILLIS), 1_000_000L).orElseThrow());
		return List.copyOf(chain);
	}

	@Test
	@DisplayName("no chain has an anchor before the start day: the main chain's is filed on it, a test chain never gets one")
	void noChainHasAnAnchorBeforeTheStartDay()
	{
		assertTrue(ConsensusRules.LETHENON.anchorFor(Chain.IDENTIFIER).isEmpty());
		assertTrue(ConsensusRules.LETHENON.anchorFor(Chain.TEST_IDENTIFIER).isEmpty());
	}

	@ParameterizedTest(name = "a chain on the anchored genesis block of {0} verifies")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void aChainOnTheAnchoredGenesis_verifies(final String chain)
	{
		BlockBody anchored = genesis(chain, HOLDER, "fixed");
		ConsensusRules rules = anchoredTo(anchored);

		assertEquals(anchored, rules.anchorFor(chain).orElseThrow());
		assertEquals(2L, Replay.verify(extended(anchored), rules, TIME).blocks());
	}

	@ParameterizedTest(name = "on {0}, a chain on any other genesis block does not verify, and both hashes are named")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void aChainOnAnotherGenesis_doesNotVerify(final String chain)
	{
		BlockBody anchored = genesis(chain, HOLDER, "fixed");
		BlockBody other = genesis(chain, HOLDER, "another");

		ChainRejected rejected = assertThrows(ChainRejected.class,
			() -> Replay.verify(extended(other), anchoredTo(anchored), TIME));

		assertTrue(rejected.getMessage().contains(Blocks.hashOf(anchored).toString()),
			rejected.getMessage());
		assertTrue(rejected.getMessage().contains(Blocks.hashOf(other).toString()),
			rejected.getMessage());
	}

	@ParameterizedTest(name = "an anchor of {0} binds only its own chain, not {1}")
	@CsvSource({ Chain.TEST_IDENTIFIER + ", " + Chain.IDENTIFIER,
		Chain.IDENTIFIER + ", " + Chain.TEST_IDENTIFIER })
	void anAnchorBindsOnlyItsOwnChain(final String anchoredChain, final String freeChain)
	{
		ConsensusRules rules = anchoredTo(genesis(anchoredChain, HOLDER, "fixed"));

		assertEquals(2L, Replay.verify(extended(genesis(freeChain, HOLDER, "free")), rules,
			TIME).blocks());
	}

	record Malformed(String name, Function<BlockBody, String> hex, String reasonNames)
	{
		@Override
		public String toString()
		{
			return name;
		}
	}

	static Stream<Malformed> malformedAnchors()
	{
		return Stream.of(
			new Malformed("bytes that do not decode", block -> "00ff", "decode"),
			new Malformed("no block at all", block -> hexOf(List.of()), "exactly one block"),
			new Malformed("two blocks", block -> hexOf(extended(block)), "exactly one block"),
			new Malformed("the genesis block of the main chain",
				block -> hexOf(List.of(genesis(Chain.IDENTIFIER, HOLDER, "main"))), Chain.IDENTIFIER),
			new Malformed("a block that is not mined",
				block -> hexOf(List.of(new BlockBody(Chain.TEST_IDENTIFIER, 0L,
					Bytes.of(new byte[32]), HOLDER, List.of(), TIME, 256, "never mined"))),
				"does not verify"));
	}

	@ParameterizedTest(name = "{0} is no anchor")
	@MethodSource("malformedAnchors")
	void aMalformedAnchor_isRefused(final Malformed malformed)
	{
		String hex = malformed.hex().apply(genesis(Chain.TEST_IDENTIFIER, HOLDER, "fixed"));

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> withAnchor(new GenesisAnchor(Chain.TEST_IDENTIFIER, hex)));

		assertTrue(refused.getMessage().contains(malformed.reasonNames()), refused.getMessage());
	}

	@Test
	@DisplayName("a chain cannot have two anchors")
	void aChainCannotHaveTwoAnchors()
	{
		GenesisAnchor one = new GenesisAnchor(Chain.TEST_IDENTIFIER,
			hexOf(List.of(genesis(Chain.TEST_IDENTIFIER, HOLDER, "one"))));
		GenesisAnchor two = new GenesisAnchor(Chain.TEST_IDENTIFIER,
			hexOf(List.of(genesis(Chain.TEST_IDENTIFIER, HOLDER, "two"))));

		assertThrows(IllegalArgumentException.class,
			() -> new ConsensusRules(ConsensusRules.LETHENON.activations(), List.of(),
				List.of(one, two)));
	}

	@ParameterizedTest(name = "an anchored {0} starts with its anchor, whoever starts it")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	void anAnchoredChain_startsWithItsAnchor(final String chain)
	{
		BlockBody anchored = genesis(chain, HOLDER, "fixed");

		BlockBody started = Genesis.start(anchoredTo(anchored), chain, "whatever", TIME + 1L);

		assertEquals(anchored, started);
	}

	@Test
	@DisplayName("a chain without an anchor starts with a mined genesis block paying the burn account")
	void aChainWithoutAnAnchor_startsWithAMinedGenesis()
	{
		BlockBody started = Genesis.start(Chain.TEST_IDENTIFIER, "free", TIME);

		assertEquals(Genesis.NOBODY, started.beneficiary());
		assertEquals(0L, started.height());
		assertEquals(1L, Replay.verify(List.of(started)).blocks());
	}
}
