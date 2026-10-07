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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.astrapi69.lethenon.Bytes;

/**
 * The payloads of GET_CHAIN, CHAIN and GET_BLOCKS: their shape, and the bounds a peer cannot
 * exceed
 */
class SyncMessagesTest
{

	private static List<Bytes> hashes(final long count)
	{
		return LongStream.range(0, count)
			.mapToObj(height -> Bytes.of(ByteBuffer.allocate(Long.BYTES).putLong(height).array()))
			.toList();
	}

	private static long heightOf(final Bytes hash)
	{
		return ByteBuffer.wrap(hash.toByteArray()).getLong();
	}

	@ParameterizedTest(name = "a chain of {0} blocks")
	@ValueSource(longs = { 1, 2, 11, 12, 1_000, 1_000_000 })
	void aLocator_startsAtTheTip_endsAtGenesis_andFitsItsLimit(final long blocks)
	{
		Locator locator = Locator.of(hashes(blocks));
		List<Long> heights = locator.hashes().stream().map(SyncMessagesTest::heightOf).toList();

		assertEquals(blocks - 1, heights.getFirst(), "the tip first");
		assertEquals(0L, heights.getLast(), "the genesis block last");
		assertTrue(heights.size() <= Locator.LIMIT, heights.size() + " hashes");
		for (int index = 1; index < heights.size(); index++)
		{
			assertTrue(heights.get(index) < heights.get(index - 1), heights.toString());
		}
	}

	@Test
	@DisplayName("a locator names the ten newest blocks one by one, then doubles its steps")
	void aLocator_namesTheTenNewest_thenDoublesItsSteps()
	{
		List<Long> heights = Locator.of(hashes(100)).hashes().stream()
			.map(SyncMessagesTest::heightOf).toList();

		assertEquals(List.of(99L, 98L, 97L, 96L, 95L, 94L, 93L, 92L, 91L, 90L, 88L, 84L, 76L, 60L,
			28L, 0L), heights);
	}

	@Test
	@DisplayName("the three payloads survive their own encoding")
	void thePayloads_roundTrip() throws ProtocolViolation
	{
		Locator locator = Locator.of(hashes(40));
		ChainEntry entry = new ChainEntry(7L, hashes(ChainEntry.LIMIT));
		BlockRequest request = new BlockRequest(12L, BlockRequest.LIMIT);

		assertEquals(locator, Locator.decode(locator.encode()));
		assertEquals(entry, ChainEntry.decode(entry.encode()));
		assertEquals(request, BlockRequest.decode(request.encode()));
	}

	static Stream<Arguments> payloadsOutOfBounds()
	{
		List<Bytes> tooMany = new ArrayList<>(hashes(ChainEntry.LIMIT + 1));
		return Stream.of(
			Arguments.of("a locator of no hashes", MessageType.GET_CHAIN, countThen(0)),
			Arguments.of("a locator of one hash more than the limit", MessageType.GET_CHAIN,
				countThen(Locator.LIMIT + 1)),
			Arguments.of("a CHAIN answer of one hash more than the limit", MessageType.CHAIN,
				chainEntryOf(tooMany)),
			Arguments.of("GET_BLOCKS for no block", MessageType.GET_BLOCKS,
				new BlockRequest(0L, 0).encode()),
			Arguments.of("GET_BLOCKS from a negative height", MessageType.GET_BLOCKS,
				new BlockRequest(-1L, 1).encode()),
			Arguments.of("GET_BLOCKS for one block more than a batch", MessageType.GET_BLOCKS,
				new BlockRequest(0L, BlockRequest.LIMIT + 1).encode()));
	}

	private static byte[] countThen(final int count)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Wire.writeInt(out, count);
		return out.toByteArray();
	}

	private static byte[] chainEntryOf(final List<Bytes> hashes)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Wire.writeLong(out, 0L);
		Wire.writeInt(out, hashes.size());
		hashes.forEach(hash -> Wire.writeBytes(out, hash.toByteArray()));
		return out.toByteArray();
	}

	@ParameterizedTest(name = "{0} is refused")
	@MethodSource("payloadsOutOfBounds")
	void aPayloadOutOfBounds_isRefused(final String description, final MessageType type,
		final byte[] payload)
	{
		assertThrows(ProtocolViolation.class, () -> {
			switch (type)
			{
				case GET_CHAIN -> Locator.decode(payload);
				case CHAIN -> ChainEntry.decode(payload);
				default -> BlockRequest.decode(payload);
			}
		});
	}
}
