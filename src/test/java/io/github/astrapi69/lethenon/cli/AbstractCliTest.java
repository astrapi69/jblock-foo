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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Drives the command line the way a shell does - through {@link LethenonCli#execute(String...)}
 * with standard input, output and error swapped for buffers - and keeps what it printed, the same
 * shape as mystic-crypt's CLI tests
 */
abstract class AbstractCliTest
{

	/** What the last command printed to standard output */
	protected String out;

	/** What the last command printed to standard error */
	protected String err;

	/**
	 * Runs one command with the given lines on standard input
	 *
	 * @param stdin
	 *            what standard input holds, a password or a phrase per line
	 * @param args
	 *            the command line
	 * @return the exit code
	 */
	protected int run(final String stdin, final String... args)
	{
		InputStream originalIn = System.in;
		PrintStream originalOut = System.out;
		PrintStream originalErr = System.err;
		ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
		ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
		System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
		System.setOut(new PrintStream(outBuffer, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
		try
		{
			return LethenonCli.execute(args);
		}
		finally
		{
			out = outBuffer.toString(StandardCharsets.UTF_8);
			err = errBuffer.toString(StandardCharsets.UTF_8);
			System.setIn(originalIn);
			System.setOut(originalOut);
			System.setErr(originalErr);
		}
	}
}
