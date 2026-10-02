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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The published address as text, because a sender has to be able to receive it: it travels out of
 * band, the way a bank account number does, so what one command prints has to be what another
 * command takes (#37).
 */
class PublishedAddressTest
{

	private final PublishedAddress address = PublishedAddress.of(
		OneTimeAddresses.newEphemeralKeyPair(),
		TransactionSigner.newKeyPair(SignatureSuite.ED25519));

	@Test
	@DisplayName("an address survives being written down and typed back in")
	void anAddress_survivesItsTextForm()
	{
		String text = address.toText();

		assertEquals(address, PublishedAddress.parse(text));
		assertTrue(text.contains(PublishedAddress.SEPARATOR),
			"the two keys are one string with one separator: " + text);
		assertEquals(2, text.split(PublishedAddress.SEPARATOR).length);
	}

	@Test
	@DisplayName("the text is the two keys in the order they are named, view first")
	void theText_namesTheViewKeyFirst()
	{
		String text = address.toText();

		assertTrue(text.startsWith(address.viewKey().toString()), text);
		assertTrue(text.endsWith(address.spendKey().toString()), text);
	}

	@ParameterizedTest(name = "\"{0}\" is not an address")
	@ValueSource(strings = { "", ":", "deadbeef", "deadbeef:", ":deadbeef", "dead:beef:cafe",
			"nothex:nothex" })
	@DisplayName("what is not an address is refused, rather than becoming one nobody can be paid at")
	void whatIsNotAnAddress_isRefused(final String text)
	{
		assertThrows(IllegalArgumentException.class, () -> PublishedAddress.parse(text));
	}

	@Test
	@DisplayName("an address whose keys are not keys is refused when it is used, with its reason")
	void anAddressOfWrongKeys_isRefusedWhereItIsUsed()
	{
		PublishedAddress nonsense = PublishedAddress
			.parse(Bytes.of(new byte[] { 1, 2, 3 }) + PublishedAddress.SEPARATOR
				+ Bytes.of(new byte[] { 4, 5, 6 }));

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> OneTimeAddresses.destinationFor(nonsense,
				OneTimeAddresses.newEphemeralKeyPair()));

		assertTrue(refused.getMessage().contains("public key"), refused.getMessage());
	}
}
