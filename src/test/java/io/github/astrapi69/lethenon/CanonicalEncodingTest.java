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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * One encoder, and only one.
 * <p>
 * Two encoders that disagree by a byte produce a chain split: the same transaction hashes
 * differently on two machines, their signatures stop verifying for each other, and the network
 * forks over an implementation detail. So the encoding is written once, it carries its own version,
 * and these tests hold the property that makes it canonical - equal content, equal bytes.
 */
class CanonicalEncodingTest
{

	private static TransactionBody aTransfer()
	{
		return aTransferTo(Destination.direct(Bytes.of(new byte[] { 4, 5, 6 })));
	}

	private static TransactionBody aStealthTransfer()
	{
		return aTransferTo(new Destination(AddressScheme.STEALTH_V1, Bytes.of(new byte[] { 4, 5, 6 }),
			Bytes.of(new byte[] { 7, 8, 9 }), 200));
	}

	private static TransactionBody aTransferTo(final Destination recipient)
	{
		return new TransactionBody(Chain.IDENTIFIER, 7L, Bytes.of(new byte[] { 1, 2, 3 }),
			recipient, Amount.ofLeth(12L), Amount.ofLethe(500L), "no permanent record about people");
	}

	private static TransactionBody withRecipient(final TransactionBody body,
		final Destination recipient)
	{
		return new TransactionBody(body.chainIdentifier(), body.nonce(), body.sender(), recipient,
			body.amount(), body.fee(), body.memo());
	}

	@Test
	@DisplayName("what is encoded reads back as the same transaction")
	void transaction_roundTrips()
	{
		TransactionBody body = aTransfer();

		assertEquals(body, CanonicalEncoding.readTransaction(CanonicalEncoding.encode(body)));
	}

	@Test
	@DisplayName("a one-time destination reads back with its scheme, ephemeral key and view tag")
	void aStealthTransaction_roundTrips()
	{
		TransactionBody body = aStealthTransfer();

		assertEquals(body, CanonicalEncoding.readTransaction(CanonicalEncoding.encode(body)));
	}

	@Test
	@DisplayName("a destination whose scheme this build does not know is refused when read")
	void anUnknownScheme_isRefusedWhenRead()
	{
		// a direct destination under an unknown name: its shape is valid for every scheme a lenient
		// reader could fall back to, so only the name itself can be what is refused
		byte[] encoded = CanonicalEncoding.encode(aTransfer());
		String asText = new String(encoded, java.nio.charset.StandardCharsets.ISO_8859_1);
		byte[] renamed = asText.replace("direct", "dirext")
			.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

		IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
			() -> CanonicalEncoding.readTransaction(renamed),
			"funds sent under a scheme nobody here knows cannot be attributed to anybody");
		assertTrue(refusal.getMessage().contains("'dirext'"), refusal.getMessage());
	}

	@Test
	@DisplayName("equal transactions encode to identical bytes")
	void equalTransactions_encodeIdentically()
	{
		assertArrayEquals(CanonicalEncoding.encode(aTransfer()),
			CanonicalEncoding.encode(aTransfer()),
			"this is what canonical means, and what a signature over the bytes depends on");
	}

	@ParameterizedTest(name = "a different {0} changes the bytes")
	@ValueSource(strings = { "nonce", "recipient", "amount", "fee", "memo", "scheme",
			"ephemeral key", "view tag" })
	void aChangedField_changesTheBytes(final String field)
	{
		TransactionBody body = aStealthTransfer();
		Destination to = body.recipient();
		TransactionBody changed = switch (field)
		{
			case "nonce" -> new TransactionBody(body.chainIdentifier(), body.nonce() + 1,
				body.sender(), body.recipient(), body.amount(), body.fee(), body.memo());
			case "recipient" -> withRecipient(body, new Destination(to.scheme(),
				Bytes.of(new byte[] { 4, 5, 7 }), to.ephemeralKey(), to.viewTag()));
			case "scheme" -> withRecipient(body, Destination.direct(to.key()));
			case "ephemeral key" -> withRecipient(body, new Destination(to.scheme(), to.key(),
				Bytes.of(new byte[] { 7, 8, 8 }), to.viewTag()));
			case "view tag" -> withRecipient(body,
				new Destination(to.scheme(), to.key(), to.ephemeralKey(), to.viewTag() + 1));
			case "amount" -> new TransactionBody(body.chainIdentifier(), body.nonce(),
				body.sender(), body.recipient(), Amount.ofLeth(13L), body.fee(), body.memo());
			case "fee" -> new TransactionBody(body.chainIdentifier(), body.nonce(), body.sender(),
				body.recipient(), body.amount(), Amount.ofLethe(501L), body.memo());
			default -> new TransactionBody(body.chainIdentifier(), body.nonce(), body.sender(),
				body.recipient(), body.amount(), body.fee(), body.memo() + ".");
		};

		assertFalse(
			Arrays.equals(CanonicalEncoding.encode(body), CanonicalEncoding.encode(changed)),
			"a field that does not reach the bytes is a field nobody signed");
	}

	@Test
	@DisplayName("the encoding says which version wrote it, and refuses one it does not know")
	void theVersion_isCarriedAndChecked()
	{
		byte[] encoded = CanonicalEncoding.encode(aTransfer());

		assertEquals(CanonicalEncoding.VERSION, encoded[0],
			"the version is the first byte, so a reader knows before it parses");
		encoded[0] = (byte)(CanonicalEncoding.VERSION + 1);
		assertThrows(IllegalArgumentException.class,
			() -> CanonicalEncoding.readTransaction(encoded),
			"a build that cannot know what a newer version changed must not guess");
	}

	@Test
	@DisplayName("a memo keeps its text, umlauts and all")
	void aMemo_keepsItsText()
	{
		TransactionBody body = new TransactionBody(Chain.IDENTIFIER, 1L, Bytes.of(new byte[] { 9 }),
			Destination.direct(Bytes.of(new byte[] { 8 })), Amount.ZERO, Amount.ZERO,
			"Überwachung ist kein Schutz - Ärger für Späher");

		assertEquals(body.memo(),
			CanonicalEncoding.readTransaction(CanonicalEncoding.encode(body)).memo());
	}

	@Test
	@DisplayName("a memo longer than the limit is refused where it is built")
	void aMemo_beyondTheLimit_isRefused()
	{
		String tooLong = "x".repeat(TransactionBody.MEMO_LIMIT + 1);

		assertThrows(IllegalArgumentException.class,
			() -> new TransactionBody(Chain.IDENTIFIER, 1L, Bytes.of(new byte[] { 9 }),
				Destination.direct(Bytes.of(new byte[] { 8 })), Amount.ZERO, Amount.ZERO, tooLong),
			"a memo nobody bounded is a block size nobody bounded");
	}
}
