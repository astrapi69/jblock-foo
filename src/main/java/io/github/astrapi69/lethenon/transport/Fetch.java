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

import java.util.List;

import io.github.astrapi69.lethenon.Bytes;

/**
 * What a node is fetching from one peer: the hashes it still expects, from which height, and
 * whether the peer's answer was full, so that there may be more after them
 *
 * @param expected
 *            the hashes of the blocks still to fetch, oldest first
 * @param nextHeight
 *            the height of the first of them
 * @param more
 *            whether the peer's CHAIN answer was full and a further GET_CHAIN is due after them
 */
record Fetch(List<Bytes> expected, long nextHeight, boolean more)
{

	Fetch
	{
		expected = List.copyOf(expected);
	}

	/**
	 * The next request, at most {@link BlockRequest#LIMIT} blocks
	 */
	BlockRequest nextRequest()
	{
		return new BlockRequest(nextHeight, Math.min(BlockRequest.LIMIT, expected.size()));
	}

	/**
	 * What is left after the given number of blocks arrived
	 */
	Fetch after(final int arrived)
	{
		return new Fetch(expected.subList(arrived, expected.size()), nextHeight + arrived, more);
	}
}
