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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The milestone's acceptance, against files rather than against objects this test just built.
 * <p>
 * The chain was written once by a process that is gone, and this one is given nothing but the
 * bytes: no keys to trust, no state handed over, no server asked. That is the property the whole
 * project is about - verification without an observer.
 * <p>
 * And the half that makes the other half mean something: the corrupted file, one bit flipped in a
 * signature, has to be refused. A verifier that has never rejected anything is untested.
 */
class ChainFixtureTest
{

	private static byte[] resource(final String name) throws IOException
	{
		try (InputStream stream = ChainFixtureTest.class.getResourceAsStream("/chain/" + name))
		{
			assertNotNull(stream, name + " is on the test class path");
			return stream.readAllBytes();
		}
	}

	@Test
	@DisplayName("the chain file replays, and every balance adds up to the supply")
	void theChainFile_replays() throws IOException
	{
		List<BlockBody> chain = CanonicalEncoding.readChain(resource("chain.lethenon"));
		Bytes holder = Bytes.of(resource("holder.key"));
		Bytes miner = Bytes.of(resource("miner.key"));

		Replay replay = Replay.verify(chain, List.of(miner, miner), holder);

		assertEquals(2L, replay.blocks());
		assertEquals(1L, replay.transactions());
		assertEquals(Emission.TOTAL_SUPPLY, replay.finalState().total());
		assertEquals(Amount.ofLeth(42L).plus(Emission.BLOCK_REWARD),
			replay.finalState().balanceOf(miner),
			"the miner holds what was transferred to it plus one block reward");
	}

	@Test
	@DisplayName("the corrupted chain file is refused, and the reason says what failed")
	void theCorruptedFile_isRefused() throws IOException
	{
		List<BlockBody> chain = CanonicalEncoding.readChain(resource("chain-corrupted.lethenon"));
		Bytes holder = Bytes.of(resource("holder.key"));
		Bytes miner = Bytes.of(resource("miner.key"));

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, List.of(miner, miner), holder));

		assertTrue(refused.getMessage().contains("signature"), refused.getMessage());
	}
}
