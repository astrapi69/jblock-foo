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

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Blocks;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.Chain;
import io.github.astrapi69.lethenon.ChainRejected;
import io.github.astrapi69.lethenon.Replay;

/**
 * The genesis block for a node with an empty chain file, taken from a peer (ADR 0003, the node
 * command)
 * <p>
 * Trust on first use: the block is the one the first peer that answers announces in its HELLO.
 * It is checked to hash to that announcement, to be a genesis block of {@link Chain#TEST_IDENTIFIER}
 * and to verify; what the peer chose as its genesis block cannot be checked against anything else.
 * A node that should not trust its peers for that starts from a chain file that has the block.
 */
public final class Bootstrap
{

	private Bootstrap()
	{
	}

	/**
	 * Takes the genesis block from the node at the given address
	 *
	 * @param address
	 *            the peer
	 * @return a chain of the genesis block alone
	 * @throws IOException
	 *             when the peer cannot be reached, is not on the test chain, or sends a block that
	 *             is not the genesis block it announced
	 */
	public static List<BlockBody> genesisFrom(final PeerAddress address) throws IOException
	{
		try (Client peer = Client.open(address))
		{
			Hello theirs = peer.theirs();
			if (!Chain.TEST_IDENTIFIER.equals(theirs.chainIdentifier()))
			{
				throw new IOException("the peer at " + address + " is on chain '"
					+ theirs.chainIdentifier() + "', and a node runs only on '"
					+ Chain.TEST_IDENTIFIER + "'");
			}
			peer.send(new Frame(MessageType.HELLO, new Hello(Hello.PROTOCOL_VERSION,
				theirs.chainIdentifier(), theirs.genesisHash(), 0L, theirs.genesisHash(),
				BigInteger.ZERO).encode()));
			peer.send(new Frame(MessageType.GET_BLOCKS, new BlockRequest(0L, 1).encode()));
			List<BlockBody> genesis = checked(address, theirs, blocksFrom(peer));
			peer.closeInOrder();
			return genesis;
		}
	}

	/**
	 * Reads until BLOCKS arrives; a block or transfer the peer relays meanwhile is passed over
	 */
	private static List<BlockBody> blocksFrom(final Client peer) throws IOException
	{
		Frame frame = peer.read();
		while (frame.type() != MessageType.BLOCKS)
		{
			frame = peer.read();
		}
		try
		{
			return CanonicalEncoding.readChain(frame.payload());
		}
		catch (IllegalArgumentException undecodable)
		{
			throw new ProtocolViolation("bytes that do not decode as blocks: "
				+ undecodable.getMessage());
		}
	}

	private static List<BlockBody> checked(final PeerAddress address, final Hello theirs,
		final List<BlockBody> blocks) throws IOException
	{
		if (blocks.size() != 1 || !Blocks.hashOf(blocks.getFirst()).equals(theirs.genesisHash()))
		{
			throw new IOException("block 0 from the peer at " + address + " does not hash to the "
				+ "genesis block " + theirs.genesisHash() + " its HELLO announced");
		}
		BlockBody genesis = blocks.getFirst();
		if (genesis.height() != 0L || !Chain.TEST_IDENTIFIER.equals(genesis.chainIdentifier()))
		{
			throw new IOException("the peer at " + address + " sent block " + genesis.height()
				+ " of chain '" + genesis.chainIdentifier() + "' as its genesis block");
		}
		try
		{
			Replay.verify(blocks);
		}
		catch (ChainRejected rejected)
		{
			throw new IOException("the genesis block from the peer at " + address
				+ " does not verify: " + rejected.getMessage());
		}
		return blocks;
	}
}
