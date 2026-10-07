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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Bytes that come from a peer are untrusted (#80): a length prefix must not decide how much memory
 * the decoder reserves, and bytes that end early must be refused as such. Every case is refused
 * with an IllegalArgumentException, which is how the decoder already refuses an unknown version.
 */
class UntrustedBytesTest
{

	private static final byte[] A_SIGNED_TRANSFER = CanonicalEncoding.encode(TransactionSigner.sign(
		new TransactionBody(Chain.TEST_IDENTIFIER, 0L, Bytes.of(new byte[] { 1 }),
			Destination.direct(Bytes.of(new byte[] { 2 })), Amount.ofLeth(1L), Amount.ZERO,
			"untrusted"),
		SignatureSuite.ED25519, TransactionSigner.newKeyPair(SignatureSuite.ED25519).getPrivate()));

	static Stream<Arguments> bytesThatLie()
	{
		return Stream.of(
			Arguments.of("a negative length prefix", prefixed(-1, 8)),
			Arguments.of("a length prefix larger than the bytes left", prefixed(1_000_000, 8)),
			Arguments.of("a length prefix of the largest int", prefixed(Integer.MAX_VALUE, 8)),
			Arguments.of("bytes that end in the middle",
				Arrays.copyOf(A_SIGNED_TRANSFER, A_SIGNED_TRANSFER.length / 2)),
			Arguments.of("no bytes at all", new byte[0]));
	}

	static Stream<Arguments> readers()
	{
		return Stream.of(
			Arguments.of("a signed transfer",
				(Function<byte[], Object>)CanonicalEncoding::readSignedTransaction),
			Arguments.of("a transfer body",
				(Function<byte[], Object>)CanonicalEncoding::readTransaction),
			Arguments.of("a chain", (Function<byte[], Object>)CanonicalEncoding::readChain));
	}

	static Stream<Arguments> everyReaderAndEveryLie()
	{
		return readers().flatMap(reader -> bytesThatLie().map(lie -> Arguments.of(reader.get()[0],
			reader.get()[1], lie.get()[0], lie.get()[1])));
	}

	@ParameterizedTest(name = "{0} from {2} is refused")
	@MethodSource("everyReaderAndEveryLie")
	void bytesThatLie_areRefused(final String what, final Function<byte[], Object> reader,
		final String lie, final byte[] bytes)
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> reader.apply(bytes), what + " from " + lie);

		assertTrue(refused.getMessage() != null && !refused.getMessage().isBlank(),
			"the refusal says why");
	}

	@ParameterizedTest(name = "a length of {0} is named in the refusal")
	@MethodSource("lengths")
	void theRefusal_namesTheAnnouncedLength(final int announced)
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> CanonicalEncoding.readSignedTransaction(prefixed(announced, 8)));

		assertTrue(refused.getMessage().contains(String.valueOf(announced)), refused.getMessage());
	}

	static List<Integer> lengths()
	{
		return List.of(-1, 1_000_000, Integer.MAX_VALUE);
	}

	/**
	 * A length prefix followed by a few bytes; for a chain the first byte is taken as the version,
	 * so the chain readers see the version byte and then a count
	 */
	private static byte[] prefixed(final int length, final int following)
	{
		return ByteBuffer.allocate(Integer.BYTES + following).putInt(length).array();
	}
}
