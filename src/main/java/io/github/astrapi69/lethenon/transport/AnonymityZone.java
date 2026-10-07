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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.astrapi69.lethenon.CanonicalEncoding;
import io.github.astrapi69.lethenon.SignedTransaction;

/**
 * The peers a node reaches through Tor for its own transfers, after Monero's {@code --tx-proxy}
 * (ADR 0004, step 2, #116): a transfer that originates on the node goes only to them, or waits for
 * one, and never goes out in the clear. The zone carries transfers and nothing else, and has a node
 * identity of its own.
 */
final class AnonymityZone
{

	private final Outbound route;

	private final int maximum;

	private final long nodeId;

	private final List<Peer> peers = new CopyOnWriteArrayList<>();

	private final AtomicInteger connections = new AtomicInteger();

	private final List<SignedTransaction> waiting = new ArrayList<>();

	private final Set<PeerAddress> handshaking = ConcurrentHashMap.newKeySet();

	private volatile Discovery discovery;

	AnonymityZone(final Outbound route, final int maximum, final long nodeId)
	{
		this.route = route;
		this.maximum = maximum;
		this.nodeId = nodeId;
	}

	Outbound route()
	{
		return route;
	}

	long nodeId()
	{
		return nodeId;
	}

	List<Peer> peers()
	{
		return peers;
	}

	boolean isMember(final Peer peer)
	{
		return peers.contains(peer);
	}

	/**
	 * Takes a connection, or says why not
	 *
	 * @return whether the zone has room for it
	 */
	boolean reserve()
	{
		if (connections.incrementAndGet() > maximum)
		{
			connections.decrementAndGet();
			return false;
		}
		return true;
	}

	void release()
	{
		connections.decrementAndGet();
	}

	int maximum()
	{
		return maximum;
	}

	int outgoing()
	{
		return connections.get();
	}

	Set<PeerAddress> handshaking()
	{
		return handshaking;
	}

	/**
	 * Peer exchange inside the zone: onion addresses only (ADR 0004, step 3)
	 */
	Discovery discovery()
	{
		return discovery;
	}

	void discovery(final Discovery zoneDiscovery)
	{
		this.discovery = zoneDiscovery;
	}

	/**
	 * Sends an own transfer to every peer of the zone, or keeps it until one joins
	 *
	 * @return whether it went out now
	 */
	synchronized boolean send(final SignedTransaction transfer)
	{
		if (peers.isEmpty())
		{
			waiting.add(transfer);
			return false;
		}
		Frame frame = frameOf(transfer);
		peers.forEach(peer -> peer.send(frame));
		return true;
	}

	/**
	 * A peer joined the zone: it gets every own transfer that waited for one
	 */
	synchronized void joined(final Peer peer)
	{
		peers.add(peer);
		waiting.forEach(transfer -> peer.send(frameOf(transfer)));
		waiting.clear();
	}

	void left(final Peer peer)
	{
		peers.remove(peer);
	}

	private static Frame frameOf(final SignedTransaction transfer)
	{
		return new Frame(MessageType.TRANSFER, CanonicalEncoding.encode(transfer));
	}
}
