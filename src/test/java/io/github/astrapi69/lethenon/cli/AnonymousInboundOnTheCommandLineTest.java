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

/**
 * node --anonymous-inbound: an onion service on the command line (ADR 0004, step 3, #120)
 */
class AnonymousInboundOnTheCommandLineTest extends AbstractCliTest
{

	private static final String ONION =
		"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.onion";

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
	@DisplayName("node --anonymous-inbound listens for its onion service on loopback and says so")
	void anonymousInbound_listensOnLoopback()
	{
		assertEquals(0, run("", "node", "--chain", chain.toString(), "--listen", "0",
			"--tx-proxy", "tor,127.0.0.1:9050", "--anonymous-inbound",
			ONION + ":18484,127.0.0.1:0,5", "--for", "1"), err);

		assertTrue(out.contains("onion service " + ONION + ":18484 on 127.0.0.1 port"), out);
		assertTrue(out.contains("at most 5"), out);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
		"--anonymous-inbound only|tor-less|--tx-proxy",
		"example.org:18484,127.0.0.1:0|with|onion address",
		ONION + ":18484,0.0.0.0:18485|with|127.0.0.1",
		ONION + ":18484|with|<onion>:<port>,127.0.0.1:<port>",
		ONION + ":18484,127.0.0.1:0,none|with|<onion>:<port>,127.0.0.1:<port>" })
	@DisplayName("a malformed --anonymous-inbound, or one without --tx-proxy, is refused with the reason")
	void aMalformedAnonymousInbound_isRefused(final String value, final String zone,
		final String reason)
	{
		String inbound = value.startsWith("--") ? ONION + ":18484,127.0.0.1:0" : value;
		int exit = "with".equals(zone)
			? run("", "node", "--chain", chain.toString(), "--listen", "0", "--tx-proxy",
				"tor,127.0.0.1:9050", "--anonymous-inbound", inbound, "--for", "1")
			: run("", "node", "--chain", chain.toString(), "--listen", "0",
				"--anonymous-inbound", inbound, "--for", "1");

		assertEquals(1, exit, out);
		assertTrue(err.contains(reason), err);
	}
}
