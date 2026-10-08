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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The consensus rule of the privacy block's phase B: which scheme of which building block a chain
 * admits from which height on, and until which. The table of today admits exactly what the chain
 * accepted before the rule existed; the rule itself is shown to bite with tables that admit less.
 */
class ConsensusRulesTest
{

	private static final Bytes MINER = Bytes.of(new byte[] { 9 });

	private static final long NOW = 1_759_500_000_000L;

	private final Wallet holder = Wallet.create();

	private final Bytes holderKey = holder.spendKey(SignatureSuite.ED25519);

	static Stream<Arguments> everySchemeThatWasAcceptedBefore()
	{
		List<Arguments> cases = new ArrayList<>();
		for (String chain : List.of(Chain.IDENTIFIER, Chain.TEST_IDENTIFIER))
		{
			for (Scheme scheme : List.<Scheme> of(SignatureSuite.ED25519, SignatureSuite.ML_DSA_65,
				AddressScheme.DIRECT, AddressScheme.STEALTH_V2, AmountScheme.PLAIN))
			{
				for (long height : new long[] { 0L, 1L, 500_000L, Long.MAX_VALUE })
				{
					cases.add(Arguments.of(chain, scheme, height));
				}
			}
		}
		return cases.stream();
	}

	@ParameterizedTest(name = "{0} admits {1} at height {2}")
	@MethodSource("everySchemeThatWasAcceptedBefore")
	void theTable_admitsEverySchemeTheChainAcceptedBefore(final String chain, final Scheme scheme,
		final long height)
	{
		assertTrue(ConsensusRules.LETHENON.admits(chain, scheme, height));
	}

	@ParameterizedTest(name = "{0} never admits stealth-v1")
	@MethodSource("bothChains")
	void theTable_neverAdmitsTheFirstStealthScheme(final String chain)
	{
		assertFalse(ConsensusRules.LETHENON.admits(chain, AddressScheme.STEALTH_V1, 0L));
		assertFalse(ConsensusRules.LETHENON.admits(chain, AddressScheme.STEALTH_V1, Long.MAX_VALUE));
	}

	static Stream<String> bothChains()
	{
		return Stream.of(Chain.IDENTIFIER, Chain.TEST_IDENTIFIER);
	}

	@Test
	@DisplayName("every scheme names the building block it belongs to")
	void everyScheme_namesItsBuildingBlock()
	{
		for (SignatureSuite suite : SignatureSuite.values())
		{
			assertEquals(BuildingBlock.AUTHORIZATION, suite.block(), suite.identifier());
		}
		for (AddressScheme scheme : AddressScheme.values())
		{
			assertEquals(BuildingBlock.RECIPIENT, scheme.block(), scheme.identifier());
		}
		for (AmountScheme scheme : AmountScheme.values())
		{
			assertEquals(BuildingBlock.AMOUNT, scheme.block(), scheme.identifier());
		}
	}

	@Test
	@DisplayName("no two schemes of one building block share an identifier")
	void identifiers_areUniqueWithinABuildingBlock()
	{
		Set<String> seen = new HashSet<>();
		for (Scheme scheme : Scheme.all())
		{
			assertTrue(seen.add(scheme.block() + "/" + scheme.identifier()),
				"twice: " + scheme.identifier());
		}
	}

	@Test
	@DisplayName("a version 1 transfer carries its amount in the clear")
	void aVersionOneTransfer_hasThePlainAmountScheme()
	{
		SignedTransaction transfer = holder.sign(new TransactionBody(Chain.IDENTIFIER, 0L, holderKey,
			Destination.direct(MINER), Amount.ofLeth(1L), Amount.ZERO, "plain"),
			SignatureSuite.ED25519);

		assertEquals(AmountScheme.PLAIN, transfer.body().amountScheme());
		assertEquals("plain", AmountScheme.PLAIN.identifier());
	}

	@Test
	@DisplayName("a scheme admitted from a later height is refused before it, and accepted from it")
	void aSchemeAdmittedLater_isRefusedBefore_andAcceptedFrom()
	{
		ConsensusRules ed25519FromTwo = rulesWith(
			SchemeActivation.from(Chain.TEST_IDENTIFIER, SignatureSuite.ED25519, 2L));
		List<BlockBody> chain = testChain();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(minedOnto(chain, List.of(aTransfer(chain))), ed25519FromTwo));
		assertEquals("block 1 carries a transfer whose authorization scheme 'ed25519' chain '"
			+ Chain.TEST_IDENTIFIER + "' admits from height 2", refused.getMessage());

