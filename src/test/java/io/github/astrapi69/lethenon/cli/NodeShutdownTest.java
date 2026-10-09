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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A node stopped from outside - SIGTERM, or Ctrl-C in a terminal, which takes the same way out of
 * the JVM - prints its stop line and its refusals, as a stop through --for does (#129). The node
 * runs in a JVM of its own, because the signal ends the whole process.
 */
class NodeShutdownTest extends AbstractCliTest
{

	@TempDir
	Path directory;

	@Test
	@DisplayName("a node stopped with SIGTERM prints its stop line and its refusals")
	void aNodeStoppedWithSigterm_printsItsStopLineAndRefusals() throws Exception
	{
		Path chain = directory.resolve("c.lethenon");
		String password = aPassword();
		String wallet = directory.resolve("w.wallet").toString();
		assertEquals(0, run(password, "wallet", "create", "--wallet", wallet), err);
		assertEquals(0, run(password, "mine", "--testnet", "--chain", chain.toString(), "--wallet",
			wallet), err);
		int nobodyListens;
		try (ServerSocket probe = new ServerSocket(0))
		{
			nobodyListens = probe.getLocalPort();
		}
		Path stdout = directory.resolve("stdout.txt");
		Path stderr = directory.resolve("stderr.txt");

		Process node = new ProcessBuilder(javaCommand(), "-cp", System.getProperty("java.class.path"),
			LethenonCli.class.getName(), "node", "--chain", chain.toString(), "--listen", "0",
			"--no-discovery", "--peer", "127.0.0.1:" + nobodyListens)
			.redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
		try
		{
			awaitLine(stdout, "node on ", node);

			node.destroy();

			assertTrue(node.waitFor(30, TimeUnit.SECONDS), "the node ends after SIGTERM");
		}
		finally
		{
			node.destroyForcibly();
		}
		String printed = Files.readString(stdout, StandardCharsets.UTF_8);
		String refused = Files.readString(stderr, StandardCharsets.UTF_8);
		assertTrue(printed.contains("stopped at height 0"), printed);
		assertTrue(refused.contains("127.0.0.1:" + nobodyListens + " was not connected"), refused);
		assertEquals(1, linesStartingWith(printed, "stopped at height"),
			"the stop line is printed once: " + printed);
	}

	private static String javaCommand()
	{
		return Path.of(System.getProperty("java.home"), "bin", "java").toString();
	}

	private static void awaitLine(final Path file, final String start, final Process process)
		throws IOException, InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
		while (System.nanoTime() < deadline)
		{
			if (linesStartingWith(Files.readString(file, StandardCharsets.UTF_8), start) > 0)
			{
				return;
			}
			if (!process.isAlive())
			{
				throw new AssertionError("the node ended before it started: exit "
					+ process.exitValue());
			}
			Thread.sleep(100);
		}
		throw new AssertionError("no line starting with '" + start + "' within 60 s");
	}

	private static long linesStartingWith(final String text, final String start)
		throws IOException
	{
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new java.io.StringReader(text)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				lines.add(line);
			}
		}
		return lines.stream().filter(line -> line.startsWith(start)).count();
	}
}
