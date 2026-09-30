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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a signature actually covers.
 * <p>
 * Never a bare hash: a signature over an unlabelled digest can be replayed wherever the same digest
 * means something else - another chain, another message type, a test network. So the bytes that get
 * signed start with a domain tag, name the chain and the type, and only then carry the digest of
 * the canonical encoding.
 */
class SigningPayloadTest
{

	private static TransactionBody aTransfer(final long nonce)
	{
		return new TransactionBody(Chain.IDENTIFIER, nonce, Bytes.of(new byte[] { 1 }),
			Bytes.of(new byte[] { 2 }), Amount.ofLeth(1L), Amount.ZERO, "for the record");
	}

	@Test
	@DisplayName("the payload starts with the domain tag and names the chain and the type")
	void thePayload_isLabelled()
	{
		byte[] payload = SigningPayload.of(aTransfer(1L));
		String readable = new String(payload, StandardCharsets.UTF_8);

		assertTrue(readable.startsWith(SigningPayload.DOMAIN_TAG),
			"the tag is what keeps this signature from meaning anything elsewhere: " + readable);
		assertTrue(readable.contains(Chain.IDENTIFIER),
			"and the chain identifier is what keeps it off another network");
		assertTrue(readable.contains(SigningPayload.TYPE_TRANSFER), "and the type is named");
	}

	@Test
	@DisplayName("the same transaction always gives the same bytes to sign")
	void thePayload_isStable()
	{
		assertArrayEquals(SigningPayload.of(aTransfer(1L)), SigningPayload.of(aTransfer(1L)));
	}

	@Test
	@DisplayName("a different transaction gives different bytes")
	void aDifferentTransaction_givesADifferentPayload()
	{
		assertFalse(
			Arrays.equals(SigningPayload.of(aTransfer(1L)), SigningPayload.of(aTransfer(2L))),
			"otherwise one signature would pay twice");
	}

	@Test
	@DisplayName("the payload carries a digest of the encoding, not the encoding itself")
	void thePayload_carriesADigest()
	{
		byte[] payload = SigningPayload.of(aTransfer(1L));

		assertEquals(SigningPayload.DOMAIN_TAG.length() + Chain.IDENTIFIER.length()
			+ SigningPayload.TYPE_TRANSFER.length() + 3 + 32, payload.length,
			"tag, chain, type, three separators and a SHA-256 digest - a fixed length, whatever "
				+ "the memo says");
	}
}
