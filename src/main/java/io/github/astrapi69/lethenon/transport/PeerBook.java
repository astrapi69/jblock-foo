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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a node knows about other nodes' addresses, after Monero's white and gray peer lists: an
 * address it has itself been connected to, and one it has only heard of from a peer
 * <p>
 * Only the first kind is passed on. An address heard of becomes the first kind when a connection
 * to it succeeds, and is forgotten when one fails.
 */
final class PeerBook
{

	/** Monero's {@code P2P_LOCAL_WHITE_PEERLIST_LIMIT 1000} ({@code src/cryptonote_config.h:138}) */
	static final int CONFIRMED_LIMIT = 1_000;

	/** Monero's {@code P2P_LOCAL_GRAY_PEERLIST_LIMIT 5000} ({@code src/cryptonote_config.h:139}) */
	static final int HEARD_LIMIT = 5_000;

	private final LinkedHashSet<PeerAddress> confirmed = new LinkedHashSet<>();

	private final LinkedHashSet<PeerAddress> heard = new LinkedHashSet<>();

	private final Set<PeerAddress> itself = new LinkedHashSet<>();

	/**
	 * An address this node has been connected to; it moves to the newest end
	 */
	synchronized void confirmed(final PeerAddress address)
	{
		heard.remove(address);
		confirmed.remove(address);
		confirmed.add(address);
		trim(confirmed, CONFIRMED_LIMIT);
	}

	/**
	 * Addresses a peer passed on; an address already confirmed, or known to be this node, is not
	 * added again
	 */
	synchronized void heard(final List<PeerAddress> addresses)
	{
		for (PeerAddress address : addresses)
		{
			if (!confirmed.contains(address) && !itself.contains(address))
			{
				heard.add(address);
			}
		}
		trim(heard, HEARD_LIMIT);
	}

	/**
	 * An address that turned out to be this node itself: forgotten, and never taken up again
	 */
	synchronized void isItself(final PeerAddress address)
	{
		forget(address);
		itself.add(address);
	}

	synchronized void forget(final PeerAddress address)
	{
		confirmed.remove(address);
		heard.remove(address);
	}

	/**
	 * What this node passes on: the newest {@link PeerList#LIMIT} confirmed addresses
	 */
	synchronized List<PeerAddress> shareable()
	{
		List<PeerAddress> all = new ArrayList<>(confirmed);
		return List.copyOf(all.subList(Math.max(0, all.size() - PeerList.LIMIT), all.size()));
	}

	/**
	 * An address to connect to that is not busy: a confirmed one first, then one heard of
	 */
	synchronized Optional<PeerAddress> candidate(final Set<PeerAddress> busy)
	{
		for (PeerAddress address : confirmed)
		{
			if (!busy.contains(address))
			{
				return Optional.of(address);
			}
		}
		for (PeerAddress address : heard)
		{
			if (!busy.contains(address))
			{
				return Optional.of(address);
			}
		}
		return Optional.empty();
	}

	synchronized List<PeerAddress> heardOf()
	{
		return List.copyOf(heard);
	}

	private static void trim(final LinkedHashSet<PeerAddress> addresses, final int limit)
	{
		Iterator<PeerAddress> oldest = addresses.iterator();
		while (addresses.size() > limit && oldest.hasNext())
		{
			oldest.next();
			oldest.remove();
		}
	}
}
