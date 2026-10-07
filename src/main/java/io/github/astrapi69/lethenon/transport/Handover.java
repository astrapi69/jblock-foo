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
import java.util.List;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.SignedTransaction;

/**
 * Hands one signed transfer to a running node: a handshake, one {@code TRANSFER} frame, and a
 * close in order ({@link Client#closeInOrder()})
 * <p>
 * When it returns, the node has handled the transfer: admitted it and written its pool, or refused
 * it and recorded why. A caller can then look at the node's pool file and know.
 */
public final class Handover
{

	private Handover()
	{
	}

	/**
	 * Hands a transfer to the node at the given address
	 *
	 * @param address
	 *            the node
	 * @param chain
	 *            the caller's chain, for the HELLO; its chain identifier and genesis block have
	 *            to be the node's
	 * @param transfer
	 *            the signed transfer
	 * @throws IOException
	 *             when the node cannot be reached, is on another chain or genesis block, or does
	 *             not close within {@link Node#HANDSHAKE_MILLIS}
	 */
	public static void send(final PeerAddress address, final List<BlockBody> chain,
		final SignedTransaction transfer) throws IOException
	{
		try
		{
			handOver(address, chain, transfer);
		}
		catch (IOException failed)
		{
			throw new IOException("handing the transfer to the node at " + address + " failed: "
				+ failed.getMessage(), failed);
		}
	}

	private static void handOver(final PeerAddress address, final List<BlockBody> chain,
		final SignedTransaction transfer) throws IOException
	{
		try (Client node = Client.open(address))
		{
			Hello ours = Hello.of(chain);
			requireSameChain(ours, node.theirs());
			node.send(new Frame(MessageType.HELLO, ours.encode()));
			node.send(new Frame(MessageType.TRANSFER, CanonicalEncoding.encode(transfer)));
			node.closeInOrder();
		}
	}

	private static void requireSameChain(final Hello ours, final Hello theirs) throws IOException
	{
		if (!ours.chainIdentifier().equals(theirs.chainIdentifier()))
		{
			throw new IOException("it is on chain '" + theirs.chainIdentifier()
				+ "', and this chain file on '" + ours.chainIdentifier() + "'");
		}
		if (!ours.genesisHash().equals(theirs.genesisHash()))
		{
			throw new IOException("it has the genesis block " + theirs.genesisHash()
				+ ", and this chain file " + ours.genesisHash());
		}
	}
}
