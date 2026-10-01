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

import java.security.KeyPair;
import java.security.PublicKey;

/**
 * What a recipient publishes, and what never leaves their machine.
 * <p>
 * Two keys, as every stealth address scheme has: a VIEW key, which is what a sender needs in order
 * to pay, and a SPEND key, which is the account's signing key. The published address is the pair of
 * public halves; the private halves stay with the recipient, and the view private key is the only
 * thing needed to recognise a payment - so scanning can happen somewhere the spending key never
 * goes.
 * <p>
 * The address itself is NOT on the chain. It travels the way a bank account number does, out of
 * band, which is also why it is not a hard fork to change: only what a transaction carries is
 * (lethenon#2).
 *
 * @param viewKey
 *            the X25519 public key a sender derives the shared secret against
 * @param spendKey
 *            the public key of the account, which is what a transfer finally names
 */
public record PublishedAddress(Bytes viewKey, Bytes spendKey)
{

	/**
	 * The address belonging to a view key pair and a spend key pair
	 *
	 * @param view
	 *            the X25519 pair used for recognising payments
	 * @param spend
	 *            the signing pair of the account
	 * @return the address to publish
	 */
	public static PublishedAddress of(final KeyPair view, final KeyPair spend)
	{
		return new PublishedAddress(encoded(view.getPublic()), encoded(spend.getPublic()));
	}

	private static Bytes encoded(final PublicKey key)
	{
		return Bytes.of(key.getEncoded());
	}
}
