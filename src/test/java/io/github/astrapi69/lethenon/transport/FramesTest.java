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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One message is one frame: a 4-byte length, a type byte, the payload (ADR 0003). The length is
 * checked before anything is allocated, so a peer cannot make a node reserve memory by announcing
 * a large frame.
 */
class FramesTest
{

	@Test
	@DisplayName("a frame written is the frame read")
	void aFrame_roundTrips() throws IOException
	{
		byte[] payload = { 1, 2, 3, 4 };

		Frame read = Frames.read(input(written(new Frame(MessageType.BLOCK, payload))), 64);

		assertEquals(MessageType.BLOCK, read.type());
		assertArrayEquals(payload, read.payload());
	}

	@Test
	@DisplayName("a frame announcing more than the limit is refused before its payload is read")
	void aFrameAboveTheLimit_isRefusedBeforeReading()
	{
		// the announced length is huge and no payload follows: a reader that allocated or waited
		// for the payload would fail differently
		byte[] announcement = ByteBuffer.allocate(5).putInt(Integer.MAX_VALUE)
			.put(MessageType.BLOCK.code()).array();

		ProtocolViolation refused = assertThrows(ProtocolViolation.class,
			() -> Frames.read(input(announcement), Frames.MAXIMUM_FRAME));

		assertTrue(refused.getMessage().contains(String.valueOf(Integer.MAX_VALUE)),
			refused.getMessage());
	}

	static Stream<Arguments> framesThatAreNoFrames()
	{
		return Stream.of(
			Arguments.of("a length of zero, which leaves no room for the type",
				ByteBuffer.allocate(4).putInt(0).array()),
			Arguments.of("a negative length", ByteBuffer.allocate(4).putInt(-1).array()),
			Arguments.of("a length one above the limit, with nothing after it",
				ByteBuffer.allocate(5).putInt(65).put(MessageType.BLOCK.code()).array()),
			Arguments.of("an unknown message type",
				ByteBuffer.allocate(5).putInt(1).put((byte)99).array()));
	}

	@ParameterizedTest(name = "{0} is refused")
	@MethodSource("framesThatAreNoFrames")
	void aFrameThatIsNoFrame_isRefused(final String description, final byte[] bytes)
	{
		assertThrows(ProtocolViolation.class, () -> Frames.read(input(bytes), 64), description);
	}

	@Test
	@DisplayName("a stream that ends inside a frame is an end of stream, not a frame")
	void aTruncatedFrame_isAnEndOfStream()
	{
		byte[] truncated = ByteBuffer.allocate(7).putInt(10).put(MessageType.BLOCK.code())
			.put(new byte[] { 1, 2 }).array();

		assertThrows(EOFException.class, () -> Frames.read(input(truncated), 64));
	}

	@Test
	@DisplayName("writing a frame above the limit is refused, not sent")
	void writingAboveTheLimit_isRefused()
	{
		Frame tooLarge = new Frame(MessageType.BLOCKS, new byte[Frames.MAXIMUM_FRAME]);

		assertThrows(ProtocolViolation.class,
			() -> Frames.write(new DataOutputStream(new ByteArrayOutputStream()), tooLarge));
	}

	private static byte[] written(final Frame frame) throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Frames.write(new DataOutputStream(bytes), frame);
		return bytes.toByteArray();
	}

	private static DataInputStream input(final byte[] bytes)
	{
		return new DataInputStream(new ByteArrayInputStream(bytes));
	}
}
