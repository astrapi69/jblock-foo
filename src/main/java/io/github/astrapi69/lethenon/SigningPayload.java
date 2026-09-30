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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The bytes a signature actually covers.
 * <p>
 * Never a bare hash. A signature over an unlabelled digest can be replayed anywhere the same digest
 * means something else - another chain, another message type, a test network - so the signed bytes
 * start with a domain tag, name the chain and the message type, and only then carry the digest of
 * the canonical encoding.
 */
public final class SigningPayload
{

	/** What these bytes are for, first and in the clear */
	public static final String DOMAIN_TAG = "lethenon.sign.v1";

	/** The message type of a transfer */
	public static final String TYPE_TRANSFER = "transfer";

	private static final char SEPARATOR = ':';

	private SigningPayload()
	{
	}

	/**
	 * The bytes to sign for a transfer
	 *
	 * @param body
	 *            the transfer
	 * @param suite
	 *            the scheme that signs it, named in the payload so a signature cannot be
	 *            reinterpreted under another suite
	 * @return the labelled payload: tag, chain, type, suite and the digest of the canonical
	 *         encoding
	 */
	public static byte[] of(final TransactionBody body, final SignatureSuite suite)
	{
		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		payload.writeBytes(label(body.chainIdentifier(), TYPE_TRANSFER, suite));
		payload.writeBytes(digestOf(CanonicalEncoding.encode(body)));
		return payload.toByteArray();
	}

	private static byte[] label(final String chainIdentifier, final String type,
		final SignatureSuite suite)
	{
		return (DOMAIN_TAG + SEPARATOR + chainIdentifier + SEPARATOR + type + SEPARATOR
			+ suite.identifier() + SEPARATOR).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] digestOf(final byte[] content)
	{
		try
		{
			return MessageDigest.getInstance("SHA-256").digest(content);
		}
		catch (NoSuchAlgorithmException never)
		{
			// SHA-256 is required of every Java platform
			throw new IllegalStateException("SHA-256 is missing from this Java runtime", never);
		}
	}
}
