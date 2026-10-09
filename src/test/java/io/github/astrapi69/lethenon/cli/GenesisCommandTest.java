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
package io.github.astrapi69.lethenon.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ConsensusRules;
import io.github.astrapi69.lethenon.Genesis;
import io.github.astrapi69.lethenon.GenesisAnchor;

/**
 * lethenon genesis: a candidate genesis block's hash and canonical bytes, ready to be filed as an
 * anchor in the code (#104). Its words are a headline of the day and its reward goes to
 * {@link Genesis#NOBODY} (#148). It mines the main chain's candidate too, although this build
 * starts no main chain: that is what the start day runs (ADR 0005)
 */
class GenesisCommandTest extends AbstractCliTest
{

	@TempDir
	Path directory;

	@ParameterizedTest(name = "on {0}")
	@ValueSource(strings = { Chain.IDENTIFIER, Chain.TEST_IDENTIFIER })
	@DisplayName("genesis prints bytes that decode to the block whose hash it prints, a valid anchor")
	void genesis_printsAValidAnchor(final String chain) throws Exception
	{
		long filesBefore = filesIn(directory);
		String[] arguments = Chain.TEST_IDENTIFIER.equals(chain)
			? new String[] { "genesis", "--testnet", "--headline", "  a headline of the day " }
			: new String[] { "genesis", "--headline", "  a headline of the day " };

		assertEquals(0, run("", arguments), err);

		String hash = matchIn(java.util.regex.Pattern.compile("hash ([0-9a-f]+)"), out);
		String hex = matchIn(java.util.regex.Pattern.compile("anchor ([0-9a-f]+)"), out);
		List<BlockBody> decoded = CanonicalEncoding.readChain(Bytes.ofHex(hex).toByteArray());
		assertEquals(1, decoded.size());
		BlockBody genesis = decoded.getFirst();
		assertEquals(hash, Blocks.hashOf(genesis).toString());
		assertEquals(Genesis.NOBODY, genesis.beneficiary());
		assertTrue(genesis.pun().startsWith("a headline of the day"), genesis.pun());
		assertTrue(out.contains("chain " + chain), out);
		ConsensusRules anchored = new ConsensusRules(ConsensusRules.LETHENON.activations(),
			List.of(), List.of(new GenesisAnchor(chain, hex)));
		assertEquals(genesis, anchored.anchorFor(chain).orElseThrow());
		assertEquals(filesBefore, filesIn(directory), "genesis writes nothing");
	}

	@Test
	@DisplayName("genesis without a headline is a usage error, a blank headline an error")
	void genesis_withoutAHeadline_isRefused()
	{
		assertEquals(2, run("", "genesis", "--testnet"));
		assertTrue(err.contains("--headline"), err);

		assertEquals(1, run("", "genesis", "--testnet", "--headline", "   "));
		assertTrue(err.contains("headline is empty"), err);
	}

	private static long filesIn(final Path directory) throws java.io.IOException
	{
		try (var listing = Files.list(directory))
		{
			return listing.count();
		}
	}
}
