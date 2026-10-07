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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which scheme of which building block each chain admits, and from which height: the consensus
 * rule of the privacy block (phase B, ADR 0002).
 * <p>
 * The rule is code, and every verifier runs the same table, so changing it is a fork at the height
 * the change names. {@link #LETHENON} admits exactly what both chains accepted before the rule
 * existed, from the genesis block on; a scheme that is not in it is refused. ADR 0001 decides what
 * may be added: a new scheme goes onto the test chain first and onto the main chain only after the
 * external review its rules 3 and 5 require.
 */
public final class ConsensusRules
{

	/** The rule both chains run today */
	public static final ConsensusRules LETHENON = new ConsensusRules(List.of(
		SchemeActivation.from(Chain.IDENTIFIER, SignatureSuite.ED25519, 0L),
		SchemeActivation.from(Chain.IDENTIFIER, SignatureSuite.ML_DSA_65, 0L),
		SchemeActivation.from(Chain.IDENTIFIER, AddressScheme.DIRECT, 0L),
		SchemeActivation.from(Chain.IDENTIFIER, AddressScheme.STEALTH_V2, 0L),
		SchemeActivation.from(Chain.IDENTIFIER, AmountScheme.PLAIN, 0L),
		SchemeActivation.from(Chain.TEST_IDENTIFIER, SignatureSuite.ED25519, 0L),
		SchemeActivation.from(Chain.TEST_IDENTIFIER, SignatureSuite.ML_DSA_65, 0L),
		SchemeActivation.from(Chain.TEST_IDENTIFIER, AddressScheme.DIRECT, 0L),
		SchemeActivation.from(Chain.TEST_IDENTIFIER, AddressScheme.STEALTH_V2, 0L),
		SchemeActivation.from(Chain.TEST_IDENTIFIER, AmountScheme.PLAIN, 0L)),
		List.of(new BlockLimits(Chain.IDENTIFIER, BlockLimits.TWO_HOURS,
			BlockLimits.MAXIMUM_BLOCK_BYTES),
			new BlockLimits(Chain.TEST_IDENTIFIER, BlockLimits.TWO_HOURS,
				BlockLimits.MAXIMUM_BLOCK_BYTES)));

	private final List<SchemeActivation> activations;

	private final List<BlockLimits> limits;

	private final Map<String, BlockBody> anchors;

	/**
	 * A rule made of the given lines
	 *
	 * @param activations
	 *            at most one line per chain and scheme
	 * @throws IllegalArgumentException
	 *             when a chain and scheme appear twice: which line would hold is then a matter of
	 *             order, and a consensus rule must not depend on that
	 */
	public ConsensusRules(final List<SchemeActivation> activations)
	{
		this(activations, List.of());
	}

	/**
	 * A rule made of the given lines and per-chain block limits; a chain without limits gets none
	 * of them, which is where the main chain stands until the decision that starts it (#96)
	 *
	 * @param activations
	 *            at most one line per chain and scheme
	 * @param limits
	 *            at most one set of block limits per chain
	 * @throws IllegalArgumentException
	 *             when a chain and scheme, or a chain's limits, appear twice
	 */
	public ConsensusRules(final List<SchemeActivation> activations, final List<BlockLimits> limits)
	{
		this(activations, limits, List.of());
	}

	/**
	 * A rule made of the given lines, block limits and genesis anchors (#104)
	 *
	 * @param activations
	 *            at most one line per chain and scheme
	 * @param limits
	 *            at most one set of block limits per chain
	 * @param anchors
	 *            at most one genesis block fixed in the code per chain
	 * @throws IllegalArgumentException
	 *             when anything appears twice for a chain, or an anchor is not a genesis block of
	 *             its chain that verifies on its own
	 */
	public ConsensusRules(final List<SchemeActivation> activations, final List<BlockLimits> limits,
		final List<GenesisAnchor> anchors)
	{
		Set<String> chains = new HashSet<>();
		for (BlockLimits each : limits)
		{
			if (!chains.add(each.chainIdentifier()))
			{
				throw new IllegalArgumentException(
					"chain '" + each.chainIdentifier() + "' has block limits twice");
			}
		}
		this.limits = List.copyOf(limits);
		Set<String> seen = new HashSet<>();
		for (SchemeActivation activation : activations)
		{
			String key = activation.chainIdentifier() + "/" + activation.scheme().block() + "/"
				+ activation.scheme().identifier();
			if (!seen.add(key))
			{
				throw new IllegalArgumentException("scheme '" + activation.scheme().identifier()
					+ "' appears twice for chain '" + activation.chainIdentifier() + "'");
			}
		}
		this.activations = List.copyOf(activations);
		this.anchors = decoded(anchors, new ConsensusRules(this.activations, this.limits, Map.of()));
	}

	private ConsensusRules(final List<SchemeActivation> activations,
		final List<BlockLimits> limits, final Map<String, BlockBody> anchors)
	{
		this.activations = activations;
		this.limits = limits;
		this.anchors = anchors;
	}

	/**
	 * The anchors as blocks, each checked: exactly one block, at height 0, of the chain it is filed
	 * under, verifying on its own under the same rule without anchors
	 */
	private static Map<String, BlockBody> decoded(final List<GenesisAnchor> anchors,
		final ConsensusRules unanchored)
	{
		Map<String, BlockBody> blocks = new HashMap<>();
		for (GenesisAnchor anchor : anchors)
		{
			BlockBody genesis = genesisOf(anchor, unanchored);
			if (blocks.put(anchor.chainIdentifier(), genesis) != null)
			{
				throw new IllegalArgumentException(
					"chain '" + anchor.chainIdentifier() + "' has two genesis anchors");
			}
		}
		return Map.copyOf(blocks);
	}

	private static BlockBody genesisOf(final GenesisAnchor anchor,
		final ConsensusRules unanchored)
	{
		String chain = anchor.chainIdentifier();
		List<BlockBody> blocks;
		try
		{
			blocks = CanonicalEncoding.readChain(Bytes.ofHex(anchor.canonicalHex()).toByteArray());
		}
		catch (IllegalArgumentException undecodable)
		{
			throw new IllegalArgumentException("the genesis anchor of chain '" + chain
				+ "' does not decode: " + undecodable.getMessage(), undecodable);
		}
		if (blocks.size() != 1)
		{
			throw new IllegalArgumentException("the genesis anchor of chain '" + chain + "' holds "
				+ blocks.size() + " blocks, and an anchor is exactly one block");
		}
		BlockBody genesis = blocks.getFirst();
		if (!chain.equals(genesis.chainIdentifier()))
		{
			throw new IllegalArgumentException("the genesis anchor filed under chain '" + chain
				+ "' is a block of chain '" + genesis.chainIdentifier() + "'");
		}
		try
		{
			Replay.verify(blocks, unanchored, genesis.timestamp());
		}
		catch (ChainRejected rejected)
		{
			throw new IllegalArgumentException("the genesis anchor of chain '" + chain
				+ "' does not verify: " + rejected.getMessage(), rejected);
		}
		return genesis;
	}

	/**
	 * The genesis block fixed in the code for a chain
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @return its anchored genesis block, empty for a chain that has none
	 */
	public Optional<BlockBody> anchorFor(final String chainIdentifier)
	{
		return Optional.ofNullable(anchors.get(chainIdentifier));
	}

	/**
	 * The block limits of a chain
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @return its limits, empty for a chain that has none
	 */
	public Optional<BlockLimits> limitsFor(final String chainIdentifier)
	{
		return limits.stream().filter(each -> each.chainIdentifier().equals(chainIdentifier))
			.findFirst();
	}

	/**
	 * The lines of this rule
	 *
	 * @return the activations
	 */
	public List<SchemeActivation> activations()
	{
		return activations;
	}

	/**
	 * Whether a chain admits a scheme at a height
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @param scheme
	 *            the scheme
	 * @param height
	 *            the block height
	 * @return true when a line admits it
	 */
	public boolean admits(final String chainIdentifier, final Scheme scheme, final long height)
	{
		return lineFor(chainIdentifier, scheme).map(line -> line.admits(height)).orElse(false);
	}

	/**
	 * Refuses a transfer that uses a scheme the chain does not admit at this height
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @param scheme
	 *            the scheme the transfer uses
	 * @param height
	 *            the height of the block that carries it
	 * @throws ChainRejected
	 *             naming the block, the scheme and the range the chain admits it in
	 */
	void requireAdmitted(final String chainIdentifier, final Scheme scheme, final long height)
	{
		Optional<SchemeActivation> line = lineFor(chainIdentifier, scheme);
		if (line.isPresent() && line.get().admits(height))
		{
			return;
		}
		String transfer = "block " + height + " carries a transfer whose " + scheme.block().label()
			+ " scheme '" + scheme.identifier() + "' chain '" + chainIdentifier + "' ";
		if (line.isEmpty())
		{
			throw new ChainRejected(transfer + "does not admit");
		}
		if (height < line.get().fromHeight())
		{
			throw new ChainRejected(transfer + "admits from height " + line.get().fromHeight());
		}
		throw new ChainRejected(
			transfer + "admitted until height " + line.get().untilHeight().getAsLong());
	}

	private Optional<SchemeActivation> lineFor(final String chainIdentifier, final Scheme scheme)
	{
		return activations.stream()
			.filter(line -> line.chainIdentifier().equals(chainIdentifier)
				&& line.scheme().block() == scheme.block()
				&& line.scheme().identifier().equals(scheme.identifier()))
			.findFirst();
	}
}
