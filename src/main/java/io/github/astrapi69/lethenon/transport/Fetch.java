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

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.Bytes;

/**
 * What a node is fetching from one peer: from which block of its own chain the peer's chain goes
 * on, the blocks fetched so far, the hashes still expected, and whether the peer's answer was full
 * <p>
 * The fetched blocks are a candidate on top of the base block. They wait here until the candidate
 * carries more work than the node's chain; a fork may need several batches before it does.
 *
 * @param base
 *            the height of the last block both chains share
 * @param baseHash
 *            its hash, so that a fetch whose base the node no longer has is recognised
 * @param expected
 *            the hashes of the blocks still to fetch, oldest first
 * @param fetched
 *            the blocks fetched after the base and not yet adopted, oldest first
 * @param more
 *            whether the peer's CHAIN answer was full and a further GET_CHAIN is due after them
 */
record Fetch(long base, Bytes baseHash, List<Bytes> expected, List<BlockBody> fetched,
	boolean more)
{

	Fetch
	{
		expected = List.copyOf(expected);
		fetched = List.copyOf(fetched);
	}

	/**
	 * The height of the next block to fetch
	 */
	long nextHeight()
	{
		return base + 1 + fetched.size();
	}

	/**
	 * The next request, at most {@link BlockRequest#LIMIT} blocks
	 */
	BlockRequest nextRequest()
	{
		return new BlockRequest(nextHeight(), Math.min(BlockRequest.LIMIT, expected.size()));
	}

	/**
	 * The fetch after the given blocks arrived, in the order they were expected
	 */
	Fetch with(final List<BlockBody> arrived)
	{
		List<BlockBody> all = new ArrayList<>(fetched);
		all.addAll(arrived);
		return new Fetch(base, baseHash, expected.subList(arrived.size(), expected.size()), all,
			more);
	}

	/**
	 * The rest of the fetch after the node adopted everything fetched so far
	 */
	Fetch rebasedOn(final long newBase, final Bytes newBaseHash)
	{
		return new Fetch(newBase, newBaseHash, expected, List.of(), more);
	}
}
