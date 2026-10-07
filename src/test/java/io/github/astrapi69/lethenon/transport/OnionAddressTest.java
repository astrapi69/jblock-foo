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
package io.github.astrapi69.lethenon.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A peer address recognises a v3 onion address, 56 base32 characters before {@code .onion}, and
 * refuses every other name that ends in {@code .onion} (#114)
 */
class OnionAddressTest
{

	@Test
	@DisplayName("a v3 onion address is an onion address, whatever its case")
	void aV3OnionAddress_isRecognised()
	{
		assertTrue(PeerAddress.parse(ProxyTest.ONION + ":18480").isOnion());
		assertTrue(PeerAddress.parse(ProxyTest.ONION.toUpperCase(java.util.Locale.ROOT) + ":18480")
			.isOnion());
	}

	@Test
	@DisplayName("an ordinary host is not an onion address")
	void anOrdinaryHost_isNotAnOnionAddress()
	{
		assertFalse(PeerAddress.parse("127.0.0.1:18480").isOnion());
		assertFalse(PeerAddress.parse("onion.example.org:18480").isOnion());
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource({ "lethenonlethenon.onion, a v2 address of 16 characters",
		"lethenonlethenonlethenonlethenonlethenonlethenonlethenn.onion, 55 characters",
		"lethenonlethenonlethenonlethenonlethenonlethenonlethenonl.onion, 57 characters",
		"lethenonlethenonlethenonlethenonlethenonlethenonlethen01.onion, digits outside base32",
		"lethenon-ethenonlethenonlethenonlethenonlethenonlethenon.onion, a hyphen",
		".onion, nothing before the suffix" })
	@DisplayName("a name that ends in .onion and is not a v3 address is refused")
	void aNameThatIsNotAV3OnionAddress_isRefused(final String host, final String why)
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> new PeerAddress(host, 18480));

		assertTrue(refused.getMessage().contains("56"), refused.getMessage());
	}

	@Test
	@DisplayName("a parsed onion address keeps its host and port")
	void aParsedOnionAddress_keepsHostAndPort()
	{
		PeerAddress address = PeerAddress.parse(ProxyTest.ONION + ":18484");

		assertEquals(ProxyTest.ONION, address.host());
		assertEquals(18484, address.port());
	}
}