		List<BlockBody> waited = minedOnto(chain, List.of());
		Replay replay = Replay.verify(minedOnto(waited, List.of(aTransfer(waited))), ed25519FromTwo);
		assertEquals(1L, replay.transactions());
	}

	@Test
	@DisplayName("a scheme switched off at a height is accepted before it, and refused from it")
	void aSchemeSwitchedOff_isAcceptedBefore_andRefusedFrom()
	{
		ConsensusRules ed25519UntilTwo = rulesWith(
			SchemeActivation.between(Chain.TEST_IDENTIFIER, SignatureSuite.ED25519, 0L, 2L));
		List<BlockBody> chain = testChain();
		List<BlockBody> early = minedOnto(chain, List.of(aTransfer(chain)));

		assertEquals(1L, Replay.verify(early, ed25519UntilTwo).transactions());

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(minedOnto(early, List.of(aTransfer(early))), ed25519UntilTwo));
		assertEquals("block 2 carries a transfer whose authorization scheme 'ed25519' chain '"
			+ Chain.TEST_IDENTIFIER + "' admitted until height 2", refused.getMessage());
	}

	@Test
	@DisplayName("a recipient scheme the chain does not admit is refused")
	void aRecipientSchemeNotAdmitted_isRefused()
	{
		ConsensusRules withoutStealth = new ConsensusRules(ConsensusRules.LETHENON.activations()
			.stream()
			.filter(activation -> !(activation.chainIdentifier().equals(Chain.TEST_IDENTIFIER)
				&& activation.scheme() == AddressScheme.STEALTH_V2))
			.toList());
		List<BlockBody> chain = testChain();
		Destination oneTime = OneTimeAddresses.destinationFor(Wallet.create().address(),
			OneTimeAddresses.newEphemeralKeyPair());
		SignedTransaction toAnAddress = Transfers.prepare(holder, SignatureSuite.ED25519, chain,
			List.of(), oneTime, Amount.ofLeth(1L), Amount.ZERO, "to an address");

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(minedOnto(chain, List.of(toAnAddress)), withoutStealth));
		assertEquals("block 1 carries a transfer whose recipient scheme 'stealth-v2' chain '"
			+ Chain.TEST_IDENTIFIER + "' does not admit", refused.getMessage());
	}

	@Test
	@DisplayName("an amount scheme the chain does not admit is refused")
	void anAmountSchemeNotAdmitted_isRefused()
	{
		ConsensusRules withoutPlainAmounts = new ConsensusRules(ConsensusRules.LETHENON.activations()
			.stream()
			.filter(activation -> !(activation.chainIdentifier().equals(Chain.TEST_IDENTIFIER)
				&& activation.scheme() == AmountScheme.PLAIN))
			.toList());
		List<BlockBody> chain = testChain();

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(minedOnto(chain, List.of(aTransfer(chain))), withoutPlainAmounts));
		assertEquals("block 1 carries a transfer whose amount scheme 'plain' chain '"
			+ Chain.TEST_IDENTIFIER + "' does not admit", refused.getMessage());
	}

	@Test
	@DisplayName("an activation for the test chain leaves the main chain as it was")
	void anActivationForTheTestChain_leavesTheMainChainAlone()
	{
		ConsensusRules ed25519FromTwoOnTheTestChain = rulesWith(
			SchemeActivation.from(Chain.TEST_IDENTIFIER, SignatureSuite.ED25519, 2L));
		// a main chain of its own: the rule table here carries no genesis anchor, and the anchored
		// genesis pays nobody who could sign the transfer
		List<BlockBody> main = List.of(Blocks.mine(Mining.nextBlock(Chain.IDENTIFIER, List.of(),
			holderKey, List.of(), "main", NOW), 1_000_000L).orElseThrow());
		SignedTransaction transfer = holder.sign(new TransactionBody(Chain.IDENTIFIER, 0L,
			holderKey, Destination.direct(MINER), Amount.ofLeth(1L), Amount.ZERO, "a transfer"),
			SignatureSuite.ED25519);

		assertEquals(1L, Replay.verify(minedOnto(main, List.of(transfer)),
			ed25519FromTwoOnTheTestChain).transactions());
	}

	static Stream<Arguments> activationsThatCannotBe()
	{
		return Stream.of(
			Arguments.of("a negative first height",
				(Runnable)() -> SchemeActivation.between(Chain.IDENTIFIER, SignatureSuite.ED25519, -1L,
					5L)),
			Arguments.of("an end not after the start",
				(Runnable)() -> SchemeActivation.between(Chain.IDENTIFIER, SignatureSuite.ED25519, 5L,
					5L)),
			Arguments.of("a chain that does not exist",
				(Runnable)() -> SchemeActivation.from("lethenon-3", SignatureSuite.ED25519, 0L)),
			Arguments.of("two activations of one scheme on one chain",
				(Runnable)() -> new ConsensusRules(List.of(
					SchemeActivation.from(Chain.IDENTIFIER, SignatureSuite.ED25519, 0L),
					SchemeActivation.from(Chain.IDENTIFIER, SignatureSuite.ED25519, 9L)))));
	}

	@ParameterizedTest(name = "{0} is refused")
	@MethodSource("activationsThatCannotBe")
	void anActivationThatCannotBe_isRefused(final String description, final Runnable building)
	{
		assertThrows(IllegalArgumentException.class, building::run, description);
	}

	private ConsensusRules rulesWith(final SchemeActivation replacement)
	{
		List<SchemeActivation> activations = new ArrayList<>();
		for (SchemeActivation activation : ConsensusRules.LETHENON.activations())
		{
			boolean replaced = activation.chainIdentifier().equals(replacement.chainIdentifier())
				&& activation.scheme() == replacement.scheme();
			if (!replaced)
			{
				activations.add(activation);
			}
		}
		activations.add(replacement);
		return new ConsensusRules(activations);
	}

	private SignedTransaction aTransfer(final List<BlockBody> chain)
	{
		return Transfers.prepare(holder, SignatureSuite.ED25519, chain, List.of(),
			Destination.direct(MINER), Amount.ofLeth(1L), Amount.ZERO, "a transfer");
	}

	private List<BlockBody> testChain()
	{
		return List.of(Blocks.mine(Mining.nextBlock(Chain.TEST_IDENTIFIER, List.of(), holderKey,
			List.of(), "in the beginning", NOW), 1_000_000L).orElseThrow());
	}

	private static List<BlockBody> minedOnto(final List<BlockBody> chain,
		final List<SignedTransaction> waiting)
	{
		List<BlockBody> extended = new ArrayList<>(chain);
		extended.add(Blocks.mine(Mining.nextBlock(chain, MINER, waiting, "block " + chain.size(),
			NOW + 60_000L * chain.size()), 1_000_000L).orElseThrow());
		return extended;
	}
}
