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
import java.util.List;
import java.util.Optional;

/**
 * How a chain starts: with the genesis block fixed in the code for it, or, for a test chain, with
 * one mined now (#104). The main chain starts only from its anchor: a build without one refuses to
 * start it (#148, ADR 0005). Every genesis block pays its reward to {@link #NOBODY}
 */
public final class Genesis
{

	/**
	 * The burn account every genesis block pays its reward to (#148, ADR 0005): words, not a key.
	 * Two things keep it from being spent, each enough alone. A consensus rule: a genesis block
	 * paying anybody else does not verify, and a transfer from this account is refused. And the
	 * bytes themselves: they decode as the public key of no signature suite, so no signature
	 * verifies for them ({@code NobodyIsNoKeyTest} checks every suite)
	 */
	public static final Bytes NOBODY = Bytes.of(
		"nobody holds the genesis reward of lethenon-2".getBytes(StandardCharsets.UTF_8));

	/** How many puns mining a genesis block tries before giving up */
	static final long ATTEMPTS = 10_000_000L;

	private Genesis()
	{
	}

	/**
	 * The first block of a new chain, under {@link ConsensusRules#LETHENON}
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @param pun
	 *            the words mining starts from; for the main chain, a headline of the day it starts
	 * @param now
	 *            the current time, milliseconds since the epoch
	 * @return the anchored genesis block, or a newly mined one for a test chain
	 * @throws IllegalStateException
	 *             when the main chain has no anchor in this build, or no pun reaches the genesis
	 *             difficulty
	 */
	public static BlockBody start(final String chainIdentifier, final String pun, final long now)
	{
		return start(ConsensusRules.LETHENON, chainIdentifier, pun, now);
	}

	/**
	 * The first block of a new chain under the given rule
	 */
	static BlockBody start(final ConsensusRules rules, final String chainIdentifier,
		final String pun, final long now)
	{
		Optional<BlockBody> anchored = rules.anchorFor(chainIdentifier);
		if (anchored.isPresent())
		{
			return anchored.get();
		}
		if (Chain.IDENTIFIER.equals(chainIdentifier))
		{
			throw new IllegalStateException("the main chain '" + Chain.IDENTIFIER
				+ "' starts only from the genesis block fixed in the code, and this build has none: "
				+ "the main chain starts with lethenon 1.0.0 (ADR 0005). A test chain starts with "
				+ "--testnet");
		}
		return candidate(chainIdentifier, pun, now);
	}

	/**
	 * Mines a genesis block, whether or not the chain has an anchor: what {@code lethenon genesis}
	 * prints for filing, and how a test chain starts
	 *
	 * @param chainIdentifier
	 *            the chain
	 * @param pun
	 *            the words mining starts from; for the main chain, a headline of the day it starts
	 * @param now
	 *            the current time, milliseconds since the epoch
	 * @return the mined block, paying {@link #NOBODY}
	 * @throws IllegalStateException
	 *             when no pun reaches the genesis difficulty
	 */
	public static BlockBody candidate(final String chainIdentifier, final String pun,
		final long now)
	{
		return Blocks.mine(Mining.nextBlock(chainIdentifier, List.of(), NOBODY, List.of(), pun,
			now), ATTEMPTS).orElseThrow(() -> new IllegalStateException(
				"no pun of " + ATTEMPTS + " attempts reached the genesis difficulty"));
	}

	/**
	 * What would be filed as the anchor of a genesis block: its canonical bytes in hexadecimal
	 *
	 * @param genesis
	 *            the block
	 * @return the anchor
	 */
	public static GenesisAnchor anchorOf(final BlockBody genesis)
	{
		return new GenesisAnchor(genesis.chainIdentifier(),
			Bytes.of(CanonicalEncoding.encodeChain(List.of(genesis))).toString());
	}
}
