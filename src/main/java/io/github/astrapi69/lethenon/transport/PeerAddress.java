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

/**
 * A peer as the command line names it
 *
 * @param host
 *            the host name or address
 * @param port
 *            the TCP port
 */
public record PeerAddress(String host, int port)
{

	/**
	 * Checks the address
	 *
	 * @throws IllegalArgumentException
	 *             for an empty host or a port outside 1 to 65535
	 */
	public PeerAddress
	{
		if (host == null || host.isBlank())
		{
			throw new IllegalArgumentException("a peer needs a host");
		}
		if (port < 1 || port > 65_535)
		{
			throw new IllegalArgumentException("a port is 1 to 65535, not " + port);
		}
	}

	/**
	 * Reads host:port
	 *
	 * @param text
	 *            the address, for example localhost:18480
	 * @return the address
	 * @throws IllegalArgumentException
	 *             when the text is not host:port
	 */
	public static PeerAddress parse(final String text)
	{
		int colon = text.lastIndexOf(':');
		if (colon <= 0 || colon == text.length() - 1)
		{
			throw new IllegalArgumentException("a peer is host:port, not '" + text + "'");
		}
		try
		{
			return new PeerAddress(text.substring(0, colon),
				Integer.parseInt(text.substring(colon + 1)));
		}
		catch (NumberFormatException notANumber)
		{
			throw new IllegalArgumentException("a peer is host:port, not '" + text + "'");
		}
	}

	@Override
	public String toString()
	{
		return host + ":" + port;
	}
}
