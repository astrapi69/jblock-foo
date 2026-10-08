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
 * How a chain starts: with the genesis block fixed in the code for it, or, for a chain that has
 * none, with one mined for its holder (#104)
 */
public final class Genesis
{

	/**
	 * The beneficiary of the anchored genesis block of {@code lethenon-2}: words, not a key. No
	 * signature verifies for a sender that does not decode as a public key of any suite, so the
	 * genesis reward is paid out of the pool like every reward and can never be spent (#137,
	 * ADR 0005)
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
	 * @param holder
	 *            whom a mined genesis block allocates to; unused for an anchored chain
	 * @param pun
	 *            the words mining starts from
	 * @param now
	 *            the current time, milliseconds since the epoch
	 * @return the anchored genesis block, or a newly mined one
	 * @throws IllegalStateException
	 *             when no pun reaches the genesis difficulty
	 */
	public static BlockBody start(final String chainIdentifier, final Bytes holder,
		final String pun, final long now)
	{
		return start(ConsensusRules.LETHENON, chainIdentifier, holder, pun, now);
	}

	/**
	 * The first block of a new chain under the given rule
	 */
	static BlockBody start(final ConsensusRules rules, final String chainIdentifier,
		final Bytes holder, final String pun, final long now)
	{
		Optional<BlockBody> anchored = rules.anchorFor(chainIdentifier);
		if (anchored.isPresent())
		{
			return anchored.get();
		}
		return Blocks.mine(Mining.nextBlock(chainIdentifier, List.of(), holder, List.of(), pun,
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
