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

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.Bytes;
import io.github.astrapi69.lethenon.ChainWork;

/**
 * The handshake message: who a node is and where its chain stands (ADR 0003). Monero's handshake
 * carries a network id and the peer's height, cumulative difficulty and top block id; this one
 * carries the chain identifier and the genesis hash in place of the network id.
 *
 * @param protocolVersion
 *            the version of this protocol the node speaks
 * @param chainIdentifier
 *            the chain the node runs, which has to be the test chain
 * @param genesisHash
 *            the hash of its genesis block
 * @param bestHeight
 *            the height of its tip
 * @param bestHash
 *            the hash of its tip
 * @param work
 *            the cumulative work of its chain ({@link ChainWork})
 */
public record Hello(int protocolVersion, String chainIdentifier, Bytes genesisHash,
	long bestHeight, Bytes bestHash, BigInteger work)
{

	/** The bytes every HELLO starts with */
	public static final String MAGIC = "LETHENON";

	/** The protocol this build speaks */
	public static final int PROTOCOL_VERSION = 1;

	private static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

	/**
	 * The HELLO a node on the given chain sends
	 *
	 * @param chain
	 *            the chain, genesis first, not empty
	 * @return the message
	 */
	public static Hello of(final List<BlockBody> chain)
	{
		BlockBody tip = chain.getLast();
		return new Hello(PROTOCOL_VERSION, chain.getFirst().chainIdentifier(),
			Blocks.hashOf(chain.getFirst()), tip.height(), Blocks.hashOf(tip), ChainWork.of(chain));
	}

	/**
	 * The payload of a HELLO frame
	 *
	 * @return the bytes
	 */
	public byte[] encode()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(MAGIC_BYTES);
		Wire.writeInt(out, protocolVersion);
		Wire.writeText(out, chainIdentifier);
		Wire.writeBytes(out, genesisHash.toByteArray());
		Wire.writeLong(out, bestHeight);
		Wire.writeBytes(out, bestHash.toByteArray());
		Wire.writeBytes(out, work.toByteArray());
		return out.toByteArray();
	}

	/**
	 * Reads the payload of a HELLO frame
	 *
	 * @param payload
	 *            the bytes
	 * @return the message
	 * @throws ProtocolViolation
	 *             when the magic is missing or the bytes do not decode exactly
	 */
	public static Hello decode(final byte[] payload) throws ProtocolViolation
	{
		ByteBuffer in = ByteBuffer.wrap(payload);
		if (payload.length < MAGIC_BYTES.length
			|| !Arrays.equals(Arrays.copyOf(payload, MAGIC_BYTES.length), MAGIC_BYTES))
		{
			throw new ProtocolViolation("a HELLO has to start with " + MAGIC);
		}
		in.position(MAGIC_BYTES.length);
		int version = Wire.readInt(in);
		String chain = Wire.readText(in, 64, "the chain identifier");
		Bytes genesis = Wire.readHash(in, "the genesis hash");
		long height = Wire.readLong(in);
		Bytes best = Wire.readHash(in, "the best hash");
		BigInteger work = new BigInteger(1, Wire.readBytes(in, 64, "the work"));
		Wire.requireEnd(in, "a HELLO");
		return new Hello(version, chain, genesis, height, best, work);
	}
}
