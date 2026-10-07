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
package io.github.astrapi69.lethenon.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.astrapi69.lethenon.ChainFile;
import io.github.astrapi69.lethenon.transport.Node;
import io.github.astrapi69.lethenon.transport.Socks5Server;

/**
 * node --tx-proxy: an anonymity zone on the command line (ADR 0004, step 2, #116)
 */
class AnonymityZoneOnTheCommandLineTest extends AbstractCliTest
{

	private static final String ONION =
		"lethenonlethenonlethenonlethenonlethenonlethenonlethenon.onion";

	@TempDir
	Path directory;

	private Path chain;

	@BeforeEach
	void aTestChain()
	{
		String password = aPassword();
		String wallet = directory.resolve("miner.wallet").toString();
		chain = directory.resolve("chain.lethenon");
		assertEquals(0, run(password, "wallet", "create", "--wallet", wallet), err);
		assertEquals(0, run(password, "mine", "--testnet", "--chain", chain.toString(),
			"--wallet", wallet), err);
	}

	@Test
	@DisplayName("node --tx-proxy reaches an onion peer through the proxy, as a peer of the anonymity zone")
	void txProxy_reachesAnOnionPeerInTheZone() throws Exception
	{
		Path copy = directory.resolve("copy.lethenon");
		java.nio.file.Files.copy(chain, copy);
		try (Socks5Server socks = Socks5Server.start();
			Node onion = Node.on(new ChainFile(copy).require()))
		{
			socks.route(ONION, 18480, onion.listen(0));

			assertEquals(0, run("", "node", "--chain", chain.toString(), "--listen", "0",
				"--tx-proxy", "tor," + socks.address() + ",3", "--peer", ONION + ":18480",
				"--for", "2"), err);

			assertEquals(1, socks.requests().size());
		}
		assertTrue(out.contains("anonymity zone through the SOCKS5 proxy at"), out);
		assertTrue(out.contains("at most 3 peer(s)"), out);
		assertTrue(out.contains("1 anonymity peer(s)"), out);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = { "i2p,127.0.0.1:4447|tor",
		"tor|tor,host:port", "tor,127.0.0.1:9050,0|at least one",
		"tor,127.0.0.1:9050,many|tor,host:port" })
	@DisplayName("a --tx-proxy that is not tor,host:port[,max] is refused with the reason")
	void aMalformedTxProxy_isRefused(final String value, final String reason)
	{
		assertEquals(1, run("", "node", "--chain", chain.toString(), "--listen", "0",
			"--tx-proxy", value, "--for", "1"));

		assertTrue(err.contains(reason), err);
	}
}
