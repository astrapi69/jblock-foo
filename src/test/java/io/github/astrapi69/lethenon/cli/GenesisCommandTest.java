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

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ConsensusRules;
import io.github.astrapi69.lethenon.GenesisAnchor;

/**
 * lethenon genesis: a candidate genesis block's hash and canonical bytes, ready to be filed as an
 * anchor in the code (#104)
 */
class GenesisCommandTest extends AbstractCliTest
{

	@TempDir
	Path directory;

	@Test
	@DisplayName("genesis prints bytes that decode to the block whose hash it prints, a valid anchor")
	void genesis_printsAValidAnchor() throws Exception
	{
		String password = aPassword();
		String wallet = directory.resolve("holder.wallet").toString();
		assertEquals(0, run(password, "wallet", "create", "--wallet", wallet), err);
		String account = matchIn(ACCOUNT, out);
		long filesBefore;
		try (var listing = Files.list(directory))
		{
			filesBefore = listing.count();
		}

		assertEquals(0, run(password, "genesis", "--testnet", "--wallet", wallet), err);

		String hash = matchIn(java.util.regex.Pattern.compile("hash ([0-9a-f]+)"), out);
		String hex = matchIn(java.util.regex.Pattern.compile("anchor ([0-9a-f]+)"), out);
		List<BlockBody> decoded = CanonicalEncoding.readChain(Bytes.ofHex(hex).toByteArray());
		assertEquals(1, decoded.size());
		assertEquals(hash, Blocks.hashOf(decoded.getFirst()).toString());
		assertEquals(account, decoded.getFirst().beneficiary().toString());
		assertTrue(out.contains("chain " + Chain.TEST_IDENTIFIER), out);
		ConsensusRules anchored = new ConsensusRules(ConsensusRules.LETHENON.activations(),
			List.of(), List.of(new GenesisAnchor(Chain.TEST_IDENTIFIER, hex)));
		assertEquals(decoded.getFirst(),
			anchored.anchorFor(Chain.TEST_IDENTIFIER).orElseThrow());
		try (var listing = Files.list(directory))
		{
			assertEquals(filesBefore, listing.count(), "genesis writes nothing");
		}
	}
}
