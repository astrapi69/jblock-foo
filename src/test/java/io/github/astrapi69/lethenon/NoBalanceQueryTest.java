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
package io.github.astrapi69.lethenon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Milestone 3's acceptance, in the only form a test can take it: a network capture of the wallet
 * holds no balance query because the code that computes a balance cannot reach a network at all.
 * <p>
 * Capturing packets in a unit test would prove something about one run on one machine. This proves
 * it about every run: the chain package - where {@link WalletScan}, {@link Replay} and
 * {@link ChainState} live - names no networking type and imports no transport package. The gossip
 * and Tor transport milestone 3 also asks for will arrive in a package of its own, and this test is
 * what keeps the balance from quietly reaching into it (lethenon#2).
 */
class NoBalanceQueryTest
{

	/** The chain package, which is where a balance is computed */
	private static final Path CHAIN_PACKAGE = Path
		.of("src/main/java/io/github/astrapi69/lethenon");

	/**
	 * What a balance must not be able to name. A transport package is on the list for the same
	 * reason as a socket: reaching it from here is how asking somebody starts.
	 */
	private static final List<String> OUT_OF_REACH = List.of("java.net.", "javax.net.", "java.rmi",
		"HttpClient", "HttpURLConnection", "URLConnection", "ServerSocket", "SocketChannel",
		"InetAddress", "DatagramSocket", "lethenon.transport");

	@Test
	@DisplayName("nothing in the chain package can reach a network")
	void theChainPackage_reachesNoNetwork() throws IOException
	{
		List<String> found = new ArrayList<>();
		List<Path> sources = sources();
		for (Path source : sources)
		{
			List<String> lines = Files.readAllLines(source);
			for (int line = 0; line < lines.size(); line++)
			{
				for (String forbidden : OUT_OF_REACH)
				{
					if (lines.get(line).contains(forbidden))
					{
						found.add(source.getFileName() + ":" + (line + 1) + " names " + forbidden);
					}
				}
			}
		}

		assertEquals(List.of(), found, "a balance is computed, never requested: " + found);
		assertTrue(sources.size() >= 10, "scanned " + sources.size()
			+ " sources, which is too few to be the chain package - a check that cannot check "
			+ "must not report green");
	}

	/**
	 * The chain package itself, without its subpackages: a transport, when it exists, is a package
	 * of its own and is allowed to do what its name says
	 */
	private static List<Path> sources() throws IOException
	{
		assertTrue(Files.isDirectory(CHAIN_PACKAGE),
			"no chain package at " + CHAIN_PACKAGE.toAbsolutePath()
				+ "; this test runs from the project directory");
		try (Stream<Path> entries = Files.list(CHAIN_PACKAGE))
		{
			return entries.filter(entry -> entry.getFileName().toString().endsWith(".java"))
				.sorted().toList();
		}
	}
}
