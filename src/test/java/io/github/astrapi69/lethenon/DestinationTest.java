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

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The shape of a one-time destination, as decided in lethenon#2: a scheme named on the wire, the
 * key the funds are locked to, the sender's ephemeral key and a one-byte view tag
 */
class DestinationTest
{

	private static final Bytes KEY = Bytes.of(new byte[] { 4, 5, 6 });

	private static final Bytes EPHEMERAL = Bytes.of(new byte[] { 7, 8, 9 });

	private static final Bytes NONE = Bytes.of(new byte[0]);

	@Test
	@DisplayName("a direct destination is the key alone")
	void direct_isTheKeyAlone()
	{
		Destination destination = Destination.direct(KEY);

		assertEquals(AddressScheme.DIRECT, destination.scheme());
		assertEquals(KEY, destination.key());
		assertEquals(NONE, destination.ephemeralKey());
		assertEquals(0, destination.viewTag());
	}

	static Stream<Arguments> shapesThatAreRefused()
	{
		return Stream.of(//
			Arguments.of("direct with an ephemeral key", AddressScheme.DIRECT, KEY, EPHEMERAL, 0),
			Arguments.of("direct with a view tag", AddressScheme.DIRECT, KEY, NONE, 1),
			Arguments.of("stealth without an ephemeral key", AddressScheme.STEALTH_V1, KEY, NONE,
				0),
			Arguments.of("any scheme without a key", AddressScheme.DIRECT, NONE, NONE, 0),
			Arguments.of("a view tag below one byte", AddressScheme.STEALTH_V1, KEY, EPHEMERAL, -1),
			Arguments.of("a view tag above one byte", AddressScheme.STEALTH_V1, KEY, EPHEMERAL,
				256));
	}

	@ParameterizedTest(name = "{0} is refused")
	@MethodSource("shapesThatAreRefused")
	void aShapeTheSchemeDoesNotAllow_isRefused(final String caseName, final AddressScheme scheme,
		final Bytes key, final Bytes ephemeralKey, final int viewTag)
	{
		assertThrows(IllegalArgumentException.class,
			() -> new Destination(scheme, key, ephemeralKey, viewTag), caseName);
	}

	@ParameterizedTest(name = "view tag {0} is one byte")
	@ValueSource(ints = { 0, 1, 254, 255 })
	void everyViewTagOfOneByte_isAccepted(final int viewTag)
	{
		assertEquals(viewTag,
			new Destination(AddressScheme.STEALTH_V1, KEY, EPHEMERAL, viewTag).viewTag());
	}

	@Test
	@DisplayName("a scheme is found by its wire identifier, and an unknown one is refused")
	void aScheme_isFoundByItsIdentifier_andAnUnknownOneIsRefused()
	{
		assertEquals(AddressScheme.DIRECT, AddressScheme.withIdentifier("direct"));
		assertEquals(AddressScheme.STEALTH_V1, AddressScheme.withIdentifier("stealth-v1"),
			"stealth-v1 is still READ - a file that carries one has to be nameable - and nothing "
				+ "produces a new one (#21)");
		assertEquals(AddressScheme.STEALTH_V2, AddressScheme.withIdentifier("stealth-v2"));
		assertThrows(IllegalArgumentException.class,
			() -> AddressScheme.withIdentifier("stealth-v3"),
			"a build that does not know a scheme cannot tell whose the funds are");
	}
}
