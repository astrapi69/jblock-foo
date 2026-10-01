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

import java.util.Arrays;

import io.github.astrapi69.crypt.data.hex.HexExtensions;

/**
 * An immutable sequence of bytes, so that a key, an address or a hash can live in a record and
 * still compare by content.
 * <p>
 * A bare {@code byte[]} in a record compares by identity, which makes two equal transactions
 * unequal and a signature check pass or fail by accident.
 * <p>
 * The hexadecimal form goes through crypt-data's {@code HexExtensions} rather than through
 * {@code java.util.HexFormat}: this family publishes that conversion, and a chain that reaches past
 * its own libraries never finds out whether they are any good.
 */
public final class Bytes
{

	private final byte[] content;

	private Bytes(final byte[] content)
	{
		this.content = content;
	}

	/**
	 * Takes a copy, so that a later change to the caller's array cannot change this value
	 *
	 * @param content
	 *            the bytes
	 * @return the value
	 */
	public static Bytes of(final byte[] content)
	{
		return new Bytes(content.clone());
	}

	/**
	 * Reads a hexadecimal text, the form these values are written in
	 *
	 * @param hexadecimal
	 *            the text
	 * @return the value
	 */
	public static Bytes ofHex(final String hexadecimal)
	{
		// crypt-data 13.0 refuses input that is not hexadecimal with an IllegalArgumentException
		// of its own, naming the reason, so there is nothing to translate here any more (#51)
		return new Bytes(HexExtensions.decodeHex(hexadecimal.toCharArray()));
	}

	/**
	 * A copy of the bytes
	 *
	 * @return the bytes
	 */
	public byte[] toByteArray()
	{
		return content.clone();
	}

	/**
	 * How many bytes this holds
	 *
	 * @return the length
	 */
	public int length()
	{
		return content.length;
	}

	@Override
	public boolean equals(final Object other)
	{
		return other instanceof Bytes bytes && Arrays.equals(bytes.content, content);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(content);
	}

	@Override
	public String toString()
	{
		return String.valueOf(HexExtensions.encodeHex(content));
	}
}
