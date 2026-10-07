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

/**
 * A genesis block fixed in the code: the chain it belongs to and the block's canonical bytes, as
 * {@link CanonicalEncoding#encodeChain} writes a chain of one, in hexadecimal (#104)
 * <p>
 * Monero fixes its genesis in the code the same way, as {@code GENESIS_TX} and
 * {@code GENESIS_NONCE} ({@code src/cryptonote_config.h:239-240}). {@link ConsensusRules} checks
 * an anchor when it is built; {@code lethenon genesis} prints the bytes of a candidate.
 *
 * @param chainIdentifier
 *            the chain the block starts
 * @param canonicalHex
 *            the block's canonical bytes, hexadecimal
 */
public record GenesisAnchor(String chainIdentifier, String canonicalHex)
{
}
